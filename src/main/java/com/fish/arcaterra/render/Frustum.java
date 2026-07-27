package com.fish.arcaterra.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 视锥体，用于剔除不可见区块。
 */
public class Frustum {
    private final float[][] planes = new float[6][4]; // 左、右、下、上、近、远

    public Frustum(Matrix4f projection, Matrix4f view) {
        Matrix4f viewProj = new Matrix4f(projection).mul(view);
        update(viewProj);
    }
    public void update(Matrix4f viewProjMatrix) {
        // 提取视锥体平面（从 view-projection 矩阵）
        float[] m = new float[16];
        viewProjMatrix.get(m);

        // 左平面
        planes[0][0] = m[3] + m[0];
        planes[0][1] = m[7] + m[4];
        planes[0][2] = m[11] + m[8];
        planes[0][3] = m[15] + m[12];
        normalize(0);

        // 右平面
        planes[1][0] = m[3] - m[0];
        planes[1][1] = m[7] - m[4];
        planes[1][2] = m[11] - m[8];
        planes[1][3] = m[15] - m[12];
        normalize(1);

        // 下平面
        planes[2][0] = m[3] + m[1];
        planes[2][1] = m[7] + m[5];
        planes[2][2] = m[11] + m[9];
        planes[2][3] = m[15] + m[13];
        normalize(2);

        // 上平面
        planes[3][0] = m[3] - m[1];
        planes[3][1] = m[7] - m[5];
        planes[3][2] = m[11] - m[9];
        planes[3][3] = m[15] - m[13];
        normalize(3);

        // 近平面
        planes[4][0] = m[3] + m[2];
        planes[4][1] = m[7] + m[6];
        planes[4][2] = m[11] + m[10];
        planes[4][3] = m[15] + m[14];
        normalize(4);

        // 远平面
        planes[5][0] = m[3] - m[2];
        planes[5][1] = m[7] - m[6];
        planes[5][2] = m[11] - m[10];
        planes[5][3] = m[15] - m[14];
        normalize(5);
    }

    private void normalize(int index) {
        float[] p = planes[index];
        float length = (float) Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]);
        p[0] /= length;
        p[1] /= length;
        p[2] /= length;
        p[3] /= length;
    }

    /**
     * 检查 AABB 是否与视锥体相交。
     */
    public boolean isAABBVisible(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            // 测试 AABB 的 8 个顶点是否都在平面外侧
            float x = p[0] > 0 ? maxX : minX;
            float y = p[1] > 0 ? maxY : minY;
            float z = p[2] > 0 ? maxZ : minZ;
            if (p[0]*x + p[1]*y + p[2]*z + p[3] < 0) {
                return false;
            }
        }
        return true;
    }
}
