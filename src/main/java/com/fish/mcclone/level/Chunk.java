package com.fish.mcclone.level;

import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    // 🔥 核心修复：静态纹理变量（解决 Chunk.texture 报错）
    public static int texture = 0;

    public AABB aabb;
    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;

    // 渲染距离优化
    private static final int RENDER_DISTANCE = 16;
    private final int groundLevel;

    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);
        this.groundLevel = level.depth * 2 / 3;
    }

    // 优化渲染：带玩家坐标（渲染距离）
    public void render(int layer, float playerX, float playerZ) {
        // 单例Tesselator
        Tesselator t = Tesselator.getInstance();
        t.init();

        // 最优循环顺序 Z→Y→X
        for (int z = z0; z < z1; z++) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    // 渲染距离跳过
                    float dx = x - playerX;
                    float dz = z - playerZ;
                    if (dx * dx + dz * dz > RENDER_DISTANCE * RENDER_DISTANCE) continue;

                    // 空气直接跳过
                    int blockId = level.getTile(x, y, z);
                    if (blockId == 0) continue;

                    // 只渲染暴露面（被包围的方块不画）
                    boolean isExposed =
                            !level.isSolidTile(x+1,y,z) || !level.isSolidTile(x-1,y,z) ||
                                    !level.isSolidTile(x,y+1,z) || !level.isSolidTile(x,y-1,z) ||
                                    !level.isSolidTile(x,y,z+1) || !level.isSolidTile(x,y,z-1);
                    if (!isExposed) continue;

                    // 渲染方块
                    int tex = (y < groundLevel) ? 0 : 1;
                    if (tex == 0) {
                        Tile.rock.render(t, level, layer, x, y, z);
                    } else {
                        Tile.grass.render(t, level, layer, x, y, z);
                    }
                }
            }
        }

        t.flush();
    }

    // 兼容旧代码
    public void render(int layer) {
        render(layer, 0, 0);
    }

    public void setDirty() {}
}