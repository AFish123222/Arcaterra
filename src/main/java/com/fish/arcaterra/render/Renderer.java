package com.fish.arcaterra.render;

import com.fish.arcaterra.test.TestCubeMesh;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    public static final Renderer INSTANCE = new Renderer();
    private final TestCubeMesh testCubeMesh;

    private Renderer() {
        testCubeMesh = new TestCubeMesh();
    }

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

    public void beginPickRender() {
        glPushAttrib(GL_ENABLE_BIT);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
    }

    public void endPickRender() {
        glPopMatrix();
        glPopAttrib();
    }

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

    // VAO/VBO方块绘制接口
    public void drawTestCubeVao(float x, float y, float z) {
        glPushMatrix();
        glTranslatef(x,y,z);
        glColor3f(0.9f,0.2f,0.2f);
        testCubeMesh.render();
        glPopMatrix();
    }

    // 退出释放
    public void destroyCubeMesh() {
        testCubeMesh.destroy();
    }
}