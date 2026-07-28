package com.fish.arcaterra.ui.hud;

import static com.fish.arcaterra.Arcaterra.WIDTH;
import static com.fish.arcaterra.Arcaterra.HEIGHT;
import static org.lwjgl.opengl.GL11.*;

/**
 * 调试指示器：在屏幕上显示玩家局部坐标轴方向
 * 用三个彩色点表示 X+1, Y+1, Z+1 在屏幕上的投影位置
 */
public class DebugIndicators implements IHudElement {
    private float px, py, pz;
    private float yRot, xRot;
    private boolean visible = true;
    private static DebugIndicators debugIndicators;

    private DebugIndicators() {
    }

    public static DebugIndicators getDebugIndicators() {
        if (debugIndicators == null) {
            debugIndicators = new DebugIndicators();
        }
        return debugIndicators;
    }

    public void setPlayerPos(float px, float py, float pz) {
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    public void setRotation(float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render() {
        if (!visible) return;

        // 构建旋转矩阵（将局部坐标转换到世界坐标）
        float yaw = (float) Math.toRadians(yRot);
        float pitch = (float) Math.toRadians(xRot);
        float cosY = (float) Math.cos(yaw);
        float sinY = (float) Math.sin(yaw);
        float cosP = (float) Math.cos(pitch);
        float sinP = (float) Math.sin(pitch);

        // 计算方向向量
        float[] dirX = {cosY, 0, -sinY};          // 局部 X 轴（右）
        float[] dirY = {0, 1, 0};                  // 局部 Y 轴（上）
        float[] dirZ = {sinY, 0, cosY};            // 局部 Z 轴（前）

        // 俯仰影响
        dirY[0] = -sinP * cosY;
        dirY[2] = -sinP * -sinY;
        dirZ[0] = cosP * sinY;
        dirZ[2] = cosP * cosY;

        // 世界坐标
        float length = 1.0f;
        float[][] points = {
                {px + dirX[0] * length, py + dirY[0] * length, pz + dirZ[0] * length, 1, 0, 0}, // X+1 (红)
                {px + dirX[1] * length, py + dirY[1] * length, pz + dirZ[1] * length, 0, 1, 0}, // Y+1 (绿)
                {px + dirX[2] * length, py + dirY[2] * length, pz + dirZ[2] * length, 0, 0, 1}  // Z+1 (蓝)
        };

        // 投影到屏幕（简单透视投影）
        float aspect = (float) WIDTH / HEIGHT;
        float fov = 0.1f; // 与 glFrustum 的 near 一致，这里假设是 0.1
        float near = 0.1f;
        float far = 2000f;

        glPointSize(80.0f);
        for (float[] pt : points) {
            float wx = pt[0];
            float wy = pt[1];
            float wz = pt[2];

            // 从眼睛坐标到相机坐标（假设相机在 (px, py, pz)，朝向视角方向）
            float dx = wx - px;
            float dy = wy - py;
            float dz = wz - pz;

            // 应用视角旋转
            float ex = dx * cosY + dz * sinY;
            float ey = dy * cosP - (dx * sinY - dz * cosY) * sinP;
            float ez = (dx * sinY - dz * cosY) * cosP + dy * sinP;

            if (ez < 0.1f) continue; // 在相机后面，跳过

            // 透视投影到屏幕坐标
            float screenX = WIDTH / 2f + (ex / ez) * (WIDTH / (2 * aspect * fov));
            float screenY = HEIGHT / 2f - (ey / ez) * (HEIGHT / (2 * fov));

            glColor3f(pt[3], pt[4], pt[5]);
            glBegin(GL_POINTS);
            glVertex2f(screenX, screenY);
            glEnd();
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}