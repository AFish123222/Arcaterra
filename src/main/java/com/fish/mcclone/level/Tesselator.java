package com.fish.mcclone.level;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

public class Tesselator {
    private static final int MAX_VERTICES = 100000;

    private final FloatBuffer vertexBuffer  = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer texBuffer     = BufferUtils.createFloatBuffer(MAX_VERTICES * 2);
    private final FloatBuffer colorBuffer   = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);

    private int vertices = 0;
    private float u, v;
    private float r, g, b;

    private boolean useTex = false;
    private boolean useCol = false;

    public void init() {
        clear();
        useTex = false;
        useCol = false;
    }

    public void tex(float u, float v) {
        useTex = true;
        this.u = u;
        this.v = v;
    }

    public void color(float r, float g, float b) {
        useCol = true;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void vertex(float x, float y, float z) {
        vertexBuffer.put(x).put(y).put(z);
        if(useTex) texBuffer.put(u).put(v);
        if(useCol) colorBuffer.put(r).put(g).put(b);

        vertices++;
        if (vertices >= MAX_VERTICES) flush();
    }

    public void flush() {
        if (vertices == 0) return;

        vertexBuffer.flip();
        texBuffer.flip();
        colorBuffer.flip();

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, vertexBuffer);

        if(useTex) {
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, 0, texBuffer);
        }
        if(useCol) {
            glEnableClientState(GL_COLOR_ARRAY);
            glColorPointer(3, GL_FLOAT, 0, colorBuffer);
        }

        glDrawArrays(GL_QUADS, 0, vertices);

        glDisableClientState(GL_VERTEX_ARRAY);
        if(useTex) glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        if(useCol) glDisableClientState(GL_COLOR_ARRAY);

        clear();
    }

    private void clear() {
        vertices = 0;
        vertexBuffer.clear();
        texBuffer.clear();
        colorBuffer.clear();
    }
}