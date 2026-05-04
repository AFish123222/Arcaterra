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
        // 统一绑定纹理（全地图只绑1次，性能最优）
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, Chunk.texture);

        // 🔥 修复1：遍历区块+判空（杜绝空指针）
        for (Chunk chunk : level.chunks) {
            if (chunk != null) {
                // Chunk内部已实现【只渲染玩家所在区块】，直接调用即可
                chunk.render(layer, player.x, player.y, player.z);
            }
        }
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
        x0 /= 16; x1 /= 16; y0 /= 16; y1 /= 16; z0 /= 16; z1 /= 16;
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); z0 = Math.max(0, z0);
        x1 = Math.min(level.xChunks - 1, x1);
        y1 = Math.min(level.yChunks - 1, y1);
        z1 = Math.min(level.zChunks - 1, z1);

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Chunk chunk = level.chunks[(x + y * level.xChunks) * level.zChunks + z];
                    if (chunk != null) chunk.setDirty();
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
        setDirty(0, 0, 0, this.level.width, this.level.depth, this.level.height);
    }
}