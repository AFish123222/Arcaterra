package com.fish.mcclone.level;

import com.fish.mcclone.HitResult;
import com.fish.mcclone.Player;
import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.joml.FrustumIntersection;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class LevelRenderer implements LevelListener {
    private static final int CHUNK_SIZE = 16;
    private Level level;
    Tesselator t;

    public LevelRenderer(Level level) {
        this.t = Tesselator.getInstance();
        this.level = level;
        level.addListener(this);
    }

    public void render(Player player, int layer) {
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT | GL_TEXTURE_BIT | GL_DEPTH_BUFFER_BIT);

        // ✅【固定矩阵：必加！】保存相机矩阵 + 强制重置
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_BLEND);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glColor3f(1, 1, 1);
        glBindTexture(GL_TEXTURE_2D, Chunk.texture);

        for (Chunk chunk : level.chunkMap.values()) {
            if (chunk != null) {
                chunk.render(layer, player.x, player.y, player.z);
            }
        }

        // ✅【固定矩阵：必加！】恢复相机矩阵
        glPopMatrix();

        glPopAttrib();
    }

    public void pick(Player player) {
        float r = 3.0F;
        AABB box = player.bb.grow(r, r, r);
        int x0 = (int) box.x0;
        int x1 = (int) (box.x1 + 1.0F);
        int y0 = (int) box.y0;
        int y1 = (int) (box.y1 + 1.0F);
        int z0 = (int) box.z0;
        int z1 = (int) (box.z1 + 1.0F);

        FrustumIntersection frustum = getCurrentFrustum();
        glInitNames();
        this.t.init();

        for (int x = x0; x < x1; x++) {
            glPushName(x);
            for (int y = y0; y < y1; y++) {
                glPushName(y);
                for (int z = z0; z < z1; z++) {
                    Chunk chunk = level.getChunkByWorldPos(x, y, z);
                    if (chunk == null || !isBoxInFrustum(frustum, x, y, z) || !chunk.isSolid(x, y, z)) {
                        glPopName();
                        continue;
                    }

                    glPushName(z);
                    glPushName(0);

                    for (int i = 0; i < 6; i++) {
                        glPushName(i);
                        renderSimpleFace(x, y, z, i);
                        glPopName();
                    }

                    glPopName();
                    glPopName();
                }
                glPopName();
            }
            glPopName();
        }

        this.t.flush();
        glRenderMode(GL_RENDER);
    }

    // 获取视锥体（优化剔除）
    private FrustumIntersection getCurrentFrustum() {
        try (MemoryStack stack = stackPush()) {
            FloatBuffer projBuf = stack.mallocFloat(16);
            FloatBuffer modelBuf = stack.mallocFloat(16);

            glGetFloatv(GL_PROJECTION_MATRIX, projBuf);
            glGetFloatv(GL_MODELVIEW_MATRIX, modelBuf);

            Matrix4f proj = new Matrix4f(projBuf);
            Matrix4f model = new Matrix4f(modelBuf);

            return new FrustumIntersection(proj.mul(model));
        }
    }

    // 视锥体剔除
    private boolean isBoxInFrustum(FrustumIntersection frustum, int x, int y, int z) {
        return frustum.testAab(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f);
    }

    // 拾取用简化面渲染
    private void renderSimpleFace(int x, int y, int z, int face) {
        switch (face) {
            case 0: // y-
                t.vertex(x, y, z);
                t.vertex(x + 1, y, z);
                t.vertex(x + 1, y, z + 1);
                t.vertex(x, y, z + 1);
                break;
            case 1: // y+
                t.vertex(x, y + 1, z);
                t.vertex(x, y + 1, z + 1);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x + 1, y + 1, z);
                break;
            case 2: // z-
                t.vertex(x, y, z);
                t.vertex(x, y + 1, z);
                t.vertex(x + 1, y + 1, z);
                t.vertex(x + 1, y, z);
                break;
            case 3: // z+
                t.vertex(x, y, z + 1);
                t.vertex(x + 1, y, z + 1);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x, y + 1, z + 1);
                break;
            case 4: // x-
                t.vertex(x, y, z);
                t.vertex(x, y, z + 1);
                t.vertex(x, y + 1, z + 1);
                t.vertex(x, y + 1, z);
                break;
            case 5: // x+
                t.vertex(x + 1, y, z);
                t.vertex(x + 1, y + 1, z);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x + 1, y, z + 1);
                break;
        }
    }

    // 渲染选中方块高亮框（修复Tile→Block）
    public void renderHit(HitResult h) {
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, (float)Math.sin(System.currentTimeMillis() / 100.0D) * 0.2F + 0.4F);
        this.t.init();
        renderSimpleFace( h.x, h.y, h.z, h.f);
        this.t.flush();
        GL11.glDisable(GL_BLEND);
    }

    // ====================== 修复NPE：重写脏区块更新 ======================
    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        // 计算区块范围
        int minCX = x0 / CHUNK_SIZE;
        int maxCX = x1 / CHUNK_SIZE;
        int minCY = y0 / CHUNK_SIZE;
        int maxCY = y1 / CHUNK_SIZE;
        int minCZ = z0 / CHUNK_SIZE;
        int maxCZ = z1 / CHUNK_SIZE;

        // 遍历动态区块，不依赖固定数组
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    Chunk chunk = level.getChunkByWorldPos(cx * CHUNK_SIZE, cy * CHUNK_SIZE, cz * CHUNK_SIZE);
                    if (chunk != null) {
                        chunk.setDirty();
                    }
                }
            }
        }
    }

    // 监听器实现（无修改，兼容无限）
    public void tileChanged(int x, int y, int z) {
        setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        setDirty(x - 1, y0 - 1, z - 1, x + 1, y1 + 1, z + 1);
    }

    // 修复：全量刷新遍历动态区块
    public void allChanged() {
        for (Chunk chunk : level.chunkMap.values()) {
            if (chunk != null) {
                chunk.setDirty();
            }
        }
    }
}