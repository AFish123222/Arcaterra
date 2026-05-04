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
        // ========== 全地图只绑定1次纹理（替代每个Chunk绑定） ==========
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, Chunk.texture); // 统一绑定

        // 遍历Chunk时，传入玩家坐标
        for (Chunk chunk : level.chunks) {
            // chunk may null
            chunk.render(layer, player.x, player.z, player.y);
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

        // 优化1：获取视锥体用于剔除（避免处理视野外的方块）
        FrustumIntersection frustum = getCurrentFrustum();

        glInitNames();

        // 优化2：复用 Tessellator，减少 init/flush 次数
        this.t.init();

        for (int x = x0; x < x1; x++) {
            glPushName(x);
            for (int y = y0; y < y1; y++) {
                glPushName(y);
                for (int z = z0; z < z1; z++) {
                    // 优化3：视锥体剔除 + 空气判断，提前跳过
                    if (!isBoxInFrustum(frustum, x, y, z) || this.level.shouldIsAir(x, y, z)) {
                        continue;
                    }

                    glPushName(z);
                    glPushName(0); // 保持原名字栈结构（方块类型标记）

                    // 优化4：简化面渲染（仅几何，无纹理/光照）
                    for (int i = 0; i < 6; i++) {
                        glPushName(i);
                        renderSimpleFace(x, y, z, i); // 替代复杂的 Tile.rock.renderFace
                        glPopName();
                    }

                    glPopName();
                    glPopName();
                }
                glPopName();
            }
            glPopName();
        }

        this.t.flush(); // 优化2：最后统一 flush

        // ========== 【必须加在pick方法的最后一行！】 ==========
        // 强制切回渲染模式，同时获取命中数，彻底退出选择模式
        int hits = glRenderMode(GL_RENDER);
//        System.out.println("Pick hits: " + hits); // 顺便看拾取有没有生效
    }

// ------------------------------ 辅助优化方法 ------------------------------

    /**
     * 获取当前视锥体（用于剔除视野外方块）
     */
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

    /**
     * 判断方块是否在视锥体内
     */
    private boolean isBoxInFrustum(FrustumIntersection frustum, int x, int y, int z) {
        // 方块AABB：从 (x,y,z) 到 (x+1,y+1,z+1)
        return frustum.testAab(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f);
    }

    /**
     * 简化的方块面渲染（仅几何，用于拾取）
     */
    private void renderSimpleFace(int x, int y, int z, int face) {
        // 直接用简单的四边形替代复杂的 Tile 渲染（拾取不需要纹理）
        // 这里的顶点坐标对应 Minecraft 方块的 6 个面
        switch (face) {
            case 0: // 下底面 (y-)
                t.vertex(x, y, z); // 给渲染器推顶点
                t.vertex(x + 1, y, z);
                t.vertex(x + 1, y, z + 1);
                t.vertex(x, y, z + 1);
                break;
            case 1: // 上顶面 (y+)
                t.vertex(x, y + 1, z);
                t.vertex(x, y + 1, z + 1);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x + 1, y + 1, z);
                break;
            case 2: // 北面 (z-)
                t.vertex(x, y, z);
                t.vertex(x, y + 1, z);
                t.vertex(x + 1, y + 1, z);
                t.vertex(x + 1, y, z);
                break;
            case 3: // 南面 (z+)
                t.vertex(x, y, z + 1);
                t.vertex(x + 1, y, z + 1);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x, y + 1, z + 1);
                break;
            case 4: // 西面 (x-)
                t.vertex(x, y, z);
                t.vertex(x, y, z + 1);
                t.vertex(x, y + 1, z + 1);
                t.vertex(x, y + 1, z);
                break;
            case 5: // 东面 (x+)
                t.vertex(x + 1, y, z);
                t.vertex(x + 1, y + 1, z);
                t.vertex(x + 1, y + 1, z + 1);
                t.vertex(x + 1, y, z + 1);
                break;
        }
    }

    public void renderHit(HitResult h) {
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 1);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, (float)Math.sin(System.currentTimeMillis() / 100.0D) * 0.2F + 0.4F);
        this.t.init();
        Tile.rock.renderFace(this.t, h.x, h.y, h.z, h.f);
        this.t.flush();
        GL11.glDisable(3042);
    }

    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        x0 /= 16;
        x1 /= 16;
        y0 /= 16;
        y1 /= 16;
        z0 /= 16;
        z1 /= 16;
        if (x0 < 0)
            x0 = 0;
        if (y0 < 0)
            y0 = 0;
        if (z0 < 0)
            z0 = 0;
        if (x1 >= level.xChunks)
            x1 = level.xChunks - 1;
        if (y1 >= level.yChunks)
            y1 = level.yChunks - 1;
        if (z1 >= level.zChunks)
            z1 = level.zChunks - 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++)
                    level.chunks[(x + y * level.xChunks) * level.zChunks + z].setDirty();
            }
        }
    }

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
