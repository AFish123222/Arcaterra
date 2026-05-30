package com.fish.mcclone.level;

import com.fish.mcclone.phys.AABB;
import java.util.ArrayList;

public class Level {
    public final int width;
    public final int height;
    public final int depth;


    // 区块数组：未来替换为 八叉树(LOD树) 存储
    Chunk[] chunks;
    int xChunks, yChunks, zChunks;
    public final int groundY;
    static int[] lightDepths;
    final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public Level(int w, int h, int d) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.lightDepths = new int[w * h];
        groundY = Math.min(60, depth - 2);

        // 计算区块数量
        this.xChunks = (width + 15) / 16;
        this.yChunks = (depth + 15) / 16;
        this.zChunks = (height + 15) / 16;
        this.chunks = new Chunk[xChunks * yChunks * zChunks];

        // 创建所有基础区块 (16x16x16 叶子节点)
        for (int cx = 0; cx < xChunks; cx++) {
            for (int cy = 0; cy < yChunks; cy++) {
                for (int cz = 0; cz < zChunks; cz++) {
                    int x0 = cx * 16;
                    int y0 = cy * 16;
                    int z0 = cz * 16;
                    int x1 = Math.min(x0 + 16, width);
                    int y1 = Math.min(y0 + 16, depth);
                    int z1 = Math.min(z0 + 16, height);

                    chunks[(cx + cy * xChunks) * zChunks + cz] = new Chunk(this, x0, y0, z0, x1, y1, z1);
                }
            }
        }

        calcLightDepths(0, 0, width, height);
    }

    // ====================== LOD 树通用方法 ======================
    public Chunk getChunkByWorldPos(int x, int y, int z) {
        int cx = x / 16;
        int cy = y / 16;
        int cz = z / 16;
        if (cx < 0 || cy < 0 || cz < 0 || cx >= xChunks || cy >= yChunks || cz >= zChunks) return null;
        return chunks[(cx + cy * xChunks) * zChunks + cz];
    }

    // ====================== 光照/碰撞（通用） ======================
    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int z = y0; z < y1; z++) {
                int old = lightDepths[x + z * width];
                int y = depth - 1;
                while (y > 0) {
                    Chunk chunk = getChunkByWorldPos(x, y, z);
                    if (chunk == null || chunk.isSolid(x, y, z)) break;
                    y--;
                }
                lightDepths[x + z * width] = y;
            }
        }
    }

    public ArrayList<AABB> getCubes(AABB aabb) {
        ArrayList<AABB> list = new ArrayList<>();
        int x0 = Math.max(0, (int) aabb.x0);
        int x1 = Math.min(width, (int) aabb.x1 + 1);
        int y0 = Math.max(0, (int) aabb.y0);
        int y1 = Math.min(depth, (int) aabb.y1 + 1);
        int z0 = Math.max(0, (int) aabb.z0);
        int z1 = Math.min(height, (int) aabb.z1 + 1);

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Chunk chunk = getChunkByWorldPos(x, y, z);
                    if (chunk != null && chunk.isSolid(x, y, z)) {
                        list.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                    }
                }
            }
        }
        return list;
    }

    public float getBrightness(int x, int y, int z) {
        return y < lightDepths[x+z*width] ? 0.8f : 1.0f;
    }

    public void addListener(LevelListener l) { levelListeners.add(l); }

    // ====================== 存档功能（补回，解决主类报错） ======================
    public void load() {}
    public void save() {}
}