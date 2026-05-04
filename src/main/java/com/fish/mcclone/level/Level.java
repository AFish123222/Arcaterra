package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import java.io.*;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Level {
    public final int width;
    public final int height;
    public final int depth;

    Chunk[] chunks;
    int xChunks;
    int yChunks;
    int zChunks;

    public final int groundY;
    private byte[] blocks;
    static int[] lightDepths;
    final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public Level(int w, int h, int d) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.blocks = new byte[w * h * d];
        this.lightDepths = new int[w * h];

        groundY = Math.min(60, depth - 2);
        // 初始化地面（可选）
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                setTile(x, groundY, z, Block.GRASS);
            }
        }

        // 正确计算区块数量
        this.xChunks = (width + 15) / 16;
        this.yChunks = (depth + 15) / 16;
        this.zChunks = (height + 15) / 16;
        this.chunks = new Chunk[this.xChunks * this.yChunks * this.zChunks];

        // 🔥 修复1：删除错误的continue，创建所有区块，无null！
        for (int chunkX = 0; chunkX < this.xChunks; chunkX++) {
            for (int chunkY = 0; chunkY < this.yChunks; chunkY++) {
                for (int chunkZ = 0; chunkZ < this.zChunks; chunkZ++) {
                    int x0 = chunkX * 16;
                    int y0 = chunkY * 16;
                    int z0 = chunkZ * 16;
                    int x1 = (chunkX + 1) * 16;
                    int y1 = (chunkY + 1) * 16;
                    int z1 = (chunkZ + 1) * 16;

                    x1 = Math.min(x1, width);
                    y1 = Math.min(y1, depth);
                    z1 = Math.min(z1, height);

                    // 🔥 强制创建所有区块，数组永远无null
                    this.chunks[(chunkX + chunkY * this.xChunks) * this.zChunks + chunkZ] =
                            new Chunk(this, x0, y0, z0, x1, y1, z1);
                }
            }
        }

        calcLightDepths(0, 0, width, height);
        load();
    }

    // 🔥 修复2：正确判断世界坐标是否为空气（方块ID=0）
    public boolean shouldIsAir(int worldX, int worldY, int worldZ) {
        if (worldX < 0 || worldY < 0 || worldZ < 0 ||
                worldX >= width || worldY >= depth || worldZ >= height) {
            return true;
        }
        return blocks[worldX + worldY * width + worldZ * width * depth] == 0;
    }

    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int z = y0; z < y1; z++) {
                int oldDepth = lightDepths[x + z * width];
                int y = depth - 1;
                while (y > 0 && !shouldIsLightBlocker(x, y, z)) y--;
                lightDepths[x + z * width] = y;

                if (oldDepth != y) {
                    int yl0 = Math.min(oldDepth, y);
                    int yl1 = Math.max(oldDepth, y);
                    int finalX = x;
                    int finalZ = z;
                    levelListeners.forEach(l -> l.lightColumnChanged(finalX, finalZ, yl0, yl1));
                }
            }
        }
    }

    private boolean shouldIsLightBlocker(int x, int y, int z) {
        return !shouldIsAir(x, y, z);
    }

    public ArrayList<AABB> getCubes(AABB aABB) {
        ArrayList<AABB> aABBs = new ArrayList<>();
        int x0 = Math.max(0, (int) aABB.x0);
        int x1 = Math.min(width, (int) (aABB.x1 + 1));
        int y0 = Math.max(0, (int) aABB.y0);
        int y1 = Math.min(depth, (int) (aABB.y1 + 1));
        int z0 = Math.max(0, (int) aABB.z0);
        int z1 = Math.min(height, (int) (aABB.z1 + 1));

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (!shouldIsAir(x, y, z)) {
                        aABBs.add(new AABB(x, y, z, x + 1, y + 1, y + 1));
                    }
                }
            }
        }
        return aABBs;
    }

    public Chunk getChunkByChunkPosition(int chunkX, int chunkY, int chunkZ) {
        return this.chunks[(chunkX + chunkY * this.xChunks) * this.zChunks + chunkZ];
    }

    public Chunk getChunkByWorldPosition(int worldX, int worldY, int worldZ) {
        int chunkX = worldX / 16;
        int chunkY = worldY / 16;
        int chunkZ = worldZ / 16;
        return getChunkByChunkPosition(chunkX, chunkY, chunkZ);
    }

    // 基础方法
    public void setTile(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return;
        blocks[x + y * width + z * width * depth] = (byte) type;
    }

    public int getTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return 0;
        return blocks[x + y * width + z * width * depth];
    }

    public void load() {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream("level.dat")))) {
            dis.readFully(this.blocks);
            calcLightDepths(0, 0, width, height);
            levelListeners.forEach(LevelListener::allChanged);
        } catch (Exception e) {
            // 无存档忽略
        }
    }

    public void save() {
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream("level.dat")))) {
            dos.write(this.blocks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addListener(LevelListener levelListener) {
        levelListeners.add(levelListener);
    }

    public float getBrightness(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return 1.0F;
        return (y < lightDepths[x + z * width]) ? 0.8F : 1.0F;
    }

    public boolean isSolidTile(int x, int i, int z) {
        return !shouldIsAir(x, i, z);
    }
}