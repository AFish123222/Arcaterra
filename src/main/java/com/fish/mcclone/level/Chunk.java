package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    // 静态纹理变量
    public static int texture = 0;

    Level level;
    public AABB aabb;

    // 区块世界坐标边界
    public final int x0, y0, z0;
    public final int x1, y1, z1;

    // 区块核心：16x16x16 方块数组（区块内坐标，0~15）
    private short[] blocks = new short[16 * 16 * 16];
    private static final int SIZE = 16;
    private final int groundLevel;

    // 标记是否全空气（优化用）
    private boolean isAllOfAir;

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

        // 初始化区块地形（必加，否则blocks无数据）
        this.initSet();
    }

    /// 区块初始地形（正确生成地面）
    private void initSet() {
        // 遍历区块内坐标 0~15
        for (int inChunkX = 0; inChunkX < SIZE; inChunkX++) {
            for (int inChunkZ = 0; inChunkZ < SIZE; inChunkZ++) {
                // 转换：区块内Y坐标 → 世界Y坐标
                int worldY = level.groundY - y0;
                if (worldY >= 0 && worldY < SIZE) {
                    initSetTile(inChunkX, worldY, inChunkZ);
                }
            }
        }
    }

    /// 初始化方块（区块内坐标）
    private void initSetTile(int inChunkX, int inChunkY, int inChunkZ) {
        setTile(inChunkX, inChunkY, inChunkZ, Block.GRASS);
    }

    /// 放置方块（✅ 核心修复：使用区块内坐标计算索引）
    public void setTile(int x, int y, int z, int block) {
        // 区块内坐标越界判断
        if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE) {
            System.out.println("方块越界 Chunk: " + x + " " + y + " " + z);
            return;
        }
        // 区块内坐标索引（16x16x16，无越界）
        int index = (y * SIZE + z) * SIZE + x;
        blocks[index] = (short) block;

        // 通知世界更新
        int worldX = x0 + x;
        int worldY = y0 + y;
        int worldZ = z0 + z;
        level.calcLightDepths(worldX, worldZ, 1, 1);
        level.levelListeners.forEach(l -> l.tileChanged(worldX, worldY, worldZ));
    }

    // ==============================================
    // 🔥 核心：只渲染玩家所在的区块
    // ==============================================
    public void render(int layer, float playerX, float playerY, float playerZ) {
        // 非玩家所在区块 → 直接跳过，不渲染
        if (!isPlayerInsideChunk(playerX, playerY, playerZ)) {
            return;
        }

        Tesselator t = Tesselator.getInstance();
        t.init();

        // 遍历区块内所有方块
        for (int z = z0; z < z1; z++) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int blockId = getTile(x, y, z);
                    if (blockId == 0) continue;

                    // 只渲染暴露的面（优化）
                    boolean isExposed = !isSolidTile(x+1,y,z) || !isSolidTile(x-1,y,z) ||
                            !isSolidTile(x,y+1,z) || !isSolidTile(x,y-1,z) ||
                            !isSolidTile(x,y,z+1) || !isSolidTile(x,y,z-1);
                    if (!isExposed) continue;

                    // 渲染草方块/石头
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
        // 渲染当前区块的红色边界
        renderChunkBorder(playerX, playerZ, playerY);
    }

    /// 获取方块（✅ 核心修复：世界坐标转区块内坐标）
    public int getTile(int worldX, int worldY, int worldZ) {
        // 世界坐标转区块内坐标
        int x = worldX - x0;
        int y = worldY - y0;
        int z = worldZ - z0;

        // 区块内越界 → 空气
        if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE) {
            return 0;
        }

        int index = (y * SIZE + z) * SIZE + x;
        return blocks[index] & 0xFF;
    }

    // 判断固体方块
    public boolean isSolidTile(int x, int y, int z) {
        return getTile(x, y, z) != 0;
    }

    public boolean isLightBlocker(int x, int y, int z) {
        return isSolidTile(x, y, z);
    }

    // ==============================================
    // 判断玩家是否在当前区块内（精确判断）
    // ==============================================
    private boolean isPlayerInsideChunk(float playerX, float playerY, float playerZ) {
        return playerX >= x0 && playerX < x1
                && playerY >= y0 && playerY < y1
                && playerZ >= z0 && playerZ < z1;
    }

    // ==============================================
    // 红色线框渲染区块边界（仅玩家所在区块显示）
    // ==============================================
    public void renderChunkBorder(float playerX, float playerZ, float playerY) {
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glDisable(GL_TEXTURE_2D);
        glColor3f(1.0f, 0.0f, 0.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINES);

        // 底面
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y0, z0);

        // 顶面
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);

        // 垂直边
        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);

        glEnd();
        glPopAttrib();
    }

    // 兼容旧方法
    public void render(int layer) {
        render(layer, 0, 0, 0);
    }

    public void setDirty() {}

    // 无用/废弃方法（清理）
    public boolean isTile(int x, int y, int z) { return getTile(x, y, z) == 1; }
    public static boolean shouldIsAir(int worldX, int worldY, int worldZ) { return false; }
    public static boolean shouldIsLightBlocker(int x, int y, int z) { return false; }
    public void calcLightDepths(int x0, int y0, int x1, int y1) {}
}