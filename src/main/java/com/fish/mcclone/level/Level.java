package com.fish.mcclone.level;

import com.fish.mcclone.phys.AABB;
import java.io.*;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/// # 存档
public class Level {
    public final int width;
    public final int height;
    public final int depth;

    private byte[] blocks;
    private int[] lightDepths;
    private final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public Level(int w, int h, int d) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.blocks = new byte[w * h * d];
        this.lightDepths = new int[w * h];

        // 安全生成地面（防止Y坐标越界）
        int groundY = Math.min(60, depth - 2);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                setTile(x, groundY, z, 1); // 草方块
            }
        }

        calcLightDepths(0, 0, w, h);
        load();
    }

    // ======================
    // ✅ 补全 + 优化 getTile（核心！）
    // ======================
    public int getTile(int x, int y, int z) {
        // 越界直接返回空气(0)
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) {
            return 0;
        }
        // 统一索引计算（和setTile完全一致）
        int index = (y * height + z) * width + x;
        return blocks[index] & 0xFF; // 无符号byte转换
    }

    // ======================
    // 优化：统一调用getTile，删除重复计算
    // ======================
    public boolean isTile(int x, int y, int z) {
        return getTile(x, y, z) == 1; // 草方块
    }

    public boolean isSolidTile(int x, int y, int z) {
        return getTile(x, y, z) != 0; // 非空气即为固体
    }

    public boolean isLightBlocker(int x, int y, int z) {
        return isSolidTile(x, y, z);
    }

    // ======================
    // 优化：碰撞检测跳过空气，大幅提速
    // ======================
    public ArrayList<AABB> getCubes(AABB aABB) {
        ArrayList<AABB> aABBs = new ArrayList<>();
        int x0 = Math.max(0, (int) aABB.x0);
        int x1 = Math.min(width, (int) (aABB.x1 + 1.0F));
        int y0 = Math.max(0, (int) aABB.y0);
        int y1 = Math.min(depth, (int) (aABB.y1 + 1.0F));
        int z0 = Math.max(0, (int) aABB.z0);
        int z1 = Math.min(height, (int) (aABB.z1 + 1.0F));

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (isSolidTile(x, y, z)) { // 只处理固体
                        aABBs.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                    }
                }
            }
        }
        return aABBs;
    }

    // ======================
    // 以下为原有逻辑（无修改，保证兼容）
    // ======================
    public void load() {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream("level.dat")))) {
            dis.readFully(this.blocks);
            calcLightDepths(0, 0, width, height);
            levelListeners.forEach(LevelListener::allChanged);
        } catch (Exception e) {
            // 无存档时不报错，避免干扰运行
        }
    }

    public void save() {
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream("level.dat")))) {
            dos.write(this.blocks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x0 + x1; x++) {
            for (int z = y0; z < y0 + y1; z++) {
                int oldDepth = lightDepths[x + z * width];
                int y = depth - 1;
                while (y > 0 && !isLightBlocker(x, y, z)) y--;
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

    public void addListener(LevelListener levelListener) {
        levelListeners.add(levelListener);
    }

    public void removeListener(LevelListener levelListener) {
        levelListeners.remove(levelListener);
    }

    public float getBrightness(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return 1.0F;
        return (y < lightDepths[x + z * width]) ? 0.8F : 1.0F;
    }

    public void setTile(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return;
        blocks[(y * height + z) * width + x] = (byte) type;
        calcLightDepths(x, z, 1, 1);
        levelListeners.forEach(l -> l.tileChanged(x, y, z));
    }
}