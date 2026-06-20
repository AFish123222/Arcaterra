package com.fish.arcaterra.phys;

public class AABB {
    // epsilon 设为 0.001f，避免浮点误差导致卡住
    private static final float EPSILON = 0.001f;

    public float x0, y0, z0, x1, y1, z1;

    public AABB(float x0, float y0, float z0, float x1, float y1, float z1) {
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
    }

    public AABB expand(float xa, float ya, float za) {
        float _x0 = x0, _y0 = y0, _z0 = z0, _x1 = x1, _y1 = y1, _z1 = z1;
        if (xa < 0) _x0 += xa;
        if (xa > 0) _x1 += xa;
        if (ya < 0) _y0 += ya;
        if (ya > 0) _y1 += ya;
        if (za < 0) _z0 += za;
        if (za > 0) _z1 += za;
        return new AABB(_x0, _y0, _z0, _x1, _y1, _z1);
    }

    public AABB grow(float xa, float ya, float za) {
        return new AABB(x0 - xa, y0 - ya, z0 - za, x1 + xa, y1 + ya, z1 + za);
    }

    public float clipXCollide(AABB c, float xa) {
        // 先判断是否在Y和Z方向重叠（用容差）
        if (c.y1 <= y0 + EPSILON || c.y0 >= y1 - EPSILON || c.z1 <= z0 + EPSILON || c.z0 >= z1 - EPSILON)
            return xa;
        if (xa > 0 && c.x1 <= x0) {
            float max = x0 - c.x1 - EPSILON;
            if (max < xa) xa = max;
        }
        if (xa < 0 && c.x0 >= x1) {
            float max = x1 - c.x0 + EPSILON;
            if (max > xa) xa = max;
        }
        return xa;
    }

    public float clipYCollide(AABB c, float ya) {
        if (c.x1 <= x0 + EPSILON || c.x0 >= x1 - EPSILON || c.z1 <= z0 + EPSILON || c.z0 >= z1 - EPSILON)
            return ya;
        if (ya > 0 && c.y1 <= y0) {
            float max = y0 - c.y1 - EPSILON;
            if (max < ya) ya = max;
        }
        if (ya < 0 && c.y0 >= y1) {
            float max = y1 - c.y0 + EPSILON;
            if (max > ya) ya = max;
        }
        return ya;
    }

    public float clipZCollide(AABB c, float za) {
        if (c.x1 <= x0 + EPSILON || c.x0 >= x1 - EPSILON || c.y1 <= y0 + EPSILON || c.y0 >= y1 - EPSILON)
            return za;
        if (za > 0 && c.z1 <= z0) {
            float max = z0 - c.z1 - EPSILON;
            if (max < za) za = max;
        }
        if (za < 0 && c.z0 >= z1) {
            float max = z1 - c.z0 + EPSILON;
            if (max > za) za = max;
        }
        return za;
    }

    public boolean intersects(AABB c) {
        return !(c.x1 <= x0 || c.x0 >= x1 || c.y1 <= y0 || c.y0 >= y1 || c.z1 <= z0 || c.z0 >= z1);
    }

    public void move(float xa, float ya, float za) {
        x0 += xa; y0 += ya; z0 += za;
        x1 += xa; y1 += ya; z1 += za;
    }
}