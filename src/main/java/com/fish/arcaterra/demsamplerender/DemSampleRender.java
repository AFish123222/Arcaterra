package com.fish.arcaterra.demsamplerender;

import com.fish.arcaterra.Config;
import com.fish.arcaterra.Player;
import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * DEM 采样 LOD 渲染器（单例）。<br>
 * 双缓冲 + 后台线程，主线程仅上传和渲染。<br>
 *
 * 在 Arcaterra 中的集成示例<br>
 * // init()<br>
 * DemSampleRender render = DemSampleRender.getInstance();<br>
 * render.init(world, player);<br>
 *<br>
 * // 当玩家传送时调用<br>
 * render.requestRebuild();<br>
 *<br>
 * // loop() 中每帧调用<br>
 * render.updateVertices();<br>
 * // ... 进行相机变换<br>
 * render.render();<br>
 *<br>
 * // shutdown()<br>
 * render.shutdown();<br>
 */
public final class DemSampleRender {

    private static DemSampleRender instance;

    public static DemSampleRender getInstance() {
        if (instance == null) {
            instance = new DemSampleRender();
        }
        return instance;
    }

    private DemSampleRender() {}

    // ===== 依赖引用 =====
    private World world;
    private Player player;

    // ===== 双缓冲 =====
    private LODBuffer bufferA;
    private LODBuffer bufferB;
    private LODBuffer current;   // 主线程渲染用
    private LODBuffer back;      // 后台线程写入用

    // ===== VBO =====
    private int vboVertexId;
    private int vboIndexId;
    private boolean vboInitialized;

    // ===== 同步标志 =====
    private volatile boolean ready;
    private volatile boolean rebuildRequested;

    // ===== 后台线程 =====
    private Thread workerThread;
    private volatile boolean running;

    // ===== 配置缓存（init 时读取） =====
    private int renderRadius;
    private int sampleStep;
    private long regenIntervalMs;
    private long pollIntervalMs;
    private int cols;
    private int rows;

    // ===== 内部类：双缓冲数据载体 =====
    private static final class LODBuffer {
        float[] vertices;
        int[] indices;
        int vertexCount;
        int indexCount;

        void reset() {
            vertexCount = 0;
            indexCount = 0;
        }
    }

    // ============================================================
    // 公开 API
    // ============================================================

    public void init(World world, Player player) {
        if (this.world != null) {
            throw new IllegalStateException("DemSampleRender already initialized");
        }
        this.world = world;
        this.player = player;

        // 读取配置
        renderRadius = Config.DemSampleLodConfig.renderRadius;
        sampleStep = Config.DemSampleLodConfig.sampleStep;
        regenIntervalMs = Config.DemSampleLodConfig.regenIntervalMs;
        pollIntervalMs = Config.DemSampleLodConfig.pollIntervalMs;

        // 计算网格尺寸
        int size = renderRadius * 2 * Chunk.SIZE;
        cols = size / sampleStep + 1;
        rows = cols; // 正方形

        // 预分配双缓冲（一次性分配，永不 new 新数组）
        bufferA = new LODBuffer();
        bufferB = new LODBuffer();
        bufferA.vertices = new float[cols * rows * 3];
        bufferA.indices = new int[(cols - 1) * (rows - 1) * 6];
        bufferB.vertices = new float[cols * rows * 3];
        bufferB.indices = new int[(cols - 1) * (rows - 1) * 6];

        current = bufferA;
        back = bufferB;
        ready = false;
        rebuildRequested = false;

        // 生成 VBO
        vboVertexId = glGenBuffers();
        vboIndexId = glGenBuffers();
        vboInitialized = true;

        // 启动后台线程
        running = true;
        workerThread = new Thread(this::backgroundLoop);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /** 强制重建（由传送系统调用） */
    public void requestRebuild() {
        rebuildRequested = true;
    }

    /** 主线程每帧调用：检查 ready，若有新数据则上传到 VBO<br>
     * // updateVertices() 检测到 ready == true，执行 glBufferData，上传当前顶点数据<br>
     * // 然后立刻 ready = false，避免重复上传<br>
     *
     */
    public void updateVerticesIfReady() {
        if (!ready) return;

        if (current.vertexCount == 0 || current.indexCount == 0) {
            ready = false;
            return;
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboVertexId);
        FloatBuffer vBuf = MemoryUtil.memAllocFloat(current.vertexCount * 3);
        vBuf.put(current.vertices, 0, current.vertexCount * 3).flip();
        glBufferData(GL_ARRAY_BUFFER, vBuf, GL_STATIC_DRAW);
        MemoryUtil.memFree(vBuf);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndexId);
        IntBuffer iBuf = MemoryUtil.memAllocInt(current.indexCount);
        iBuf.put(current.indices, 0, current.indexCount).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuf, GL_STATIC_DRAW);
        MemoryUtil.memFree(iBuf);

        ready = false;
    }

    /** 主线程每帧调用：执行绘制 */
    public void render() {
        if (!vboInitialized || current.vertexCount == 0) return;

        glBindBuffer(GL_ARRAY_BUFFER, vboVertexId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndexId);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        glDrawElements(GL_TRIANGLES, current.indexCount, GL_UNSIGNED_INT, 0);

        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /** 清理（主类关闭时调用） */
    public void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(1000);
            } catch (InterruptedException ignored) {}
            workerThread = null;
        }
        if (vboInitialized) {
            glDeleteBuffers(vboVertexId);
            glDeleteBuffers(vboIndexId);
            vboInitialized = false;
        }
        world = null;
        player = null;
    }

    // ============================================================
    // 后台线程
    // ============================================================

    private void backgroundLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            // ---- 阶段 1：休眠 ----
            long remaining = regenIntervalMs;
            while (remaining > 0 && running) {
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                remaining -= pollIntervalMs;
                if (rebuildRequested) {
                    rebuildRequested = false;
                    break;
                }
            }
            if (!running || Thread.currentThread().isInterrupted()) break;

            // ---- 阶段 2：捕获坐标并生成 ----
            float centerX = player.x;
            float centerZ = player.z;
            float minX = centerX - renderRadius * Chunk.SIZE;
            float maxX = centerX + renderRadius * Chunk.SIZE;
            float minZ = centerZ - renderRadius * Chunk.SIZE;
            float maxZ = centerZ + renderRadius * Chunk.SIZE;

            // 用局部变量指向 back，避免期间被交换
            LODBuffer writeBuf = back;
            writeBuf.reset();

            boolean interrupted = false;
            int idxV = 0, idxI = 0;

            for (int row = 0; row < rows && running; row++) {
                float z = minZ + row * sampleStep;
                for (int col = 0; col < cols; col++) {
                    float x = minX + col * sampleStep;
                    float h = world.getTerrainProvider().getHeight(x, z);
                    writeBuf.vertices[idxV++] = x;
                    writeBuf.vertices[idxV++] = h;
                    writeBuf.vertices[idxV++] = z;
                }
                // 每行结束后检查中断
                if (rebuildRequested) {
                    rebuildRequested = false;
                    interrupted = true;
                    break;
                }
            }

            // 如果被中断或线程停止，丢弃本轮数据
            if (interrupted || !running) {
                continue;
            }

            // 生成索引（三角形条带）
            // 注意：索引相对于当前顶点缓冲区，每个顶点的索引就是其行*cols+列
            // 我们按顺序填充了顶点，索引可以直接基于行列计算
            // 但因为我们之前没有存储顶点索引，这里需要重新计算行和列
            // 简单方案：在顶点生成时同时记录索引，但那样会破坏解耦。
            // 更好的方式：根据行列直接计算索引。
            // 我们重新遍历行列构建索引。
            int iIdx = 0;
            for (int row = 0; row < rows - 1; row++) {
                for (int col = 0; col < cols - 1; col++) {
                    int i0 = row * cols + col;
                    int i1 = row * cols + col + 1;
                    int i2 = (row + 1) * cols + col;
                    int i3 = (row + 1) * cols + col + 1;
                    // 两个三角形：i0-i1-i2, i2-i1-i3
                    writeBuf.indices[iIdx++] = i0;
                    writeBuf.indices[iIdx++] = i1;
                    writeBuf.indices[iIdx++] = i2;
                    writeBuf.indices[iIdx++] = i2;
                    writeBuf.indices[iIdx++] = i1;
                    writeBuf.indices[iIdx++] = i3;
                }
            }

            writeBuf.vertexCount = idxV / 3;   // 顶点个数
            writeBuf.indexCount = iIdx;        // 索引个数

            // ---- 阶段 3：交换指针（无临时变量） ----
            if (current == bufferA) {
                current = bufferB;
                back = bufferA;
            } else {
                current = bufferA;
                back = bufferB;
            }

            ready = true;
        }
    }
}