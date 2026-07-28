package com.fish.arcaterra.ui.hud;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import static com.fish.arcaterra.Arcaterra.WIDTH;
import static com.fish.arcaterra.Arcaterra.HEIGHT;
import static org.lwjgl.opengl.GL11.*;

/**
 * 屏幕 HUD 坐标轴指示器，显示玩家局部坐标系。
 * X=红, Y=绿, Z=蓝，方向随玩家视角旋转。
 */
public class AxesIndicator implements IHudElement {
    private float yRot;   // 玩家水平旋转
    private float xRot;   // 玩家俯仰
    private boolean visible = true;
    // 单例
    private static AxesIndicator axesIndicator;

    private AxesIndicator() {
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

        // 构建旋转矩阵（与玩家视角一致）
        Matrix4f rot = new Matrix4f().identity();
        rot.rotateY((float) Math.toRadians(yRot));
        rot.rotateX((float) Math.toRadians(xRot));

        Vector4f xDir = new Vector4f(1, 0, 0, 0).mul(rot);
        Vector4f yDir = new Vector4f(0, 1, 0, 0).mul(rot);
        Vector4f zDir = new Vector4f(0, 0, 1, 0).mul(rot);

        float len = 30f; // 屏幕像素长度
        int originX = WIDTH - 80;
        int originY = HEIGHT - 80;

        drawArrow(originX, originY, xDir.x, xDir.y, xDir.z, len, 1, 0, 0);
        drawArrow(originX, originY, yDir.x, yDir.y, yDir.z, len, 0, 1, 0);
        drawArrow(originX, originY, zDir.x, zDir.y, zDir.z, len, 0, 0, 1);
    }

    private void drawArrow(int ox, int oy, float dx, float dy, float dz, float len, float r, float g, float b) {
        // 将 3D 方向投影到屏幕 2D 平面（忽略 Y 轴投影）
        // 简单做法：取 dx, dz 作为屏幕 x, y（忽略垂直方向投影）
        // 更准确的做法：投影到屏幕平面，但为了清晰，直接用 dx, dz 表示水平，dy 表示垂直偏移
        float screenX = dx * len;
        float screenY = -dz * len; // Z 轴映射到屏幕 Y（调整符号使方向符合直觉）
        // 叠加垂直分量
        screenY += dy * len * 0.6f; // Y 轴在屏幕上的投影（俯仰影响）

        float ex = ox + screenX;
        float ey = oy + screenY;

        glLineWidth(2.5f);
        glColor3f(r, g, b);
        glBegin(GL_LINES);
        glVertex2f(ox, oy);
        glVertex2f(ex, ey);
        glEnd();

        // 箭头末端画一个小球
        glPointSize(5.0f);
        glBegin(GL_POINTS);
        glVertex2f(ex, ey);
        glEnd();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public static AxesIndicator getAxesIndicator() {
        if (axesIndicator == null) {
            axesIndicator = new AxesIndicator();
        }
        return axesIndicator;
    }
}
