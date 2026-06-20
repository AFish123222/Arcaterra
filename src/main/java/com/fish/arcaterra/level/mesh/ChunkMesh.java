package com.fish.arcaterra.level.mesh;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class ChunkMesh {
    private int vaoId;
    private int vboVertex;
    private int vboIndex;
    public int indexCount;

    public ChunkMesh() {
        vaoId = glGenVertexArrays();
        vboVertex = glGenBuffers();
        vboIndex = glGenBuffers();
        indexCount = 0;
    }

    public void upload(FloatBuffer vertexBuf, IntBuffer indexBuf) {
        indexCount = indexBuf.remaining();
        glBindVertexArray(vaoId);

        glBindBuffer(GL_ARRAY_BUFFER, vboVertex);
        glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndex);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuf, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void render() {
        if (indexCount <= 0) return;
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboVertex);
        glDeleteBuffers(vboIndex);
    }
}