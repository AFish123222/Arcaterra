package com.fish.arcaterra.ui.hud;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static com.fish.arcaterra.Arcaterra.WIDTH;
import static com.fish.arcaterra.Arcaterra.HEIGHT;

public class HudManager {
    private List<IHudElement> elements = new ArrayList<>();

    public void add(IHudElement e) { elements.add(e); }
    public void render() {
        // 切换到 2D 投影
        setupOrtho();
        for (IHudElement e : elements) {
            if (e.isVisible()) e.render();
        }
        restoreProjection();
    }
    private void setupOrtho() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();                // 保存当前透视投影矩阵
        glLoadIdentity();
        glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);  // 正交投影，坐标对应屏幕像素
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);      // 让 HUD 始终在最上面
    }

    private void restoreProjection() {
        glEnable(GL_DEPTH_TEST);       // 恢复深度测试
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();                 // 恢复之前的透视投影矩阵
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
}
