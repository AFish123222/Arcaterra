package com.fish.arcaterra.octree;

import com.fish.arcaterra.level.World;
import java.util.*;

public class OctreeManager {
    private final World world;
    private final Map<Long, OctreeNode> nodeMap = new HashMap<>();
    private final List<OctreeNode> visibleNodes = new ArrayList<>();
    public static final int LEAF_SIZE = 4;
    private final int maxLod = 4;
    private static final int MAX_NODES_PER_FRAME = 20; // 每帧最多创建节点数

    public OctreeManager(World world) {
        this.world = world;
    }

    // 生成叶子体素（简易地形）
    private short[] generateTerrainForLeaf(int cx, int cy, int cz) {
        short[] voxels = new short[4 * 4 * 4];
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int y = 0; y < 4; y++) {
                    int wx = cx + x;
                    int wy = cy + y;
                    int wz = cz + z;
                    // 地面在 y=8
                    if (wy <= 8) voxels[x * 16 + y * 4 + z] = 1;
                    else if (wy == 9) voxels[x * 16 + y * 4 + z] = 2;
                    else voxels[x * 16 + y * 4 + z] = 0;
                }
            }
        }
        return voxels;
    }

    private long makeKey(int cx, int cy, int cz, int lod) {
        long offset = 0x80000000L;
        long k = ((cx + offset) & 0xFFFFFFFFL) |
                (((cy + offset) & 0xFFFFFFFFL) << 32) |
                (((cz + offset) & 0xFFFFFFFFL) << 48);
        return k ^ (lod << 60); // 混合 lod
    }

    // 获取或创建节点（懒加载）
    private OctreeNode getOrCreateNode(int cx, int cy, int cz, int lod) {
        long key = makeKey(cx, cy, cz, lod);
        OctreeNode node = nodeMap.get(key);
        if (node == null) {
            int size = LEAF_SIZE * (int) Math.pow(4, lod);
            if (lod == 0) {
                short[] voxels = generateTerrainForLeaf(cx, cy, cz);
                node = new OctreeNode(cx, cy, cz, size, voxels);
            } else {
                // 非叶子节点：创建子节点（注意：子节点可能尚未创建，但这里只创建空壳）
                // 为了懒加载，我们创建节点但 children 先置为 null，等需要时再生成
                // 但为了简单，我们只创建叶子节点，非叶子节点通过叶子合并得到？或直接不创建非叶子？
                // 更好的方式：只创建叶子节点，渲染时动态合并为粗网格。
                // 但我们的设计是非叶子节点也存储网格，所以需要创建。
                // 为了减少创建量，我们暂时只创建叶子节点，LOD 通过采样叶子实现？
                // 这里我们简化：只生成叶子节点，LOD 由叶子节点采样生成（不创建非叶子节点）
                // 所以我们只处理 lod==0
                return null; // 暂不创建非叶子
            }
            if (node != null) {
                nodeMap.put(key, node);
                node.dirty = true;
            }
        }
        return node;
    }

    // 更新：根据玩家位置加载节点
    public void update(float playerX, float playerY, float playerZ) {
        int centerX = (int) Math.floor(playerX / LEAF_SIZE) * LEAF_SIZE;
        int centerY = (int) Math.floor(playerY / LEAF_SIZE) * LEAF_SIZE;
        int centerZ = (int) Math.floor(playerZ / LEAF_SIZE) * LEAF_SIZE;

        // 计算需要加载的叶子节点列表，按距离排序
        List<Long> neededKeys = new ArrayList<>();
        int range = 10; // 以叶子为单位
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    int cx = centerX + dx * LEAF_SIZE;
                    int cy = centerY + dy * LEAF_SIZE;
                    int cz = centerZ + dz * LEAF_SIZE;
                    // 计算距离
                    float dist = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist < range) {
                        long key = makeKey(cx, cy, cz, 0);
                        neededKeys.add(key);
                    }
                }
            }
        }

        // 按距离排序（使用简单排序）
        neededKeys.sort((k1, k2) -> {
            // 从key中提取坐标并计算距离
            long k1c = k1 & ~(0xF << 60);
            long k2c = k2 & ~(0xF << 60);
            // 简化：按key排序
            return Long.compare(k1c, k2c);
        });

        // 每帧只创建有限个节点
        int created = 0;
        for (long key : neededKeys) {
            if (created >= MAX_NODES_PER_FRAME) break;
            if (!nodeMap.containsKey(key)) {
                // 解码坐标
                long offset = 0x80000000L;
                int cx = (int)((key & 0xFFFFFFFFL) - offset);
                int cy = (int)(((key >> 32) & 0xFFFFFFFFL) - offset);
                int cz = (int)(((key >> 48) & 0xFFFFFFFFL) - offset);
                OctreeNode node = getOrCreateNode(cx, cy, cz, 0);
                if (node != null) {
                    created++;
                    node.dirty = true;
                }
            }
        }

        // 重建脏节点（异步？先同步）
        for (OctreeNode node : nodeMap.values()) {
            if (node.dirty) {
                node.rebuildMesh();
                node.dirty = false;
            }
        }
    }

    // 重建脏节点（供外部调用，已包含在 update 中）
    public void rebuildDirtyNodes() {
        // 已在 update 内处理，此方法可留空或另作他用
    }

    // 渲染可见节点（根据距离选择 LOD）
    public void render(float playerX, float playerY, float playerZ) {
        visibleNodes.clear();
        for (OctreeNode node : nodeMap.values()) {
            float cx = node.cx + node.size / 2f;
            float cy = node.cy + node.size / 2f;
            float cz = node.cz + node.size / 2f;
            float dist = (float) Math.sqrt(
                    (cx - playerX) * (cx - playerX) +
                            (cy - playerY) * (cy - playerY) +
                            (cz - playerZ) * (cz - playerZ)
            );
            // 简单 LOD 选择：距离近的叶子直接渲染，远的我们暂时不渲染（因为没有非叶子节点）
            // 为了演示，我们只渲染距离 < 20 的叶子
            if (dist < 20) {
                visibleNodes.add(node);
            }
        }
        for (OctreeNode node : visibleNodes) {
            if (node.mesh.indexCount > 0) {
                node.render();
            }
        }
    }

    public void clear() {
        for (OctreeNode node : nodeMap.values()) {
            node.destroy();
        }
        nodeMap.clear();
    }
}