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

    public void drawTestCubeVao(float x, float y, float z) {
        glPushMatrix();
        glTranslatef(x, y, z);
        glColor3f(0.9f, 0.2f, 0.2f);
        testCubeMesh.render();
        glColor3f(1f, 1f, 1f);
        glPopMatrix();
    }

    public void destroyCubeMesh() {
        testCubeMesh.destroy();
    }

    public void drawEyeRay(float px, float py, float pz,float xRot, float yRot) {

        float pitch = (float) Math.toRadians(xRot);
        float yaw = (float) Math.toRadians(yRot);
        float dx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float dy = (float) (-Math.sin(pitch));
        float dz = (float) (Math.cos(pitch) * Math.cos(yaw));




        //////////
        // 保存当前状态
        glPushAttrib(GL_ENABLE_BIT | GL_LINE_BIT);

        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_LINE_SMOOTH);
        glLineWidth(2.0f);

        // 设置颜色（亮绿色，醒目）
        glColor3f(0.0f, 1.0f, 0.0f);

        float length = 5.0f;
        float ex = px + dx * length;
        float ey = py + dy * length;
        float ez = pz + dz * length;

        //////////
        px = px;
        py = py;
        ///////////

        glBegin(GL_LINES);
        glVertex3f(px, py, pz);
        glVertex3f(ex, ey, ez);
        glEnd();

        // 在终点画一个小球或十字标记（可选）
        // 这里简单画一个小点
        glPointSize(4.0f);
        glColor3f(1.0f, 0.0f, 0.0f);
        glBegin(GL_POINTS);
        glVertex3f(ex, ey, ez);
        glEnd();

        // 恢复状态
        glPopAttrib();
        ////////
    }
}