package com.fish.mcclone.level;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

/// # 渲染器（贪心网格适配版）
public class Tesselator {
    // ======================
    // 单例模式
    // ======================
    private static Tesselator instance;
    private Tesselator() {}

    public static Tesselator getInstance() {
        if (instance == null) {
            instance = new Tesselator();
        }
        return instance;
    }

    // ======================
    // 核心缓冲区
    // ======================
    private static final int MAX_VERTICES = 50000;
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer texBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 2);

    // 状态变量
    private int vertices;
    private float u, v;
    private boolean useTex = false;
    private boolean useCol = false;
    private boolean useLineRender = false; // 默认实体渲染
    private float r, g, b;

    /// 初始化
    public void init() {
        vertices = 0;
        useTex = false;
        useCol = false;
        useLineRender = false;
        vertexBuffer.clear();
        colorBuffer.clear();
        texBuffer.clear();
    }

    // 切换线框模式
    public void setLineRender(boolean line) {
        this.useLineRender = line;
    }

    // ======================
    // 纹理坐标
    // ======================
    public void tex(float u, float v) {
        useTex = true;
        this.u = u;
        this.v = v;
    }

    // ======================
    // 颜色设置
    // ======================
    public void color(float r, float g, float b) {
        useCol = true;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    // ======================
    // 提交顶点
    // ======================
    public void vertex(float x, float y, float z) {
        vertexBuffer.put(x).put(y).put(z);
        if (useTex) texBuffer.put(u).put(v);
        if (useCol) colorBuffer.put(r).put(g).put(b);
        vertices++;

        if (vertices >= MAX_VERTICES) {
            flush();
            init();
        }
    }

    // ======================
    // 贪心网格专用：添加矩形面
    // ======================
    public void addFace(float nx, float ny, float nz, float x1, float y1, float z1, float x2, float y2, float z2, int texId) {
        // 纹理坐标（16x16图集，草方块=0，石头=1）
        float u = (texId % 16) / 16f;
        float v = (texId / 16) / 16f;
        tex(u, v);

        // 绘制四边形（贪心合并后的大面）
        if (nx == 1) { // +X
            vertex(x1, y1, z1);
            vertex(x1, y2, z1);
            vertex(x1, y2, z2);
            vertex(x1, y1, z2);
        } else if (nx == -1) { // -X
            vertex(x1, y1, z1);
            vertex(x1, y1, z2);
            vertex(x1, y2, z2);
            vertex(x1, y2, z1);
        } else if (ny == 1) { // +Y
            vertex(x1, y1, z1);
            vertex(x2, y1, z1);
            vertex(x2, y1, z2);
            vertex(x1, y1, z2);
        } else if (ny == -1) { // -Y
            vertex(x1, y1, z1);
            vertex(x1, y1, z2);
            vertex(x2, y1, z2);
            vertex(x2, y1, z1);
        } else if (nz == 1) { // +Z
            vertex(x1, y1, z1);
            vertex(x2, y1, z1);
            vertex(x2, y2, z1);
            vertex(x1, y2, z1);
        } else if (nz == -1) { // -Z
            vertex(x1, y1, z1);
            vertex(x1, y2, z1);
            vertex(x2, y2, z1);
            vertex(x2, y1, z1);
        }
    }

    // ======================
    // 获取缓冲区数据（贪心网格缓存）
    // ======================
    public float[] getVertices() {
        vertexBuffer.flip();
        float[] arr = new float[vertexBuffer.remaining()];
        vertexBuffer.get(arr);
        return arr;
    }

    public float[] getTexCoords() {
        texBuffer.flip();
        float[] arr = new float[texBuffer.remaining()];
        texBuffer.get(arr);
        return arr;
    }

    // ======================
    // 刷新渲染
    // ======================
    public void flush() {
        if (vertices == 0) return;

        vertexBuffer.flip();
        colorBuffer.flip();
        texBuffer.flip();

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, vertexBuffer);

        if (useCol) {
            glEnableClientState(GL_COLOR_ARRAY);
            glColorPointer(3, GL_FLOAT, 0, colorBuffer);
        }
        if (useTex) {
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, 0, texBuffer);
        }

        // 渲染模式：实体/线框
        if (useLineRender) {
            glColor3f(0,0,0);
            glDrawArrays(GL_LINES, 0, vertices);
        } else {
            glDrawArrays(GL_QUADS, 0, vertices);
        }

        // 还原状态
        glDisableClientState(GL_VERTEX_ARRAY);
        if (useCol) glDisableClientState(GL_COLOR_ARRAY);
        if (useTex) glDisableClientState(GL_TEXTURE_COORD_ARRAY);

        init();
    }
}