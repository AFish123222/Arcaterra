package com.fish.util;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/// # 格式化打印工具集
public class PrinterUtils {
    /// 打印摄像机矩阵
    public static void printCameraMatrices(String tag) {
        System.out.println("===== " + tag + " =====");

        // 1. 打印模型视图矩阵（GL_MODELVIEW_MATRIX）
        System.out.println("--- ModelView Matrix ---");
        FloatBuffer mvBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_MODELVIEW_MATRIX, mvBuffer);
        printMatrix(mvBuffer);

        // 2. 打印投影矩阵（GL_PROJECTION_MATRIX）
        System.out.println("--- Projection Matrix ---");
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
        printMatrix(projBuffer);

        System.out.println("========================\n");
    }

    /**
     * 辅助：格式化打印 4x4 矩阵
     */
    private static void printMatrix(FloatBuffer buffer) {
        for (int i = 0; i < 4; i++) {
            System.out.printf("[%8.4f %8.4f %8.4f %8.4f]\n",
                    buffer.get(), buffer.get(), buffer.get(), buffer.get());
        }
    }
}
