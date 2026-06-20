package com.fish.arcaterra.octree;

import com.fish.arcaterra.level.World;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class OctreeManager {
    private final World world;
    private final Map<Long, OctreeNode> nodeMap = new HashMap<>();
    public static final int LEAF_SIZE = 4;
    private boolean initialized = false;

    public OctreeManager(World world) {
        this.world = world;
    }

    private short[] generateTerrainForLeaf(int cx, int cy, int cz) {
        short[] voxels = new short[4 * 4 * 4];
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int y = 0; y < 4; y++) {
                    int wx = cx + x;
                    int wy = cy + y;
                    int wz = cz + z;
                    if (wy <= 8) voxels[x * 16 + y * 4 + z] = 1;
                    else if (wy == 9) voxels[x * 16 + y * 4 + z] = 2;
                    else voxels[x * 16 + y * 4 + z] = 0;
                }
            }
        }
        return voxels;
    }

    private long makeKey(int cx, int cy, int cz) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL) |
                (((cy + offset) & 0xFFFFFFFFL) << 32) |
                (((cz + offset) & 0xFFFFFFFFL) << 48);
    }

    // 强制生成一定范围内的叶子节点（用于初始化）
    private void forceGenerate(int centerX, int centerY, int centerZ, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    int cx = centerX + dx * LEAF_SIZE;
                    int cy = centerY + dy * LEAF_SIZE;
                    int cz = centerZ + dz * LEAF_SIZE;
                    long key = makeKey(cx, cy, cz);
                    if (!nodeMap.containsKey(key)) {
                        short[] voxels = generateTerrainForLeaf(cx, cy, cz);
                        OctreeNode node = new OctreeNode(cx, cy, cz, LEAF_SIZE, voxels);
                        nodeMap.put(key, node);
                        node.dirty = true;
                    }
                }
            }
        }
    }

    public void update(float playerX, float playerY, float playerZ) {
        if (!initialized) {
            // 首次加载，生成以玩家为中心的一块区域
            int centerX = (int) Math.floor(playerX / LEAF_SIZE) * LEAF_SIZE;
            int centerY = (int) Math.floor(playerY / LEAF_SIZE) * LEAF_SIZE;
            int centerZ = (int) Math.floor(playerZ / LEAF_SIZE) * LEAF_SIZE;
            forceGenerate(centerX, centerY, centerZ, 5); // 范围 5 个叶子
            initialized = true;
        }

        // 重建脏节点
        for (OctreeNode node : nodeMap.values()) {
            if (node.dirty) {
                node.rebuildMesh();
                node.dirty = false;
            }
        }
    }

    public void render(float playerX, float playerY, float playerZ) {
        // 渲染所有节点（调试用）
        for (OctreeNode node : nodeMap.values()) {
            if (node.mesh.indexCount > 0) {
                // 临时颜色：根据高度渐变
                float r = 0.5f + (node.cy - 0) / 20f;
                float g = 0.5f;
                float b = 0.5f - (node.cy - 0) / 20f;
                glColor3f(Math.min(1, r), Math.min(1, g), Math.max(0, b));
                node.render();
            }
        }
        glColor3f(1, 1, 1); // 重置
    }

    public void rebuildDirtyNodes() {
        // 已在 update 中处理
    }

    public void clear() {
        for (OctreeNode node : nodeMap.values()) {
            node.destroy();
        }
        nodeMap.clear();
        initialized = false;
    }
}