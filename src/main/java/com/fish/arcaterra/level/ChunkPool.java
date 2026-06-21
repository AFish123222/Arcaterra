package com.fish.arcaterra.level;

import java.util.*;

public class ChunkPool {
    public static final int LOAD_RADIUS = 6; // 增大加载半径
    public static final int UNLOAD_RADIUS = LOAD_RADIUS + 2;
    public static final int LOAD_DIST_SQ = (LOAD_RADIUS * Chunk.SIZE) * (LOAD_RADIUS * Chunk.SIZE);
    public static final int UNLOAD_DIST_SQ = (UNLOAD_RADIUS * Chunk.SIZE) * (UNLOAD_RADIUS * Chunk.SIZE);

    private final Map<Long, Chunk> pool = new HashMap<>();
    private final World world;
    private final List<Chunk> visibleCache = new ArrayList<>();

    public ChunkPool(World world) {
        this.world = world;
    }

    private long getChunkKey(int cx, int cy, int cz) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL)
                | (((cy + offset) & 0xFFFFFFFFL) << 32)
                | (((cz + offset) & 0xFFFFFFFFL) << 48);
    }

    public Chunk getOrCreateChunk(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);

        Chunk chunk = pool.get(key);
        if (chunk == null) {
            chunk = new Chunk(cx, cy, cz, world);
            pool.put(key, chunk);
        }
        return chunk;
    }

    public Chunk getChunkIfLoaded(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);
        return pool.get(key);
    }

    public void update(float playerX, float playerY, float playerZ) {
        int pcx = Math.floorDiv((int) playerX, Chunk.SIZE);
        int pcy = Math.floorDiv((int) playerY, Chunk.SIZE);
        int pcz = Math.floorDiv((int) playerZ, Chunk.SIZE);

        visibleCache.clear();

        // 加载可见区块并构建缓存
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int wx = (pcx + dx) * Chunk.SIZE;
                    int wy = (pcy + dy) * Chunk.SIZE;
                    int wz = (pcz + dz) * Chunk.SIZE;
                    Chunk c = getOrCreateChunk(wx, wy, wz);
                    float cxWorld = c.getCx() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float czWorld = c.getCz() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float distSq = (cxWorld - playerX) * (cxWorld - playerX)
                            + (czWorld - playerZ) * (czWorld - playerZ);
                    if (distSq < LOAD_DIST_SQ) {
                        visibleCache.add(c);
                    }
                }
            }
        }

        // 卸载：删除距离超过 UNLOAD_DIST_SQ 的区块
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, Chunk> entry : pool.entrySet()) {
            Chunk c = entry.getValue();
            float cxWorld = c.getCx() * Chunk.SIZE + Chunk.SIZE / 2f;
            float czWorld = c.getCz() * Chunk.SIZE + Chunk.SIZE / 2f;
            float distSq = (cxWorld - playerX) * (cxWorld - playerX)
                    + (czWorld - playerZ) * (czWorld - playerZ);
            if (distSq > UNLOAD_DIST_SQ) {
                c.destroy();
                toRemove.add(entry.getKey());
            }
        }
        for (Long key : toRemove) {
            pool.remove(key);
        }
    }

    public List<Chunk> getVisibleChunks() {
        return visibleCache;
    }

    public List<Chunk> getDirtyChunks() {
        List<Chunk> list = new ArrayList<>();
        for (Chunk c : pool.values()) {
            if (c.dirty) {
                list.add(c);
            }
        }
        return list;
    }

    public Collection<Chunk> getAllChunks() {
        return pool.values();
    }

    public void clearAll() {
        for (Chunk c : pool.values()) {
            c.destroy();
        }
        pool.clear();
    }
}