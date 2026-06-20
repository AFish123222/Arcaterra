package com.fish.arcaterra.level.mesh;

import static org.lwjgl.opengl.GL15.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ChunkMesh {
    private int vboId;
    private int indexVboId;
    private int vertexCount;

    public ChunkMesh() {
        vboId = glGenBuffers();
        indexVboId = glGenBuffers();
    }

    // 上传顶点+索引到GPU显存，仅方块修改时调用
    public void upload(FloatBuffer vertexBuf, IntBuffer indexBuf) {
        vertexCount = indexBuf.remaining();

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexVboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuf, GL_STATIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    // 渲染，CPU仅提交绘制指令，无循环顶点计算
    public void render() {
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexVboId);

        glVertexPointer(3, GL_FLOAT, 20, 0);
        glTexCoordPointer(2, GL_FLOAT, 20, 12);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    // 释放显存
    public void destroy() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(indexVboId);
    }
}