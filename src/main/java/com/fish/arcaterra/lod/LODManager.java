package com.fish.arcaterra.lod;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;

public class LODManager {
    // 使用 ConcurrentHashMap 保证线程安全（后续可多线程）
    private final Map<Long, LODNode> nodeMap = new ConcurrentHashMap<>();
    private final List<LODNode> visibleNodes = new ArrayList<>();

    private static final int BASE_SIZE = 16;
    private static final int MAX_LOD = 2;
    private static final int LOAD_RADIUS = 4;
    private static final int MAX_CREATE_PER_FRAME = 5;   // 每帧最多创建5个节点
    private static final int MAX_REBUILD_PER_FRAME = 3;  // 每帧最多重建3个节点
    private static final float UPDATE_THRESHOLD = 4.0f;  // 移动超过4格才重新计算加载区域

    private float lastPlayerX, lastPlayerY, lastPlayerZ;
    private int lastBaseCx, lastBaseCy, lastBaseCz;

    // 内部节点类
    private static class LODNode {
        public final int cx, cy, cz;
        public final int size;
        public final int lod;
        public final ChunkMesh mesh;
        public boolean dirty;
        public boolean isLoaded;  // 标记是否已生成网格

        public LODNode(int cx, int cy, int cz, int size, int lod) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.size = size;
            this.lod = lod;
            this.mesh = new ChunkMesh();
            this.dirty = true;
            this.isLoaded = false;
        }

        // 快速地形生成（使用世界坐标判断）
        private short getVoxel(int wx, int wy, int wz) {
            // 简易地形：地面 y=8，草 y=9
            if (wy <= 8) return 1;
            if (wy == 9) return 2;
            return 0;
        }

        public void rebuild() {
            int step = 1 << lod;  // 2^lod
            int voxelsPerAxis = size / step;
            if (voxelsPerAxis < 1) voxelsPerAxis = 1;

            List<Float> verts = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            int offset = 0;

            for (int ix = 0; ix < voxelsPerAxis; ix++) {
                for (int iy = 0; iy < voxelsPerAxis; iy++) {
                    for (int iz = 0; iz < voxelsPerAxis; iz++) {
                        int wx = cx + ix * step;
                        int wy = cy + iy * step;
                        int wz = cz + iz * step;
                        short id = getVoxel(wx, wy, wz);
                        if (id == 0) continue;

                        int x0 = wx;
                        int y0 = wy;
                        int z0 = wz;
                        int x1 = wx + step;
                        int y1 = wy + step;
                        int z1 = wz + step;

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
                        offset += 24;
                    }
                }
            }

            if (verts.isEmpty()) {
                // 没有顶点，清理旧网格
                mesh.destroy();
                isLoaded = false;
                return;
            }

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

            isLoaded = true;
            dirty = false;
        }

        public void render() {
            if (!isLoaded || mesh.indexCount == 0) return;
            glPushMatrix();
            glTranslatef(cx, cy, cz);
            // 根据 LOD 不同颜色（方便调试）
            float r = 0.3f + 0.7f * (lod / (float)MAX_LOD);
            float g = 0.5f;
            float b = 0.3f + 0.7f * (1 - lod / (float)MAX_LOD);
            glColor3f(r, g, b);
            mesh.render();
            glPopMatrix();
        }

        public void destroy() {
            mesh.destroy();
        }
    }

    // 生成 key
    private long makeKey(int cx, int cy, int cz, int lod) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL) |
                (((cy + offset) & 0xFFFFFFFFL) << 32) |
                (((cz + offset) & 0xFFFFFFFFL) << 48) |
                ((long)lod << 60);
    }

    // 计算距离平方
    private float distSq(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        float dz = z1 - z2;
        return dx*dx + dy*dy + dz*dz;
    }

    public void update(float playerX, float playerY, float playerZ) {
        // 1. 如果玩家移动距离小于阈值，且已经初始化，则只处理重建，不重新计算加载
        boolean shouldRecalc = false;
        if (lastPlayerX == 0 && lastPlayerY == 0 && lastPlayerZ == 0) {
            shouldRecalc = true;
        } else {
            float dist = (float)Math.sqrt(distSq(playerX, playerY, playerZ, lastPlayerX, lastPlayerY, lastPlayerZ));
            if (dist > UPDATE_THRESHOLD) {
                shouldRecalc = true;
            }
        }

        if (shouldRecalc) {
            lastPlayerX = playerX;
            lastPlayerY = playerY;
            lastPlayerZ = playerZ;

            // 计算玩家所在的区块坐标
            int baseCx = Math.floorDiv((int)playerX, BASE_SIZE);
            int baseCy = Math.floorDiv((int)playerY, BASE_SIZE);
            int baseCz = Math.floorDiv((int)playerZ, BASE_SIZE);

            // 如果与上次相同，跳过
            if (baseCx == lastBaseCx && baseCy == lastBaseCy && baseCz == lastBaseCz) {
                // 坐标没变，但可能仍需要重建
            } else {
                lastBaseCx = baseCx;
                lastBaseCy = baseCy;
                lastBaseCz = baseCz;
            }

            // 构建需要的 key 集合（使用 HashSet 但只做一次）
            Set<Long> needed = new HashSet<>();

            // LOD 0: 近距离，加载 LOAD_RADIUS 范围内的 16x16x16 区块
            for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
                for (int dy = -LOAD_RADIUS; dy <= LOAD_RADIUS; dy++) {
                    for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                        int cx = (baseCx + dx) * BASE_SIZE;
                        int cy = (baseCy + dy) * BASE_SIZE;
                        int cz = (baseCz + dz) * BASE_SIZE;
                        needed.add(makeKey(cx, cy, cz, 0));
                    }
                }
            }

            // LOD 1: 中距离，2x 合并
            int lod1Size = BASE_SIZE * 2;
            int lod1Radius = LOAD_RADIUS * 2 + 2;
            int baseCx1 = Math.floorDiv((int)playerX, lod1Size);
            int baseCy1 = Math.floorDiv((int)playerY, lod1Size);
            int baseCz1 = Math.floorDiv((int)playerZ, lod1Size);
            for (int dx = -lod1Radius; dx <= lod1Radius; dx++) {
                for (int dy = -lod1Radius; dy <= lod1Radius; dy++) {
                    for (int dz = -lod1Radius; dz <= lod1Radius; dz++) {
                        int cx = (baseCx1 + dx) * lod1Size;
                        int cy = (baseCy1 + dy) * lod1Size;
                        int cz = (baseCz1 + dz) * lod1Size;
                        needed.add(makeKey(cx, cy, cz, 1));
                    }
                }
            }

            // LOD 2: 远距离，4x 合并
            int lod2Size = BASE_SIZE * 4;
            int lod2Radius = LOAD_RADIUS * 3 + 3;
            int baseCx2 = Math.floorDiv((int)playerX, lod2Size);
            int baseCy2 = Math.floorDiv((int)playerY, lod2Size);
            int baseCz2 = Math.floorDiv((int)playerZ, lod2Size);
            for (int dx = -lod2Radius; dx <= lod2Radius; dx++) {
                for (int dy = -lod2Radius; dy <= lod2Radius; dy++) {
                    for (int dz = -lod2Radius; dz <= lod2Radius; dz++) {
                        int cx = (baseCx2 + dx) * lod2Size;
                        int cy = (baseCy2 + dy) * lod2Size;
                        int cz = (baseCz2 + dz) * lod2Size;
                        needed.add(makeKey(cx, cy, cz, 2));
                    }
                }
            }

            // 卸载不在 needed 中的节点
            List<Long> toRemove = new ArrayList<>();
            for (Long key : nodeMap.keySet()) {
                if (!needed.contains(key)) {
                    LODNode node = nodeMap.get(key);
                    node.destroy();
                    toRemove.add(key);
                }
            }
            for (Long key : toRemove) {
                nodeMap.remove(key);
            }

            // 创建新节点（限制数量）
            int created = 0;
            for (Long key : needed) {
                if (created >= MAX_CREATE_PER_FRAME) break;
                if (!nodeMap.containsKey(key)) {
                    int lod = (int)((key >> 60) & 0xF);
                    long offset = 0x80000000L;
                    long coordKey = key & ~(0xF << 60);
                    int cx = (int)((coordKey & 0xFFFFFFFFL) - offset);
                    int cy = (int)(((coordKey >> 32) & 0xFFFFFFFFL) - offset);
                    int cz = (int)(((coordKey >> 48) & 0xFFFFFFFFL) - offset);
                    int size = BASE_SIZE * (1 << lod);
                    LODNode node = new LODNode(cx, cy, cz, size, lod);
                    nodeMap.put(key, node);
                    node.dirty = true;
                    created++;
                }
            }
        }

        // 2. 重建脏节点（限制数量，分帧执行）
        int rebuilt = 0;
        for (LODNode node : nodeMap.values()) {
            if (node.dirty && rebuilt < MAX_REBUILD_PER_FRAME) {
                node.rebuild();
                rebuilt++;
            }
        }
    }

    public void render(float playerX, float playerY, float playerZ) {
        // 收集可见节点（在玩家周围一定范围内）
        visibleNodes.clear();
        float renderDist = 80.0f; // 渲染距离

        for (LODNode node : nodeMap.values()) {
            if (!node.isLoaded) continue;
            float cx = node.cx + node.size / 2f;
            float cy = node.cy + node.size / 2f;
            float cz = node.cz + node.size / 2f;
            float dist = (float)Math.sqrt(distSq(cx, cy, cz, playerX, playerY, playerZ));
            if (dist < renderDist) {
                visibleNodes.add(node);
            }
        }

        // 渲染（按 LOD 排序，先远后近减少 Overdraw）
        visibleNodes.sort((a, b) -> Integer.compare(b.lod, a.lod));
        for (LODNode node : visibleNodes) {
            node.render();
        }
        glColor3f(1,1,1);
    }

    public void clear() {
        for (LODNode node : nodeMap.values()) {
            node.destroy();
        }
        nodeMap.clear();
        visibleNodes.clear();
    }
}