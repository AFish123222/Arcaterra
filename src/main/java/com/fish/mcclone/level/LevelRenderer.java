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
    // 缓存视锥体，避免每帧重复分配内存
    private FrustumIntersection frustumCache = new FrustumIntersection();

    public LevelRenderer(Level level) {
        this.t = Tesselator.getInstance();
        this.level = level;
        level.addListener(this);
    }

    public void render(Player player, int layer) {
        // 矩阵栈保护（保留原有）
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // 基础GL状态（保留原有）
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_LIGHTING);
        glBindTexture(GL_TEXTURE_2D, Chunk.texture);

        // ========== 新增：获取全局视锥体 ==========
        FrustumIntersection frustum = getCurrentFrustum();

        // 遍历区块 + 视锥剔除：屏幕外区块直接跳过渲染
        for (Chunk chunk : level.chunkMap.values()) {
            if (chunk == null) continue;

            // 视锥判断：区块包围盒是否在可视范围内
            boolean inView = frustum.testAab(
                    chunk.x0, chunk.y0, chunk.z0,
                    chunk.x1, chunk.y1, chunk.z1
            );
            if (!inView) continue; // 屏幕外 → 直接跳过

            chunk.render(layer, player.x, player.y, player.z);
        }

        // 恢复矩阵（保留原有）
        glPopMatrix();
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
                    // 🔥 修复2：从Chunk获取方块+判空+正确判断固体（拾取只渲染固体方块）
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
        // 退出选择模式（必须保留）
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
            // 复用对象，减少GC
            frustumCache.set(proj.mul(model));
            return frustumCache;
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

    // 渲染选中方块高亮框
    public void renderHit(HitResult h) {
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, (float)Math.sin(System.currentTimeMillis() / 100.0D) * 0.2F + 0.4F);
        this.t.init();
        Tile.rock.renderFace(this.t, h.x, h.y, h.z, h.f);
        this.t.flush();
        GL11.glDisable(GL_BLEND);
    }

    // 脏区块更新（LOD兼容）
    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        // 无限地图简化：直接更新周边区块
        int cx0 = x0 / 16 - 1;
        int cx1 = x1 / 16 + 1;
        int cz0 = z0 / 16 - 1;
        int cz1 = z1 / 16 + 1;

        for(int cx = cx0; cx <= cx1; cx++){
            for(int cz = cz0; cz <= cz1; cz++){
                for(int cy = 0; cy <= 8; cy++){
                    Chunk chunk = level.getChunkByWorldPos(cx*16, cy*16, cz*16);
                    if(chunk != null) chunk.setDirty();
                }
            }
        }
    }

    // 监听器实现
    public void tileChanged(int x, int y, int z) {
        setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        setDirty(x - 1, y0 - 1, z - 1, x + 1, y1 + 1, z + 1);
    }
    public void allChanged() {
        // 无限地图：刷新所有加载的区块
        for(Chunk chunk : level.chunkMap.values()){
            if(chunk != null) chunk.setDirty();
        }
    }
}