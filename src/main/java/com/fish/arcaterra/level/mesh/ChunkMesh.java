package com.fish.arcaterra.level.mesh;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/// ## 区块网格
public class ChunkMesh {
    /// 保留顶点数据与索引数据 // maybe: 占用大量内存，可以选择性的保留，随用随抓，用完释放
    private float[] vertexData;
    /// 保留顶点数据与索引数据 // maybe: 占用大量内存，可以选择性的保留，随用随抓，用完释放
    private int[] indexData;

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

    /// 提交顶点
    public void upload(FloatBuffer vertexBuf, IntBuffer indexBuf) {
        indexCount = indexBuf.remaining();
        // 保存副本（用于调试和边界提取）
        vertexData = new float[vertexBuf.remaining()];
        vertexBuf.get(vertexData);
        vertexBuf.rewind(); // 重置位置以便后续上传

        indexData = new int[indexBuf.remaining()];
        indexBuf.get(indexData);
        indexBuf.rewind();

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

    /// 获取顶点数据
    public float[] getVertexData() {
        return vertexData;
    }

    /// 获取索引数据
    public int[] getIndexData() {
        return indexData;
    }
}