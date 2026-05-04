package com.fish.mcclone.level;

import static org.lwjgl.opengl.GL11.*;

public class Tile {
    // ======================
    // 16×16 纹理图集常量（核心！适配你的纹理）
    // ======================
    private static final float UV_STEP = 1.0f / 16.0f;

    // ======================
    // 方块类型（静态实例）
    // ======================
    public static final Tile grass = new Tile(0, 0);   // 草方块 纹理(0,0)
    public static final Tile rock = new Tile(1, 0);    // 石头    纹理(1,0)

    // 方块纹理坐标
    private final float u0, v0, u1, v1;

    // ======================
    // 构造：传入纹理行列（16×16图集）
    // ======================
    private Tile(int texX, int texY) {
        this.u0 = texX * UV_STEP;
        this.v0 = texY * UV_STEP;
        this.u1 = (texX + 1) * UV_STEP;
        this.v1 = (texY + 1) * UV_STEP;
    }

    // ======================
    // 渲染完整方块（兼容Chunk批量渲染）
    // ======================
    public void render(Tesselator t, Level level, int layer, int x, int y, int z) {
        // 只渲染暴露的面（性能优化，不渲染被挡住的面）
        if (!level.isSolidTile(x, y + 1, z)) renderFace(t, 0, x, y, z); // 上
        if (!level.isSolidTile(x, y - 1, z)) renderFace(t, 1, x, y, z); // 下
        if (!level.isSolidTile(x, y, z - 1)) renderFace(t, 2, x, y, z); // 前
        if (!level.isSolidTile(x, y, z + 1)) renderFace(t, 3, x, y, z); // 后
        if (!level.isSolidTile(x - 1, y, z)) renderFace(t, 4, x, y, z); // 左
        if (!level.isSolidTile(x + 1, y, z)) renderFace(t, 5, x, y, z); // 右
    }

    /// ✅ 核心：renderFace() 渲染单个方块面
    /// @param face 面ID: 0=上 1=下 2=前 3=后 4=左 5=右
    /// @param x/y/z 方块坐标
    /// @param t 渲染器
    public void renderFace(Tesselator t, int face, int x, int y, int z) {
        float x0 = x;
        float x1 = x + 1.0f;
        float y0 = y;
        float y1 = y + 1.0f;
        float z0 = z;
        float z1 = z + 1.0f;

        // 绑定纹理坐标 + 顶点（适配16×16纹理）
        t.tex(u0, v0);
        switch (face) {
            case 0 -> { // 上面
                t.vertex(x0, y1, z0);
                t.tex(u1, v0);
                t.vertex(x1, y1, z0);
                t.tex(u1, v1);
                t.vertex(x1, y1, z1);
                t.tex(u0, v1);
                t.vertex(x0, y1, z1);
            }
            case 1 -> { // 下面
                t.vertex(x0, y0, z1);
                t.tex(u1, v0);
                t.vertex(x1, y0, z1);
                t.tex(u1, v1);
                t.vertex(x1, y0, z0);
                t.tex(u0, v1);
                t.vertex(x0, y0, z0);
            }
            case 2 -> { // 前面
                t.vertex(x0, y0, z0);
                t.tex(u1, v0);
                t.vertex(x1, y0, z0);
                t.tex(u1, v1);
                t.vertex(x1, y1, z0);
                t.tex(u0, v1);
                t.vertex(x0, y1, z0);
            }
            case 3 -> { // 后面
                t.vertex(x0, y1, z1);
                t.tex(u1, v0);
                t.vertex(x1, y1, z1);
                t.tex(u1, v1);
                t.vertex(x1, y0, z1);
                t.tex(u0, v1);
                t.vertex(x0, y0, z1);
            }
            case 4 -> { // 左面
                t.vertex(x0, y0, z1);
                t.tex(u1, v0);
                t.vertex(x0, y0, z0);
                t.tex(u1, v1);
                t.vertex(x0, y1, z0);
                t.tex(u0, v1);
                t.vertex(x0, y1, z1);
            }
            case 5 -> { // 右面
                t.vertex(x1, y1, z1);
                t.tex(u1, v0);
                t.vertex(x1, y1, z0);
                t.tex(u1, v1);
                t.vertex(x1, y0, z0);
                t.tex(u0, v1);
                t.vertex(x1, y0, z1);
            }
        }
    }
}