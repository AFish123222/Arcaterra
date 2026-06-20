package com.fish.arcaterra.render;

import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    public static final Renderer INSTANCE = new Renderer();

    private Renderer(){}

    // 渲染世界统一入口
    public void beginWorldRender() {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_LIGHTING);
    }

    public void endWorldRender() {
        glPopMatrix();
        glPopAttrib();
    }

    /// #### 拾取专用隔离矩阵
    /// 拾取三重循环里有 continue 提前跳出，已经写了 glPopName()，是安全的，只要保证每一层 push 都有对应 pop 就不会名称栈溢出卡死
    public void beginPickRender() {
        glPushAttrib(GL_ENABLE_BIT);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glInitNames();
    }


    public void endPickRender() {
        glPopMatrix();
        glPopAttrib();
        // 拾取结束不用手动清名称栈，glRenderMode 会自动重置名称栈
    }

    // 高亮UI隔离渲染
    public void beginOverlayRender() {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void endOverlayRender() {
        glPopMatrix();
        glPopAttrib();
    }
}
