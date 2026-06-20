package com.fish.arcaterra.lod;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.mesh.ChunkMesh;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

public class LodChunk {
    public final int cx, cy, cz;    // 以区块为单位（16格）的坐标
    public final int level;         // 0=原区块, 1=2x2x2, 2=4x4x4, ...
    public final int size;          // 边长（体素单位）
    private final ChunkMesh mesh;
    private boolean dirty = true;

    public LodChunk(int cx, int cy, int cz, int level) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.level = level;
        this.size = 16 * (int) Math.pow(2, level);
        this.mesh = new ChunkMesh();
    }

    // 生成网格：从世界获取体素数据，降采样生成低分辨率网格
    public void rebuildMesh(World world) {
        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int step = (int) Math.pow(2, level); // 采样步长（区块单位）
        int voxelStep = step; // 每个采样点间隔 step 个原始体素

        // 遍历合并块内的每个宏观体素（步长 step）
        for (int x = 0; x < 16; x += step) {
            for (int y = 0; y < 16; y += step) {
                for (int z = 0; z < 16; z += step) {
                    // 世界坐标
                    int wx = cx * 16 + x;
                    int wy = cy * 16 + y;
                    int wz = cz * 16 + z;
                    // 采样体素（取中心或平均值，这里取中心）
                    short id = world.getBlockSafe(wx, wy, wz);
                    if (id == 0) continue;
                    // 生成一个大立方体，尺寸为 step
                    int x0 = wx;
                    int y0 = wy;
                    int z0 = wz;
                    int x1 = wx + step;
                    int y1 = wy + step;
                    int z1 = wz + step;
                    // 只生成暴露面（检查相邻宏观体素是否为空）
                    // 由于无法直接获取邻居宏观体素，我们检查世界坐标的边界
                    // 更简单：生成所有面，但这样会大量重叠，先这样，后续优化
                    // 使用 addCube 生成完整立方体（6面）
                    addCube(verts, indices, x0, y0, z0, x1, y1, z1, verts.size()/3);
                }
            }
        }
        uploadMesh(verts, indices);
        dirty = false;
    }

    private void addCube(List<Float> verts, List<Integer> indices, int x0, int y0, int z0, int x1, int y1, int z1, int offset) {
        float[][] faceVerts = {
                {x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0},
                {x0,y0,z1, x0,y1,z1, x1,y1,z1, x1,y0,z1},
                {x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0},
                {x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1},
                {x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1},
                {x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0}
        };
        for (float[] quad : faceVerts) {
            for (float f : quad) verts.add(f);
        }
        int base = offset;
        for (int i = 0; i < 6; i++) {
            indices.add(base + i*4);
            indices.add(base + i*4 + 1);
            indices.add(base + i*4 + 2);
            indices.add(base + i*4);
            indices.add(base + i*4 + 2);
            indices.add(base + i*4 + 3);
        }
    }

    private void uploadMesh(List<Float> verts, List<Integer> indices) {
        float[] vArr = new float[verts.size()];
        int[] iArr = new int[indices.size()];
        for (int i = 0; i < vArr.length; i++) vArr[i] = verts.get(i);
        for (int i = 0; i < iArr.length; i++) iArr[i] = indices.get(i);
        FloatBuffer vBuf = MemoryUtil.memAllocFloat(vArr.length);
        IntBuffer iBuf = MemoryUtil.memAllocInt(iArr.length);
        vBuf.put(vArr).flip();
        iBuf.put(iArr).flip();
        mesh.upload(vBuf, iBuf);
        MemoryUtil.memFree(vBuf);
        MemoryUtil.memFree(iBuf);
    }

    public void render() {
        if (mesh.indexCount == 0) return;
        glPushMatrix();
        glTranslatef(cx * 16, cy * 16, cz * 16);
        mesh.render();
        glPopMatrix();
    }

    public void destroy() {
        mesh.destroy();
    }
}