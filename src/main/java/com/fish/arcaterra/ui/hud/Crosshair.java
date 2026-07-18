package com.fish.arcaterra.ui.hud;

import com.fish.arcaterra.Arcaterra;

import static org.lwjgl.opengl.GL11.*;
import static com.fish.arcaterra.Arcaterra.WIDTH;
import static com.fish.arcaterra.Arcaterra.HEIGHT;

public class Crosshair implements IHudElement {
    @Override
    public void render() {
        // 画十字线（和之前一样）
        // 在 Renderer.endWorldRender() 之后，glfwSwapBuffers 之前
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);  // 正交投影，直接对应屏幕坐标
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);             // 确保准星不被遮挡
        glColor3f(1.0f, 1.0f, 1.0f);          // 白色
        glLineWidth(2.0f);

        int cx = WIDTH / 2;
        int cy = HEIGHT / 2;
        int size = 10;  // 十字线半长

        glBegin(GL_LINES);
// 横线
        glVertex2f(cx - size, cy);
        glVertex2f(cx + size, cy);
// 竖线
        glVertex2f(cx, cy - size);
        glVertex2f(cx, cy + size);
        glEnd();

// 恢复状态
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        // 小点
    }
    @Override
    public boolean isVisible() { return true; }
}
