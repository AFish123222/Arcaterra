package com.fish.mcclone.level;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

public class Tesselator {
    // ======================
    // 单例模式核心（你要的实现）
    // ======================
    private static Tesselator instance;

    // 私有构造，禁止外部创建
    private Tesselator() {}

    // ✅ 你要的 getInstance() 方法实现
    public static Tesselator getInstance() {
        if (instance == null) {
            instance = new Tesselator();
        }
        return instance;
    }

    // ======================
    // 高性能渲染参数
    // ======================
    private static final int MAX_VERTICES = 50000;
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private int vertices;

    // 初始化缓冲区
    public void init() {
        vertices = 0;
        vertexBuffer.clear();
        colorBuffer.clear();
    }

    // 设置颜色
    public void color(float r, float g, float b) {
        colorBuffer.put(r).put(g).put(b);
    }

    // 提交顶点
    public void vertex(float x, float y, float z) {
        vertexBuffer.put(x).put(y).put(z);
        vertices++;

        // 满了才刷新，减少GPU调用
        if (vertices >= MAX_VERTICES) {
            flush();
        }
    }

    // 刷新渲染
    public void flush() {
        if (vertices == 0) return;

        vertexBuffer.flip();
        colorBuffer.flip();

        // OpenGL 渲染配置
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, vertexBuffer);

        glEnableClientState(GL_COLOR_ARRAY);
        glColorPointer(3, GL_FLOAT, 0, colorBuffer);

        glDrawArrays(GL_QUADS, 0, vertices);

        // 关闭状态
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);

        init();
    }
}