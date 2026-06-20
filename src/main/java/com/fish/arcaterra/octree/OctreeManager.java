package com.fish.arcaterra.octree;

import com.fish.arcaterra.level.World;
import java.util.*;

public class OctreeManager {
    private final World world;
    private final Map<Long, OctreeNode> nodeMap = new HashMap<>();
    private final Queue<OctreeNode> buildQueue = new LinkedList<>();
    public static final int LEAF_SIZE = 4;
    private final int maxLod = 4;
    private static final int MAX_BUILD_PER_FRAME = 4; // 每帧最多重建节点数

    // ... 构造函数等

    // 获取或创建节点（不自动重建）
    private OctreeNode getOrCreateNode(int cx, int cy, int cz, int lod) {
        int size = LEAF_SIZE * (int) Math.pow(4, lod);
        long key = makeKey(cx, cy, cz, lod);
        OctreeNode node = nodeMap.get(key);
        if (node == null) {
            if (lod == 0) {
                short[] voxels = generateTerrainForLeaf(cx, cy, cz);
                node = new OctreeNode(cx, cy, cz, size, voxels);
            } else {
                int childLod = lod - 1;
                int childSize = size / 4;
                OctreeNode[] children = new OctreeNode[64];
                for (int ix = 0; ix < 4; ix++) {
                    for (int iy = 0; iy < 4; iy++) {
                        for (int iz = 0; iz < 4; iz++) {
                            int childCx = cx + ix * childSize;
                            int childCy = cy + iy * childSize;
                            int childCz = cz + iz * childSize;
                            children[(ix * 4 + iy) * 4 + iz] =
                                    getOrCreateNode(childCx, childCy, childCz, childLod);
                        }
                    }
                }
                node = new OctreeNode(cx, cy, cz, size, lod, children, null);
            }
            nodeMap.put(key, node);
            // 新节点加入重建队列
            buildQueue.offer(node);
        }
        return node;
    }

    // 更新可见节点（只创建对象，不重建）
    public void update(float playerX, float playerY, float playerZ) {
        int centerX = (int)Math.floor(playerX / LEAF_SIZE) * LEAF_SIZE;
        int centerY = (int)Math.floor(playerY / LEAF_SIZE) * LEAF_SIZE;
        int centerZ = (int)Math.floor(playerZ / LEAF_SIZE) * LEAF_SIZE;

        // 只加载 lod 0,1,2 （可根据距离调整）
        for (int lod = 0; lod <= maxLod; lod++) {
            int range = 4; // 每个方向节点数
            int step = LEAF_SIZE * (int)Math.pow(4, lod);
            int cx0 = (centerX / step) * step;
            int cy0 = (centerY / step) * step;
            int cz0 = (centerZ / step) * step;
            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    for (int dz = -range; dz <= range; dz++) {
                        int cx = cx0 + dx * step;
                        int cy = cy0 + dy * step;
                        int cz = cz0 + dz * step;
                        getOrCreateNode(cx, cy, cz, lod);
                    }
                }
            }
        }
        // 注意：不在这里重建，由 rebuildDirtyNodes 每帧分批处理
    }

    // 每帧重建有限数量的节点
    public void rebuildDirtyNodes() {
        int count = 0;
        while (!buildQueue.isEmpty() && count < MAX_BUILD_PER_FRAME) {
            OctreeNode node = buildQueue.poll();
            if (node.dirty) {
                node.rebuildMesh();
                node.dirty = false;
                count++;
            }
            // 如果节点已经干净，直接跳过（可能在重建前被其他操作清理）
        }
        // 如果队列还很大，可以在日志中警告
        if (buildQueue.size() > 100) {
            System.out.println("Build queue size: " + buildQueue.size());
        }
    }

    // 标记节点为脏（当体素改变时调用）
    public void markDirty(OctreeNode node) {
        if (node != null && !node.dirty) {
            node.dirty = true;
            buildQueue.offer(node);
        }
    }

    // 渲染时跳过没有网格的节点（mesh.indexCount == 0）
    public void render(float playerX, float playerY, float playerZ) {
        // 收集可见节点（根据距离选择 LOD）
        List<OctreeNode> visible = new ArrayList<>();
        for (OctreeNode node : nodeMap.values()) {
            if (node.mesh.indexCount == 0) continue; // 网格未生成，跳过
            float cx = node.cx + node.size/2f;
            float cy = node.cy + node.size/2f;
            float cz = node.cz + node.size/2f;
            float dist = (float)Math.sqrt(
                    (cx - playerX)*(cx - playerX) +
                            (cy - playerY)*(cy - playerY) +
                            (cz - playerZ)*(cz - playerZ)
            );
            int neededLod;
            if (dist < 20) neededLod = 0;
            else if (dist < 50) neededLod = 1;
            else if (dist < 100) neededLod = 2;
            else neededLod = 3;
            if (node.lod == neededLod) {
                visible.add(node);
            }
        }
        // 渲染可见节点
        for (OctreeNode node : visible) {
            node.render();
        }
    }

    // 清理
    public void clear() {
        for (OctreeNode node : nodeMap.values()) {
            node.destroy();
        }
        nodeMap.clear();
        buildQueue.clear();
    }

    // ... 其他方法（makeKey, generateTerrainForLeaf 等保持不变）
}