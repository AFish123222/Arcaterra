package com.fish.mcclone.level;

public class Tile {
    // 静态方块实例
    public static Tile rock = new Tile(0);   // 岩石
    public static Tile grass = new Tile(1);  // 草方块

    private final int type;

    // 构造方法
    private Tile(int type) {
        this.type = type;
    }

    // ======================
    // 原版方块渲染（完整保留）
    // ======================
    public void render(Tesselator t, Level level, int layer, int x, int y, int z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;

        // 区分颜色：草方块绿 / 岩石灰
        if (type == 1) {
            t.color(0.3f, 0.75f, 0.2f); // 草方块
        } else {
            t.color(0.5f, 0.5f, 0.5f);   // 岩石
        }

        // 底面
        if (!level.isSolidTile(x, y-1, z)) {
            t.vertex(x0, y0, z1);
            t.vertex(x0, y0, z0);
            t.vertex(x1, y0, z0);
            t.vertex(x1, y0, z1);
        }
        // 顶面
        if (!level.isSolidTile(x, y+1, z)) {
            t.vertex(x1, y1, z1);
            t.vertex(x1, y1, z0);
            t.vertex(x0, y1, z0);
            t.vertex(x0, y1, z1);
        }
        // 前
        if (!level.isSolidTile(x, y, z-1)) {
            t.vertex(x0, y1, z0);
            t.vertex(x1, y1, z0);
            t.vertex(x1, y0, z0);
            t.vertex(x0, y0, z0);
        }
        // 后
        if (!level.isSolidTile(x, y, z+1)) {
            t.vertex(x0, y1, z1);
            t.vertex(x0, y0, z1);
            t.vertex(x1, y0, z1);
            t.vertex(x1, y1, z1);
        }
        // 左
        if (!level.isSolidTile(x-1, y, z)) {
            t.vertex(x0, y1, z1);
            t.vertex(x0, y1, z0);
            t.vertex(x0, y0, z0);
            t.vertex(x0, y0, z1);
        }
        // 右
        if (!level.isSolidTile(x+1, y, z)) {
            t.vertex(x1, y0, z1);
            t.vertex(x1, y0, z0);
            t.vertex(x1, y1, z0);
            t.vertex(x1, y1, z1);
        }
    }

    // ======================
    // ✅ 核心实现：renderFace 单独渲染一个面（你要的功能）
    // 调用：Tile.rock.renderFace(t, x, y, z, 面ID);
    // 面ID：0=下 1=上 2=前 3=后 4=左 5=右
    // ======================
    public void renderFace(Tesselator t, int x, int y, int z, int face) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;

        // 岩石灰色 / 草方块绿色
        if (type == 1) {
            t.color(0.3f, 0.75f, 0.2f);
        } else {
            t.color(0.5f, 0.5f, 0.5f);
        }

        // 根据面ID绘制对应四边形
        switch (face) {
            case 0: // 底面
                t.vertex(x0, y0, z1);
                t.vertex(x0, y0, z0);
                t.vertex(x1, y0, z0);
                t.vertex(x1, y0, z1);
                break;
            case 1: // 顶面
                t.vertex(x1, y1, z1);
                t.vertex(x1, y1, z0);
                t.vertex(x0, y1, z0);
                t.vertex(x0, y1, z1);
                break;
            case 2: // 前面 (Z-)
                t.vertex(x0, y1, z0);
                t.vertex(x1, y1, z0);
                t.vertex(x1, y0, z0);
                t.vertex(x0, y0, z0);
                break;
            case 3: // 后面 (Z+)
                t.vertex(x0, y1, z1);
                t.vertex(x0, y0, z1);
                t.vertex(x1, y0, z1);
                t.vertex(x1, y1, z1);
                break;
            case 4: // 左面 (X-)
                t.vertex(x0, y1, z1);
                t.vertex(x0, y1, z0);
                t.vertex(x0, y0, z0);
                t.vertex(x0, y0, z1);
                break;
            case 5: // 右面 (X+)
                t.vertex(x1, y0, z1);
                t.vertex(x1, y0, z0);
                t.vertex(x1, y1, z0);
                t.vertex(x1, y1, z1);
                break;
        }
    }
}