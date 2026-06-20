package com.fish.arcaterra.octree;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class OctreeNode {
    // 节点坐标（以世界坐标为单位，且对齐到 size 的倍数）
    public final int cx, cy, cz;
    public final int size;          // 边长（体素单位）
    public final int lod;           // 0 为叶子，1 为上级，...
    public final OctreeNode[] children; // 64 个子节点（仅当非叶子）
    public final short[] voxels;    // 仅叶子有效，长度为 size³（size 固定为 4）
    public ChunkMesh mesh;
    public boolean dirty;

    // 叶子节点构造
    public OctreeNode(int cx, int cy, int cz, int size, short[] voxels) {
        this(cx, cy, cz, size, 0, null, voxels);
    }

    // 内部构造
    OctreeNode(int cx, int cy, int cz, int size, int lod, OctreeNode[] children, short[] voxels) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.size = size;
        this.lod = lod;
        this.children = children;
        this.voxels = voxels;
        this.mesh = new ChunkMesh();
        this.dirty = true;
    }

    // 判断是否为叶子
    public boolean isLeaf() { return children == null; }

    // 获取世界坐标 (x,y,z) 处的体素（递归查找）
    public short getVoxel(int wx, int wy, int wz) {
        if (!contains(wx, wy, wz)) return 0;
        if (isLeaf()) {
            int lx = wx - cx;
            int ly = wy - cy;
            int lz = wz - cz;
            return voxels[lx * 4 * 4 + ly * 4 + lz];
        } else {
            // 查找子节点
            int childSize = size / 4;
            int dx = (wx - cx) / childSize;
            int dy = (wy - cy) / childSize;
            int dz = (wz - cz) / childSize;
            int idx = (dx * 4 + dy) * 4 + dz;
            return children[idx].getVoxel(wx, wy, wz);
        }
    }

    // 设置体素（仅叶子可设置，否则递归）
    public void setVoxel(int wx, int wy, int wz, short id) {
        if (!contains(wx, wy, wz)) return;
        if (isLeaf()) {
            int lx = wx - cx;
            int ly = wy - cy;
            int lz = wz - cz;
            voxels[lx * 4 * 4 + ly * 4 + lz] = id;
            dirty = true;
        } else {
            int childSize = size / 4;
            int dx = (wx - cx) / childSize;
            int dy = (wy - cy) / childSize;
            int dz = (wz - cz) / childSize;
            int idx = (dx * 4 + dy) * 4 + dz;
            children[idx].setVoxel(wx, wy, wz, id);
            // 标记自身脏（因为子节点变化可能影响父节点网格）
            dirty = true;
        }
    }

    // 检查是否包含世界坐标
    private boolean contains(int wx, int wy, int wz) {
        return wx >= cx && wx < cx + size &&
                wy >= cy && wy < cy + size &&
                wz >= cz && wz < cz + size;
    }

    // 生成网格（Greedy或简单面）
    public void rebuildMesh() {
        if (!isLeaf()) {
            // 非叶子节点：合并子节点网格？通常非叶子不直接渲染，而是作为占位，
            // 但我们也可以直接生成一个低分辨率网格（比如每个子节点采样一个体素）
            // 这里我们采用采样方式：将子节点视为4x4x4的大体素，每个大块取中心值
            buildLowResMesh();
        } else {
            buildLeafMesh();
        }
        dirty = false;
    }

    private void buildLeafMesh() {
        // 遍历 4x4x4 体素，生成简单方块
        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int offset = 0;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    short id = voxels[x * 16 + y * 4 + z];
                    if (id == 0) continue;
                    int wx = cx + x;
                    int wy = cy + y;
                    int wz = cz + z;
                    // 检查六个方向
                    // 这里简单起见，生成所有面（内部面会重叠，但初期可接受）
                    // 为了减少顶点，应检测邻居是否为空
                    // 我们实现一个简单版本：每个方块生成6个面（不论邻居）
                    // 但更高效：仅当邻居为空气才生成。
                    // 由于每个节点只有4³，性能损失不大，我们全部生成（便于观察）
                    // 但我们推荐用世界坐标检测邻居，注意递归查找可能涉及父节点
                    // 此处简化：直接生成所有面，将顶点坐标偏移到世界坐标
                    // 先获取父节点的 getVoxel，但我们需要传入 world 引用，暂不实现
                    // 为演示，我们生成所有面（将产生大量顶点，但可以看到效果）
                    // 实际使用中，应使用 World.getBlockSafe 来检测邻居
                    // 这里暂时使用本地数组检测（只检测本节点内部邻居）
                    // 为了简化，我们跳过邻居检测，生成全部6个面（共24个顶点，36个索引）
                    // 但这样会产生大量重叠，不推荐。我们改为只生成外部面（即邻居为0时）
                    // 由于无法访问外部，我们先做一个基本版本：生成所有面，但顶点只生成一次。
                    // 我们直接生成一个完整的立方体网格（所有面）
                    addFullCube(verts, indices, wx, wy, wz, offset);
                    offset += 24; // 每立方体24个顶点（每个面4个顶点）
                }
            }
        }
        uploadMesh(verts, indices);
    }

    private void addFullCube(List<Float> verts, List<Integer> indices, int x, int y, int z, int offset) {
        // 6个面的顶点，每个面4个顶点（共24个顶点）
        float[][] faceVerts = {
                {x,y,z, x+1,y,z, x+1,y+1,z, x,y+1,z}, // -Z
                {x,y,z+1, x,y+1,z+1, x+1,y+1,z+1, x+1,y,z+1}, // +Z
                {x,y,z, x,y,z+1, x,y+1,z+1, x,y+1,z}, // -X
                {x+1,y,z, x+1,y+1,z, x+1,y+1,z+1, x+1,y,z+1}, // +X
                {x,y,z, x+1,y,z, x+1,y,z+1, x,y,z+1}, // -Y
                {x,y+1,z, x,y+1,z+1, x+1,y+1,z+1, x+1,y+1,z} // +Y
        };
        for (float[] quad : faceVerts) {
            for (float f : quad) verts.add(f);
        }
        // 每个面两个三角形（索引顺序：0,1,2 和 0,2,3）
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

    private void buildLowResMesh() {
        // 对于非叶子节点，我们采样子节点的中心体素值（或平均值），生成一个粗网格
        // 每个子节点（4x4x4）视为一个大的体素，位置在子节点的中心
        // 这里使用 size/2 作为每个子节点的尺寸？更准确的是将整个节点划分为 4x4x4 块，
        // 每块大小 = size/4，然后取每块中心坐标的体素值
        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int step = size / 4;
        int offset = 0;
        for (int ix = 0; ix < 4; ix++) {
            for (int iy = 0; iy < 4; iy++) {
                for (int iz = 0; iz < 4; iz++) {
                    // 每个大块的中心坐标（世界坐标）
                    int cx0 = cx + ix * step + step/2;
                    int cy0 = cy + iy * step + step/2;
                    int cz0 = cz + iz * step + step/2;
                    // 获取该点的体素值（递归查询）
                    short id = getVoxel(cx0, cy0, cz0);
                    if (id == 0) continue;
                    // 生成一个大方块（边长 step）
                    // 顶点坐标从 cx+ix*step 到 cx+(ix+1)*step 等等
                    int x0 = cx + ix * step;
                    int y0 = cy + iy * step;
                    int z0 = cz + iz * step;
                    int x1 = x0 + step;
                    int y1 = y0 + step;
                    int z1 = z0 + step;
                    // 只有外部面需要生成（但这里简化，生成所有面）
                    // 同样，我们可以检测相邻块是否为空，但为了演示我们生成完整立方体
                    // 注意：这里使用 step 作为尺寸，为了生成正确顶点，需使用实际坐标
                    // 我们使用类似 addFullCube 但坐标动态
                    addCube(verts, indices, x0, y0, z0, x1, y1, z1, offset);
                    offset += 24;
                }
            }
        }
        uploadMesh(verts, indices);
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
        glTranslatef(cx, cy, cz);
        mesh.render();
        glPopMatrix();
    }

    public void destroy() {
        mesh.destroy();
        if (children != null) {
            for (OctreeNode child : children) {
                if (child != null) child.destroy();
            }
        }
    }
}
