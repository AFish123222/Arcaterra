package com.fish.arcaterra.test;

import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class TestCubeMesh {
    private int vao;
    private int vboVert;
    private int vboIdx;
    private int indexCount;

    private static final float[] VERT = {
            0,0,0, 1,0,0, 1,1,0, 0,1,0,
            0,0,1, 1,0,1, 1,1,1, 0,1,1
    };
    private static final int[] IDX = {
            0,1,2, 0,2,3,
            1,5,6, 1,6,2,
            5,4,7, 5,7,6,
            4,0,3, 4,3,7,
            3,2,6, 3,6,7,
            4,5,1, 4,1,0
    };

    public TestCubeMesh() {
        vao = glGenVertexArrays();
        vboVert = glGenBuffers();
        vboIdx = glGenBuffers();

        FloatBuffer vBuf = MemoryUtil.memAllocFloat(VERT.length);
        vBuf.put(VERT).flip();
        IntBuffer iBuf = MemoryUtil.memAllocInt(IDX.length);
        iBuf.put(IDX).flip();
        indexCount = IDX.length;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vboVert);
        glBufferData(GL_ARRAY_BUFFER, vBuf, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIdx);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuf, GL_STATIC_DRAW);
        glBindVertexArray(0);

        MemoryUtil.memFree(vBuf);
        MemoryUtil.memFree(iBuf);
    }

    public void render() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vboVert);
        glDeleteBuffers(vboIdx);
    }
}