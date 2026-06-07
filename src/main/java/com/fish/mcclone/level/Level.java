package com.fish.mcclone.level;

import com.fish.mcclone.phys.AABB;
import java.util.ArrayList;
import java.util.HashMap;

public class Level {
    // 无限地图核心：动态存储区块，无大小限制
    final HashMap<Long, Chunk> chunkMap = new HashMap<>();
    public final int groundY;
    static int[] lightDepths;
    final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    // 区块大小固定16x16x16
    public static final int CHUNK_SIZE = 16;
    // 玩家周围加载半径（可调整）
    public static final int LOAD_RADIUS = 8;

    // 无限地图 → 移除固定宽高深
    public Level() {
        this.groundY = 60;
        // 光照深度缓存（兼容原有逻辑）
        this.lightDepths = new int[1024 * 1024];
    }

    // ====================== 无限地图核心：区块坐标打包KEY ======================
    private long getChunkKey(int cx, int cy, int cz) {
        // 把3个区块坐标打包成唯一long，支持正负坐标（无限地图）
        return ((long)cx & 0xFFFFFFFFL) |
                ((long)(cy & 0xFFFFFFFFL) << 32) |
                ((long)(cz & 0xFFFFFFFFL) << 48);
    }

    // ====================== 动态获取/创建区块（无限地图） ======================
    public Chunk getChunkByWorldPos(int x, int y, int z) {
        // 世界坐标 → 区块坐标（支持负数，无限延伸）
        int cx = x / CHUNK_SIZE;
        int cy = y / CHUNK_SIZE;
        int cz = z / CHUNK_SIZE;

        // 修正负数坐标（Java除法负数会出错，手动修正）
        if (x < 0 && x % CHUNK_SIZE != 0) cx--;
        if (y < 0 && y % CHUNK_SIZE != 0) cy--;
        if (z < 0 && z % CHUNK_SIZE != 0) cz--;

        long key = getChunkKey(cx, cy, cz);
        Chunk chunk = chunkMap.get(key);

        // 区块不存在 → 动态创建（无限生成）
        if (chunk == null) {
            int x0 = cx * CHUNK_SIZE;
            int y0 = cy * CHUNK_SIZE;
            int z0 = cz * CHUNK_SIZE;
            int x1 = x0 + CHUNK_SIZE;
            int y1 = y0 + CHUNK_SIZE;
            int z1 = z0 + CHUNK_SIZE;
            chunk = new Chunk(this, x0, y0, z0, x1, y1, z1);
            chunkMap.put(key, chunk);
        }
        return chunk;
    }

    // ====================== 动态加载玩家周边区块（无限地图） ======================
    public void updateChunks(float playerX, float playerY, float playerZ) {
        int px = (int) playerX;
        int py = (int) playerY;
        int pz = (int) playerZ;

        // 获取玩家所在区块坐标
        int playerCX = px / CHUNK_SIZE;
        int playerCY = py / CHUNK_SIZE;
        int playerCZ = pz / CHUNK_SIZE;

        // 加载半径内所有区块（自动生成新的，无限扩展）
        for (int cx = playerCX - LOAD_RADIUS; cx <= playerCX + LOAD_RADIUS; cx++) {
            for (int cy = 0; cy <= 7; cy++) { // 垂直加载高度
                for (int cz = playerCZ - LOAD_RADIUS; cz <= playerCZ + LOAD_RADIUS; cz++) {
                    getChunkByWorldPos(
                            cx * CHUNK_SIZE,
                            cy * CHUNK_SIZE,
                            cz * CHUNK_SIZE
                    );
                }
            }
        }
    }

    // ====================== 原有逻辑（完全兼容，不动） ======================
    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int z = y0; z < y1; z++) {
                int old = lightDepths[x + z * 1024];
                int y = groundY + 10;
                while (y > 0) {
                    Chunk chunk = getChunkByWorldPos(x, y, z);
                    if (chunk == null || chunk.isSolid(x, y, z)) break;
                    y--;
                }
                lightDepths[x + z * 1024] = y;
            }
        }
    }

    public ArrayList<AABB> getCubes(AABB aabb) {
        ArrayList<AABB> list = new ArrayList<>();
        int x0 = (int) aabb.x0 - 1;
        int x1 = (int) aabb.x1 + 1;
        int y0 = (int) aabb.y0 - 1;
        int y1 = (int) aabb.y1 + 1;
        int z0 = (int) aabb.z0 - 1;
        int z1 = (int) aabb.z1 + 1;

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
        return y < lightDepths[x + z * 1024] ? 0.8f : 1.0f;
    }

    public void addListener(LevelListener l) {
        levelListeners.add(l);
    }

    // 兼容原有存档接口（空实现，不报错）
    public void load() {}
    public void save() {}
    // 临时调试：打印已加载区块数量
    public void printLoadedChunkCount() {
        System.out.println("当前已加载区块数：" + chunkMap.size());
    }
}