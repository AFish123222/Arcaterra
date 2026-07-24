package com.fish.arcaterra.tree;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * 八叉树节点，持有 8 个子节点。
 */
public class TreeNetChunk {
    public static final int LEAF_SIZE = 4;          // 叶子区块大小
    public static final int MAX_LOD = 3;            // LOD级别数量

    private final TreePath path;
    private final TreeNetChunk parent;
    private TreeNetChunk[] children;                // 8个子节点，非叶节点才存在
    private final short[] voxels;                   // 仅叶子节点有体素数据 (4x4x4)
    private final ChunkMesh[] meshes;               // 不同LOD级别的网格 (0~3)
    private final List<Runnable> pendingEvents;     // 待处理事件队列
    private boolean loaded;                         // 是否已加载到内存
    private boolean dirty;                          // 需要重新生成网格

    // 临时渲染状态（每帧更新）
    private boolean isOnPlayerPath;
    private int currentLOD;

    public TreeNetChunk(TreePath path, TreeNetChunk parent) {
        this.path = path;
        this.parent = parent;
        this.meshes = new ChunkMesh[MAX_LOD];
        this.pendingEvents = new ArrayList<>();
        this.loaded = false;
        this.dirty = true;

        // 叶子节点分配体素
        if (isLeaf()) {
            this.voxels = new short[LEAF_SIZE * LEAF_SIZE * LEAF_SIZE];
            generateTerrain();
        } else {
            this.voxels = null;
            this.children = new TreeNetChunk[8];
        }
    }

    // 判断是否为叶子节点（没有子节点且路径深度达到最大）
    public boolean isLeaf() {
        // 这里可以设定最大深度，比如当节点大小 <= 4 时视为叶子
        return getSize() <= LEAF_SIZE;
    }

    // 获取节点覆盖的体素大小（边长）
    public int getSize() {
        return LEAF_SIZE * (int) Math.pow(2, path.getDepth());
    }

    // 生成地形（仅叶子）
    private void generateTerrain() {
        // 从你的 NoiseTerrainProvider 或 FlatTerrainProvider 获取高度
        // 这里简单实现：地面在 y=8
        for (int x = 0; x < LEAF_SIZE; x++) {
            for (int z = 0; z < LEAF_SIZE; z++) {
                // 假设 groundY = 8，由 World 提供
                int groundY = 8;
                for (int y = 0; y < LEAF_SIZE; y++) {
                    int worldY = y; // 局部坐标
                    short id = 0;
                    if (worldY < groundY) id = 1;
                    else if (worldY == groundY) id = 1;
                    else if (worldY == groundY + 1) id = 2;
                    voxels[x + y * LEAF_SIZE + z * LEAF_SIZE * LEAF_SIZE] = id;
                }
            }
        }
    }

    // ========== 树操作 ==========

    // 获取或创建子节点
    public TreeNetChunk getOrCreateChild(int index) {
        if (isLeaf()) throw new IllegalStateException("Leaf node cannot have children");
        if (children[index] == null) {
            TreePath childPath = path.append(index);
            children[index] = new TreeNetChunk(childPath, this);
            // 从磁盘加载LOD数据（如果有）
            loadFromDisk(childPath);
        }
        return children[index];
    }

    // 加载磁盘中的 LOD 数据
    private void loadFromDisk(TreePath p) {
        // TODO: 从 lod/ 目录加载对应的 .dat 文件
        // 如果文件不存在，则标记为未加载，等待生成
    }

    // 递归加载路径上的节点（从根到叶子）
    public TreeNetChunk loadPath(int[] directions) {
        TreeNetChunk current = this;
        for (int dir : directions) {
            current = current.getOrCreateChild(dir);
        }
        return current;
    }

    // ========== 渲染 ==========

    /**
     * 标记玩家所在路径（从叶子向上标记）
     */
    public void markPlayerPath(TreePath playerPath) {
        // 如果当前节点是玩家路径的祖先
        if (isAncestorOf(playerPath)) {
            this.isOnPlayerPath = true;
            if (!isLeaf()) {
                int nextDir = playerPath.getDirectionAt(this.path.getDepth());
                children[nextDir].markPlayerPath(playerPath);
            }
        } else {
            this.isOnPlayerPath = false;
        }
    }

    private boolean isAncestorOf(TreePath other) {
        if (other.getDepth() < this.path.getDepth()) return false;
        // 比较路径前缀
        for (int i = 0; i < this.path.getDepth(); i++) {
            if (this.path.getDirectionAt(i) != other.getDirectionAt(i)) return false;
        }
        return true;
    }

    /**
     * 责任链渲染：从根开始递归
     */
    public void render(float camX, float camY, float camZ) {
        if (!loaded) return;

        // 如果不在玩家路径上，直接渲染当前节点的 LOD 网格，不递归子节点
        if (!isOnPlayerPath) {
            renderMesh(camX, camY, camZ);
            return;
        }

        // 如果在玩家路径上，且是叶子节点，渲染精细网格
        if (isLeaf()) {
            renderMesh(camX, camY, camZ);
            return;
        }

        // 如果在玩家路径上，且非叶子节点，递归子节点
        // 但在递归之前，也可以根据距离决定是否提前停止细化
        float dist = distanceToCamera(camX, camY, camZ);
        if (dist > LODConfig.TRIANGLE_THRESHOLD) {
            // 远处用三角网格（LOD3），不继续细化
            renderMesh(camX, camY, camZ);
            return;
        }

        // 继续向下递归
        for (TreeNetChunk child : children) {
            if (child != null && child.loaded) {
                child.render(camX, camY, camZ);
            }
        }
    }

    private float distanceToCamera(float cx, float cy, float cz) {
        // 计算节点中心到相机位置的距离
        return 0; // TODO
    }

    /**
     * 渲染当前节点的网格（选择适当的 LOD 级别）
     */
    private void renderMesh(float camX, float camY, float camZ) {
        if (meshes[currentLOD] == null) {
            generateLODMesh(currentLOD);
        }
        // 如果还是 null（空节点），跳过
        if (meshes[currentLOD] == null || meshes[currentLOD].indexCount == 0) return;

        glPushMatrix();
        // 平移：用路径计算位置
        // TODO: 根据 path 计算世界偏移
        glTranslatef(0, 0, 0);
        meshes[currentLOD].render();
        glPopMatrix();
    }

    /**
     * 生成指定 LOD 级别的网格
     */
    private void generateLODMesh(int lod) {
        if (isLeaf()) {
            // 叶子节点：从体素生成方块网格
            meshes[lod] = buildBlockMesh();
        } else {
            // 非叶节点：从子节点简化生成网格
            meshes[lod] = buildSimplifiedMesh(lod);
        }
        dirty = false;
    }

    /**
     * 从体素生成方块网格（叶子节点）
     */
    private ChunkMesh buildBlockMesh() {
        // 复用你现有的 buildBlockFaces 逻辑，但基于 voxels 数组
        // 返回 ChunkMesh
        return new ChunkMesh(); // TODO
    }

    /**
     * 从子节点简化生成网格（非叶节点）
     */
    private ChunkMesh buildSimplifiedMesh(int lod) {
        // 根据 LOD 级别，采样子节点的体素或网格
        // LOD0: 精细合并（8个子节点合并，保留细节）
        // LOD1: 粗合并（每2个方向取平均）
        // LOD2: 三角面简化
        // LOD3: 高度图
        return new ChunkMesh(); // TODO
    }

    // ========== 事件队列 ==========

    public void addEvent(Runnable event) {
        pendingEvents.add(event);
        if (!loaded) {
            // 写入磁盘
            writeEventsToDisk();
        }
    }

    private void writeEventsToDisk() {
        // TODO: 写入 lod/ 目录下的对应文件
    }

    public void processEvents() {
        // 轮询处理事件
        for (Runnable event : pendingEvents) {
            event.run();
        }
        pendingEvents.clear();
        dirty = true;
    }

    // ========== 其他 ==========

    public void unload() {
        // 卸载节点，释放网格，写入事件
        for (int i = 0; i < meshes.length; i++) {
            if (meshes[i] != null) {
                meshes[i].destroy();
                meshes[i] = null;
            }
        }
        loaded = false;
        if (!pendingEvents.isEmpty()) {
            writeEventsToDisk();
        }
    }
}