package com.fish.arcaterra.lod;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.mesh.ChunkMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class LODManager {
    private final World world;
    private final Map<Long, LODNode> nodeMap = new HashMap<>();
    private static final int BASE_SIZE = 16; // 基础区块尺寸
    private static final int MAX_LOD = 2;    // 最大 LOD 级别，覆盖 16*2^2 = 64 体素
    private static final int LOAD_RADIUS = 4; // 基础区块加载半径

    // LOD 节点
    private class LODNode {
        public final int cx, cy, cz; // 世界坐标（对齐到 size）
        public final int size;       // 边长（体素单位）
        public final int lod;        // 0=基础区块，1=2x, 2=4x...
        public final ChunkMesh mesh;
        public boolean dirty;

        public LODNode(int cx, int cy, int cz, int size, int lod) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.size = size;
            this.lod = lod;
            this.mesh = new ChunkMesh();
            this.dirty = true;
        }

        public void rebuild() {
            int step = (int) Math.pow(2, lod); // 采样步长
            int voxelsPerAxis = size / step;   // 体素数量（每个方向）
            if (voxelsPerAxis < 1) voxelsPerAxis = 1;

            List<Float> verts = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            int offset = 0;

            // 遍历采样点
            for (int ix = 0; ix < voxelsPerAxis; ix++) {
                for (int iy = 0; iy < voxelsPerAxis; iy++) {
                    for (int iz = 0; iz < voxelsPerAxis; iz++) {
                        int wx = cx + ix * step;
                        int wy = cy + iy * step;
                        int wz = cz + iz * step;
                        short id = world.getBlockSafe(wx, wy, wz);
                        if (id == 0) continue;

                        // 生成一个立方体，大小 step
                        int x0 = wx;
                        int y0 = wy;
                        int z0 = wz;
                        int x1 = wx + step;
                        int y1 = wy + step;
                        int z1 = wz + step;

                        // 六个面的顶点（每个面4个顶点）
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

            // 上传网格
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
            dirty = false;
        }

        public void render() {
            if (mesh.indexCount == 0) return;
            glPushMatrix();
            // 节点坐标已经是世界坐标，直接平移
            glTranslatef(cx, cy, cz);
            // 临时颜色，根据 LOD 不同
            float r = 0.5f + 0.5f * (lod / (float)MAX_LOD);
            float g = 0.5f + 0.5f * (1 - lod / (float)MAX_LOD);
            float b = 0.3f;
            glColor3f(r, g, b);
            mesh.render();
            glPopMatrix();
        }

        public void destroy() {
            mesh.destroy();
        }
    }

    public LODManager(World world) {
        this.world = world;
    }

    private long makeKey(int cx, int cy, int cz, int lod) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL) |
                (((cy + offset) & 0xFFFFFFFFL) << 32) |
                (((cz + offset) & 0xFFFFFFFFL) << 48) |
                ((long)lod << 60);
    }

    public void update(float playerX, float playerY, float playerZ) {
        // 1. 计算玩家所在的区块坐标（以基础区块大小 16 为单位）
        int baseCx = Math.floorDiv((int)playerX, BASE_SIZE);
        int baseCy = Math.floorDiv((int)playerY, BASE_SIZE);
        int baseCz = Math.floorDiv((int)playerZ, BASE_SIZE);

        // 2. 收集需要加载的节点
        Set<Long> needed = new HashSet<>();

        // 2.1 加载基础区块（LOD 0）在近距离
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

        // 2.2 加载 LOD1（2x 合并），覆盖更远区域
        int lod1Radius = LOAD_RADIUS * 2 + 2;
        int lod1Size = BASE_SIZE * 2;
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

        // 2.3 加载 LOD2（4x 合并）更远
        int lod2Radius = LOAD_RADIUS * 3 + 3;
        int lod2Size = BASE_SIZE * 4;
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

        // 3. 卸载不在 needed 中的节点
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

        // 4. 创建新节点（限制每帧创建数量，防止卡顿）
        int created = 0;
        final int MAX_CREATE = 20;
        for (Long key : needed) {
            if (created >= MAX_CREATE) break;
            if (!nodeMap.containsKey(key)) {
                // 解码 lod
                int lod = (int)((key >> 60) & 0xF);
                long offset = 0x80000000L;
                long coordKey = key & ~(0xF << 60);
                int cx = (int)((coordKey & 0xFFFFFFFFL) - offset);
                int cy = (int)(((coordKey >> 32) & 0xFFFFFFFFL) - offset);
                int cz = (int)(((coordKey >> 48) & 0xFFFFFFFFL) - offset);
                int size = BASE_SIZE * (int)Math.pow(2, lod);
                LODNode node = new LODNode(cx, cy, cz, size, lod);
                nodeMap.put(key, node);
                node.dirty = true;
                created++;
            }
        }

        // 5. 重建脏节点（限制每帧重建数量）
        int rebuilt = 0;
        final int MAX_REBUILD = 10;
        for (LODNode node : nodeMap.values()) {
            if (node.dirty && rebuilt < MAX_REBUILD) {
                node.rebuild();
                rebuilt++;
            }
        }
    }

    public void render(float playerX, float playerY, float playerZ) {
        // 暂时渲染所有节点（可按距离排序优化）
        for (LODNode node : nodeMap.values()) {
            // 简单距离剔除（可选）
            float dx = node.cx + node.size/2f - playerX;
            float dy = node.cy + node.size/2f - playerY;
            float dz = node.cz + node.size/2f - playerZ;
            float dist = dx*dx + dy*dy + dz*dz;
            if (dist > 100000) continue; // 超出视野不渲染
            node.render();
        }
        glColor3f(1,1,1);
    }

    public void clear() {
        for (LODNode node : nodeMap.values()) {
            node.destroy();
        }
        nodeMap.clear();
    }
}