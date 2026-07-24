package com.fish.arcaterra.tree;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import com.fish.arcaterra.terrarium.NoiseTerrainProvider;
import com.fish.arcaterra.terrarium.TerrainProvider;
import com.fish.arcaterra.tree.terrain.LodTerrainProvider;
import com.fish.arcaterra.tree.terrain.NoiseLodTerrainProvider;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * 八叉树节点，代表一个体素空间区域。
 * 每个节点持有子节点数组（8个），并提供 LOD 网格渲染。
 * <p>
 * 节点坐标由 TreePath 唯一标识，无全局坐标概念。
 */
public class TreeNetChunk {
    /** 叶子区块边长（体素单位）。 */
    public static final int LEAF_SIZE = 16;
    /** 根节点覆盖边长（世界单位）。 */
    public static final int ROOT_SIZE = 1024;
    /** LOD级别数量，0为最精细，3为最粗。 */
    public static final int MAX_LOD = 4;

    private final TerrainProvider terrainProvider;

    private final TreePath path;                    // 从根到当前节点的路径
    private final TreeNetChunk parent;
    private TreeNetChunk[] children;                // 8个子节点，非叶子节点存在
    private final short[] voxels;                   // 仅叶子节点有体素数据 (4x4x4)
    private final ChunkMesh[] meshes;               // 不同LOD级别的网格 (0~3)
    private final List<Runnable> pendingEvents;     // 待处理事件队列
    private boolean loaded;                         // 是否已加载到内存
    private boolean dirty;                          // 需要重新生成网格

    // 渲染状态（每帧更新）
    private boolean isOnPlayerPath;
    private int currentLOD;

    /**
     * 构造一个节点。
     * @param path 从根到该节点的路径
     * @param parent 父节点，根节点为 null
     */
    public TreeNetChunk(TreePath path, TreeNetChunk parent, LodTerrainProvider terrainProvider) {
        this.path = path;
        this.parent = parent;
        this.meshes = new ChunkMesh[MAX_LOD];
        this.pendingEvents = new ArrayList<>();
        this.loaded = false;
        this.dirty = true;
        this.terrainProvider = terrainProvider;

        if (isLeaf()) {
            this.voxels = new short[LEAF_SIZE * LEAF_SIZE * LEAF_SIZE];
            generateTerrain();
            this.loaded = true;
            this.children = null;
        } else {
            this.voxels = null;
            this.children = new TreeNetChunk[8];
            // 非叶节点不需要地形数据，但也可以标记为已加载（如果它有子节点数据）
            loaded = true; // 或者根据是否有子节点数据来决定
        }
    }

    /**
     * 判断是否为叶子节点（节点大小 <= LEAF_SIZE）。
     */
    public boolean isLeaf() {
        return getSize() <= LEAF_SIZE;
    }

    /**
     * 获取该节点覆盖的体素边长（世界单位）。
     * 根节点为 ROOT_SIZE，每深一层减半。
     */
    public int getSize() {
        return ROOT_SIZE >> path.getDepth();
    }

    // ========== 地形生成 ==========

    /**
     * 生成叶子节点的体素数据（占位实现，需替换为真实地形生成）。
     */
    private void generateTerrain() {
        if (terrainProvider == null) {
            System.out.println("warning：terrainProvider is null!! no terrain");
            return;
        }
        // 计算叶子在世界空间中的角点偏移
        float[] offset = computeWorldOffset();
        for (int x = 0; x < LEAF_SIZE; x++) {
            for (int z = 0; z < LEAF_SIZE; z++) {
                float worldX = offset[0] + x + 0.5f;
                float worldZ = offset[2] + z + 0.5f;
                float height = terrainProvider.getHeight(worldX, worldZ);
                int groundY = Math.round(height);
                for (int y = 0; y < LEAF_SIZE; y++) {
                    int worldY = (int)(offset[1] + y);
                    short id = 0;
                    if (worldY < groundY) id = 1;
                    else if (worldY == groundY) id = 1;
                    else if (worldY == groundY + 1) id = 2;
                    voxels[x + y * LEAF_SIZE + z * LEAF_SIZE * LEAF_SIZE] = id;
                }
            }
        }
    }
//    private void generateTerrain() {
//        if (terrainProvider == null) {
//            System.out.println("警告：terrainProvider 为空，无法生成地形！");
//            return;
//        }
//        float[] offset = computeWorldOffset();
//        this.voxels = terrainProvider.generateVoxels(offset[0], offset[1], offset[2]);
//        // 检查是否有非空气方块
//        int nonAir = 0;
//        for (short v : voxels) {
//            if (v != 0) nonAir++;
//        }
//        System.out.println("叶子节点 " + path + " 生成了 " + nonAir + " 个非空气方块");
//    }
    // ========== 树操作 ==========

    /**
     * 获取或创建子节点。
     * @param index 0~7，对应八叉树的八个象限
     */
    public TreeNetChunk getOrCreateChild(int index) {
        if (isLeaf()) throw new IllegalStateException("Leaf node cannot have children");
        if (children[index] == null) {
            TreePath childPath = path.append(index);
            children[index] = new TreeNetChunk(
                    childPath,
                    this,
                    new NoiseLodTerrainProvider() //fixme:这么写不能扩展啊
            );
            // 从磁盘加载LOD数据（如有）
            loadFromDisk(childPath);
        }
        return children[index];
    }

    /**
     * 从磁盘加载预生成的 LOD 数据（占位）。
     */
    private void loadFromDisk(TreePath p) {
        // TODO: 从 lod/ 目录加载对应的 .dat 文件
    }

    // ========== 渲染路径标记 ==========

    /**
     * 标记玩家所在的路径（从叶子向上标记）。
     * @param playerPath 玩家当前所在的叶子节点路径
     */
    public void markPlayerPath(TreePath playerPath) {
        if (isAncestorOf(playerPath)) {
            this.isOnPlayerPath = true;
            if (!isLeaf()) {
                int nextDir = playerPath.getDirectionAt(this.path.getDepth());
                // 关键修复：如果子节点为空，先创建它
                if (children[nextDir] == null) {
                    children[nextDir] = getOrCreateChild(nextDir);
                }
                children[nextDir].markPlayerPath(playerPath);
            }
        } else {
            this.isOnPlayerPath = false;
        }
    }

    /**
     * 判断当前节点是否为指定路径的祖先。
     */
    private boolean isAncestorOf(TreePath other) {
        if (other.getDepth() < this.path.getDepth()) return false;
        for (int i = 0; i < this.path.getDepth(); i++) {
            if (this.path.getDirectionAt(i) != other.getDirectionAt(i)) return false;
        }
        return true;
    }

    // ========== 渲染 ==========

    /**
     * 责任链渲染：从根开始递归，根据玩家路径决定细化程度。
     * @param camX, camY, camZ 相机位置（世界坐标）
     */
    public void render(float camX, float camY, float camZ) {
//        System.out.println(0);

        if (!loaded) return;
//        System.out.println(1);

        // 计算当前节点到相机的距离，选择 LOD 级别
        float dist = distanceToCamera(camX, camY, camZ);
        currentLOD = selectLOD(dist);

        // 不在玩家路径上：直接渲染粗网格，不递归
        if (!isOnPlayerPath) {
            renderMesh(camX, camY, camZ);
            return;
        }
//        System.out.println(2);

        // 在玩家路径上，且是叶子节点：渲染精细网格
        if (isLeaf()) {
            renderMesh(camX, camY, camZ);
            return;
        }
//        System.out.println(3);

        // 在玩家路径上，但距离足够远，停止细化（直接渲染当前节点）
        if (currentLOD >= 2) {
            renderMesh(camX, camY, camZ);
            return;
        }
//        System.out.println(4);

        // 否则继续递归子节点
        for (TreeNetChunk child : children) {
            if (child != null && child.loaded) {
                child.render(camX, camY, camZ);
            }
        }
    }

    /**
     * 计算节点中心到相机的距离。
     */
    private float distanceToCamera(float cx, float cy, float cz) {
        float[] center = computeWorldCenter();
        float dx = center[0] - cx;
        float dy = center[1] - cy;
        float dz = center[2] - cz;
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    /**
     * 根据距离选择合适的 LOD 级别。
     */
    private int selectLOD(float dist) {
        if (dist < 16) return 0;
        else if (dist < 64) return 1;
        else if (dist < 256) return 2;
        else return 3;
    }

    /**
     * 计算节点在世界空间中的中心坐标。
     */
    private float[] computeWorldCenter() {
        float[] corner = computeWorldOffset();
        int half = getSize() >> 1;
        return new float[]{corner[0] + half, corner[1] + half, corner[2] + half};
    }

    /**
     * 计算该节点在世界空间中的角点偏移（左下前角）。
     * 偏移量由路径中每个方向索引决定。
     */
    private float[] computeWorldOffset() {
        float x = 0, y = 0, z = 0;
        int size = ROOT_SIZE;
        for (int i = 0; i < path.getDepth(); i++) {
            int dir = path.getDirectionAt(i);
            int half = size >> 1;
            if ((dir & 1) != 0) x += half; else x -= half;
            if ((dir & 2) != 0) y += half; else y -= half;
            if ((dir & 4) != 0) z += half; else z -= half;
            size = half;
        }
        int halfSize = getSize() >> 1;
        // 返回角点偏移（中心 - halfSize）
        return new float[]{x - halfSize, y - halfSize, z - halfSize};
    }

    /**
     * 渲染当前节点的网格（使用当前 LOD 级别）。
     * 网格顶点相对于节点角点，因此需要平移到世界坐标。
     */
    private void renderMesh(float camX, float camY, float camZ) {
        if (meshes[currentLOD] == null) {
            generateLODMesh(currentLOD);
        }
        System.out.println("aaa");

        if (meshes[currentLOD] == null || meshes[currentLOD].indexCount == 0) return;
        System.out.println("bbb");
        float[] offset = computeWorldOffset();
        glPushMatrix();
        glTranslatef(offset[0], offset[1], offset[2]);
        meshes[currentLOD].render();
        glPopMatrix();
    }

    /**
     * 生成指定 LOD 级别的网格。
     * @param lod LOD级别，0为最精细，3为最粗
     */
    private void generateLODMesh(int lod) {
        System.out.println("generateLODMesh: lod=" + lod + ", isLeaf=" + isLeaf());
        if (isLeaf()) {
            if (lod == 0) {
                meshes[lod] = buildBlockMesh();
                System.out.println("buildBlockMesh finish: " + (meshes[lod] != null ? "indexCount=" + meshes[lod].indexCount : "null"));
            } else {
                meshes[lod] = null;
                System.out.println("leaf net LOD " + lod + " set null");
            }
        } else {
            meshes[lod] = buildSimplifiedMesh(lod);
            System.out.println("buildSimplifiedMesh finish: " + (meshes[lod] != null ? "indexCount=" + meshes[lod].indexCount : "null"));
        }
        dirty = false;
    }

    /**
     * 从叶子节点的体素数据生成方块网格。
     * @return 包含所有可见面的网格
     */
    private ChunkMesh buildBlockMesh() {
        if (!isLeaf()) return null;

        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        buildBlockFaces(verts, indices);

        if (verts.isEmpty() || indices.isEmpty()) {
            return null;
        }

        // 转换为数组
        float[] vArr = new float[verts.size()];
        int[] iArr = new int[indices.size()];
        for (int i = 0; i < vArr.length; i++) vArr[i] = verts.get(i);
        for (int i = 0; i < iArr.length; i++) iArr[i] = indices.get(i);

        // 上传到 GPU
        FloatBuffer vBuf = MemoryUtil.memAllocFloat(vArr.length);
        IntBuffer iBuf = MemoryUtil.memAllocInt(iArr.length);
        vBuf.put(vArr).flip();
        iBuf.put(iArr).flip();

        ChunkMesh mesh = new ChunkMesh();
        mesh.upload(vBuf, iBuf);

        MemoryUtil.memFree(vBuf);
        MemoryUtil.memFree(iBuf);

        return mesh;
    }


    /**
     * 生成叶子节点的方块网格。
     * @param verts 顶点列表（输出）
     * @param indices 索引列表（输出）
     */
    private void buildBlockFaces(List<Float> verts, List<Integer> indices) {
        if (!isLeaf()) return; // 只有叶子节点才生成方块网格

        float[] offset = computeWorldOffset();
        int offsetX = (int) offset[0];
        int offsetY = (int) offset[1];
        int offsetZ = (int) offset[2];

        // 创建带边界的缓冲区 (size+2)
        int size = LEAF_SIZE + 2;
        short[][][] voxelsBuf = new short[size][size][size];

        // 填充缓冲区（中心区域从当前叶子读取，边缘从树查询）
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    int wx = offsetX + x - 1;
                    int wy = offsetY + y - 1;
                    int wz = offsetZ + z - 1;
                    if (x >= 1 && x <= LEAF_SIZE && y >= 1 && y <= LEAF_SIZE && z >= 1 && z <= LEAF_SIZE) {
                        voxelsBuf[x][y][z] = getVoxel(x - 1, y - 1, z - 1);
                    } else {
                        voxelsBuf[x][y][z] = getVoxelWorld(wx, wy, wz);
                    }
                }
            }
        }

        int localOffset = 0;
        for (int rx = 0; rx < LEAF_SIZE; rx++) {
            for (int ry = 0; ry < LEAF_SIZE; ry++) {
                for (int rz = 0; rz < LEAF_SIZE; rz++) {
                    short b = getVoxel(rx, ry, rz);
                    if (b == 0) continue;

                    int wx = offsetX + rx;
                    int wy = offsetY + ry;
                    int wz = offsetZ + rz;

                    // 检测六个方向的邻居（使用全局坐标查询）
                    if (getVoxelWorld(wx - 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_X, localOffset);
                        localOffset += 4; //当你添加一个新的面时，新的顶点从当前 offset 开始，所以需要把 offset 增加 4，以便下一个面使用新的顶点索引。
                    }
                    if (getVoxelWorld(wx + 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_X, localOffset);
                        localOffset += 4; //一个矩形面由 4 个顶点构成
                    }
                    if (getVoxelWorld(wx, wy - 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_Y, localOffset);
                        localOffset += 4;
                    }
                    if (getVoxelWorld(wx, wy + 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_Y, localOffset);
                        localOffset += 4;
                    }
                    if (getVoxelWorld(wx, wy, wz - 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_Z, localOffset);
                        localOffset += 4;
                    }
                    if (getVoxelWorld(wx, wy, wz + 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_Z, localOffset);
                        localOffset += 4;
                    }
                }
            }
        }
    }

    private void addFace(List<Float> v, List<Integer> i, int x, int y, int z, Direction dir, int offset) {
        float[][] faceVerts;
        switch (dir) {
            case POS_X:
                faceVerts = new float[][]{{1,0,0}, {1,0,1}, {1,1,1}, {1,1,0}};
                break;
            case NEG_X:
                faceVerts = new float[][]{{0,0,1}, {0,0,0}, {0,1,0}, {0,1,1}};
                break;
            case POS_Y:
                faceVerts = new float[][]{{0,1,0}, {1,1,0}, {1,1,1}, {0,1,1}};
                break;
            case NEG_Y:
                faceVerts = new float[][]{{0,0,0}, {0,0,1}, {1,0,1}, {1,0,0}};
                break;
            case POS_Z:
                faceVerts = new float[][]{{0,0,1}, {1,0,1}, {1,1,1}, {0,1,1}};
                break;
            case NEG_Z:
                faceVerts = new float[][]{{1,0,0}, {0,0,0}, {0,1,0}, {1,1,0}};
                break;
            default: return;
        }
        for (float[] vert : faceVerts) {
            v.add(vert[0] + x);
            v.add(vert[1] + y);
            v.add(vert[2] + z);
        }
        i.add(offset);
        i.add(offset + 1);
        i.add(offset + 2);
        i.add(offset);
        i.add(offset + 2);
        i.add(offset + 3);
    }

    // 方向枚举
    private enum Direction {
        POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z
    }

    /**
     * 获取叶子节点中的体素（局部坐标）。
     */
    private short getVoxel(int rx, int ry, int rz) {
        if (rx < 0 || rx >= LEAF_SIZE || ry < 0 || ry >= LEAF_SIZE || rz < 0 || rz >= LEAF_SIZE) return 0;
        return voxels[rx + ry * LEAF_SIZE + rz * LEAF_SIZE * LEAF_SIZE];
    }

    /**
     * 获取世界坐标处的体素（从根节点递归查找）。
     */
    private short getVoxelWorld(int wx, int wy, int wz) {
        return getRoot().getVoxelRecursive(wx, wy, wz);
    }

    /**
     * 获取根节点。
     */
    private TreeNetChunk getRoot() {
        TreeNetChunk current = this;
        while (current.parent != null) {
            current = current.parent;
        }
        return current;
    }



    private short getVoxelRecursive(int wx, int wy, int wz) {
        if (isLeaf()) {
            float[] offset = computeWorldOffset();
            int localX = wx - (int) offset[0];
            int localY = wy - (int) offset[1];
            int localZ = wz - (int) offset[2];
            return getVoxel(localX, localY, localZ);
        }
        // 计算子节点索引并递归
        int half = getSize() >> 1;
        float[] off = computeWorldOffset();
        int midX = (int) off[0] + half;
        int midY = (int) off[1] + half;
        int midZ = (int) off[2] + half;
        int dir = 0;
        if (wx >= midX) dir |= 1;
        if (wy >= midY) dir |= 2;
        if (wz >= midZ) dir |= 4;
        if (children[dir] != null) {
            return children[dir].getVoxelRecursive(wx, wy, wz);
        }
        return 0;// 子节点未加载，视为空气
    }

    /**
     * 从子节点简化生成网格（非叶子节点）。
     */
    private ChunkMesh buildSimplifiedMesh(int lod) {
        // TODO: 根据 LOD 级别合并子节点网格或生成高度图
        return new ChunkMesh();
    }

    // ========== 事件队列 ==========

    public void addEvent(Runnable event) {
        pendingEvents.add(event);
        if (!loaded) {
            writeEventsToDisk();
        }
    }

    private void writeEventsToDisk() {
        // TODO: 写入磁盘对应文件
    }

    public void processEvents() {
        // 轮询处理事件（类似随机刻）
        for (Runnable event : pendingEvents) {
            event.run();
        }
        pendingEvents.clear();
        dirty = true;
    }

    // ========== 生命周期 ==========

    public void unload() {
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