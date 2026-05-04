package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;

public class Tile {
    public static Tile grass = new Tile(0);
    public static Tile rock = new Tile(1);

    public int tex;

    public Tile(int t) {
        tex = t;
    }

    // 🔥 核心修复：从Chunk获取固体状态，替代老的level.isSolidTile
    public void render(Tesselator t, Level level, int layer, int x, int y, int z) {
        Chunk chunk = level.getChunkByWorldPos(x, y, z);
        if (chunk == null) return;

        // 6个面渲染，使用chunk.isSolid判断
        if (!chunk.isSolid(x, y + 1, z)) renderFace(t, 0, x, y, z); // 上
        if (!chunk.isSolid(x, y - 1, z)) renderFace(t, 1, x, y, z); // 下
        if (!chunk.isSolid(x, y, z - 1)) renderFace(t, 2, x, y, z); // 前
        if (!chunk.isSolid(x, y, z + 1)) renderFace(t, 3, x, y, z); // 后
        if (!chunk.isSolid(x - 1, y, z)) renderFace(t, 4, x, y, z); // 左
        if (!chunk.isSolid(x + 1, y, z)) renderFace(t, 5, x, y, z); // 右
    }

    // 单个面渲染（原样保留）
    public void renderFace(Tesselator t, int f, int x, int y, int z) {
        float x0 = x + 0.0F;
        float x1 = x + 1.0F;
        float y0 = y + 0.0F;
        float y1 = y + 1.0F;
        float z0 = z + 0.0F;
        float z1 = z + 1.0F;

        if (f == 0) { t.vertex(x0, y1, z0); t.vertex(x0, y1, z1); t.vertex(x1, y1, z1); t.vertex(x1, y1, z0); }
        if (f == 1) { t.vertex(x0, y0, z0); t.vertex(x1, y0, z0); t.vertex(x1, y0, z1); t.vertex(x0, y0, z1); }
        if (f == 2) { t.vertex(x0, y0, z0); t.vertex(x0, y1, z0); t.vertex(x1, y1, z0); t.vertex(x1, y0, z0); }
        if (f == 3) { t.vertex(x1, y0, z1); t.vertex(x1, y1, z1); t.vertex(x0, y1, z1); t.vertex(x0, y0, z1); }
        if (f == 4) { t.vertex(x0, y0, z1); t.vertex(x0, y1, z1); t.vertex(x0, y1, z0); t.vertex(x0, y0, z0); }
        if (f == 5) { t.vertex(x1, y0, z0); t.vertex(x1, y1, z0); t.vertex(x1, y1, z1); t.vertex(x1, y0, z1); }
    }
}