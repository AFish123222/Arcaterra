package com.fish.arcaterra.level;

import com.fish.arcaterra.ChunkMesh;
import com.fish.mcclone.phys.AABB;

public class Chunk {
    public static final int SIZE = 16;
    public static final int LOD0_DIST_SQ = 256;
    public static final int LOD1_DIST_SQ = 262144;

    public final World world;
    public final int cx, cy, cz; // 区块坐标
    public final int x0, y0, z0, x1, y1, z1;
    public final AABB aabb;

    private short[] blocks;
    public boolean dirty; // 脏标记：true才重建网格
    private ChunkMesh lod0Mesh;
    private ChunkMesh lod1Mesh;

    public Chunk(World world, int cx, int cy, int cz) {
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;

        this.x0 = cx * SIZE;
        this.y0 = cy * SIZE;
        this.z0 = cz * SIZE;
        this.x1 = x0 + SIZE;
        this.y1 = y0 + SIZE;
        this.z1 = z0 + SIZE;
        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);

        blocks = new short[SIZE * SIZE * SIZE];
        lod0Mesh = new ChunkMesh();
        lod1Mesh = new ChunkMesh();
        dirty = true;

        generateTerrain();
    }

    // 修改方块，仅打脏标记，不立刻重算
    public void setBlock(int rx, int ry, int rz, short id) {
        if (rx <0 || rx >= SIZE || ry <0 || ry >= SIZE || rz <0 || rz >= SIZE) return;
        blocks[rx + ry * SIZE + rz * SIZE * SIZE] = id;
        dirty = true;
    }

    public short getBlock(int rx, int ry, int rz) {
        if (rx <0 || rx >= SIZE || ry <0 || ry >= SIZE || rz <0 || rz >= SIZE) return 0;
        return blocks[rx + ry * SIZE + rz * SIZE * SIZE];
    }

    // 分片任务调用：脏区块才重建两套网格，多帧分摊计算
    public void rebuildMesh() {
        if (!dirty) return;
        buildLod0Mesh();
        buildLod1Mesh();
        dirty = false;
    }

    // 渲染入口：只读取缓存VBO，无任何循环计算
    public void render(float px, float py, float pz) {
        float dx = (x0 + 8) - px;
        float dz = (z0 + 8) - pz;
        float distSq = dx*dx + dz*dz;

        if (distSq > LOD1_DIST_SQ) return;
        if (distSq <= LOD0_DIST_SQ) lod0Mesh.render();
        else lod1Mesh.render();
    }

    // 生成地形逻辑，仅创建区块执行一次
    private void generateTerrain(){}
    // 构建精细网格（仅脏块执行）
    private void buildLod0Mesh(){}
    // 构建预合并LOD1网格（无实时死循环）
    private void buildLod1Mesh(){}
    public boolean isSolid(int x, int y, int z){ return false; }
    public void destroy(){ lod0Mesh.destroy(); lod1Mesh.destroy(); }
}
