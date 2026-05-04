package com.fish.mcclone.level;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/// # 渲染器
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
    // 核心缓冲区（修复：添加texBuffer）
    // ======================
    private static final int MAX_VERTICES = 50000;
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer texBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 2); // ✅ 修复纹理坐标缓冲区

    // 状态变量
    /// 顶点计数器
    private int vertices;
    private float u, v;
    private boolean useTex = false;
    private boolean useCol = false; // ✅ 修复缺失的颜色状态
    /// 启用线框
    private boolean useLineRender = true;
    /// 颜色缓存
    private float r, g, b;

    /// 初始化（重置所有状态）<br>
    /// 清空vertexBuffer colorBuffer texBuffer
    public void init() {
        vertices = 0;
        useTex = false;
        useCol = false;
        vertexBuffer.clear();
        colorBuffer.clear();
        texBuffer.clear(); // ✅ 清空纹理缓冲区
    }

    public boolean isUseLineRender() {
        return useLineRender;
    }

    // ======================
    // 纹理坐标（16×16图集专用）
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
    // 提交顶点（修复：无重复写入、无报错）
    // ======================
    public void vertex(float x, float y, float z) {
        // 写入顶点
        vertexBuffer.put(x).put(y).put(z);

        // 写入纹理坐标（启用时）
        if (useTex) {
            texBuffer.put(u).put(v);
        }

        // 写入颜色（启用时）
        if (useCol) {
            colorBuffer.put(r).put(g).put(b);
        }

        vertices++; // 顶点计数器+1

        // 缓冲区满自动刷新（防溢出）
        if (vertices >= MAX_VERTICES) {
            flush();
            init();
        }
    }

    // ======================
    // 刷新渲染（修复：纹理缓冲区flip+状态正确管理）
    // ======================
    public void flush() {
        if (vertices == 0) return;

        // 翻转所有缓冲区（必须！）
        vertexBuffer.flip();
        colorBuffer.flip();
        if (useTex) texBuffer.flip(); // ✅ 修复纹理缓冲区翻转

        // 启用顶点数组
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, vertexBuffer);

        // 启用颜色数组
        if (useCol) {
            glEnableClientState(GL_COLOR_ARRAY);
            glColorPointer(3, GL_FLOAT, 0, colorBuffer);
        }

        // 启用纹理坐标数组（核心！适配16×16纹理）
        if (useTex) {
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, 0, texBuffer);
        }


        if (!useLineRender)  glDrawArrays(GL_QUADS, 0, vertices); // 绘制四边形
        // 线框
        if (useLineRender) {
            useCol = false;
            useTex = false;
            glColor3i(0,0,0); // black
//            PrinterUtils.printVertexBuffer(vertexBuffer); // 打印顶点
            glDrawArrays(GL_LINE_LOOP, 0, vertices);
            // 还原初始状态
            useCol = true;
            useTex = true;
        }

        // 禁用所有数组（状态还原）
        glDisableClientState(GL_VERTEX_ARRAY);
        if (useCol) glDisableClientState(GL_COLOR_ARRAY);
        if (useTex) glDisableClientState(GL_TEXTURE_COORD_ARRAY);

        // 重置状态
        init();
    }
}