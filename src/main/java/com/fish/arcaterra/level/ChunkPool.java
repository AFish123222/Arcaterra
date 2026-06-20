package com.fish.arcaterra.level;

import java.util.*;

public class ChunkPool {
    public static final int LOAD_RADIUS = 6;
    public static final int UNLOAD_RADIUS = LOAD_RADIUS + 3;
    public static final int CHUNK_KEEP_FRAME = 60;
    // 新增LOD距离常量
    public static final int LOD0_DIST_SQ = 25600;

    private final Map<Long, ChunkHolder> pool = new HashMap<>();
    private final World world;

    public ChunkPool(World world) {
        this.world = world;
    }

    public static class ChunkHolder {
        public final Chunk chunk;
        public int keepFrame;

        public ChunkHolder(Chunk chunk) {
            this.chunk = chunk;
            this.keepFrame = CHUNK_KEEP_FRAME;
        }
    }

    private long getChunkKey(int cx, int cy, int cz) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL)
                | (((cy + offset) & 0xFFFFFFFFL) << 32)
                | (((cz + offset) & 0xFFFFFFFFL) << 48);
    }

    public Chunk getChunk(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);

        ChunkHolder holder = pool.get(key);
        if (holder == null) {
            // 修正构造参数顺序 cx, cz, world
            Chunk newChunk = new Chunk(cx, cz, world);
            pool.put(key, new ChunkHolder(newChunk));
            return newChunk;
        }
        holder.keepFrame = CHUNK_KEEP_FRAME;
        return holder.chunk;
    }

    private List<ChunkHolder> visibleCache = new ArrayList<>();

    public void update(float playerX, float playerY, float playerZ) {
        int pcx = Math.floorDiv((int) playerX, Chunk.SIZE);
        int pcy = Math.floorDiv((int) playerY, Chunk.SIZE);
        int pcz = Math.floorDiv((int) playerZ, Chunk.SIZE);

        visibleCache.clear();

        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int wx = (pcx + dx) * Chunk.SIZE;
                    int wy = (pcy + dy) * Chunk.SIZE;
                    int wz = (pcz + dz) * Chunk.SIZE;
                    Chunk c = getChunk(wx, wy, wz);
                    // 用区块中心世界坐标计算距离
                    float cxWorld = c.getCx() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float czWorld = c.getCz() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float distSq = (cxWorld - playerX) * (cxWorld - playerX) + (czWorld - playerZ) * (czWorld - playerZ);
                    if (distSq < LOD0_DIST_SQ) {
                        visibleCache.add(pool.get(getChunkKey(c.getCx(), 0, c.getCz())));
                    }
                }
            }
        }

        // 简易卸载倒计时（补齐逻辑）
        List<Long> removeKeys = new ArrayList<>();
        for (Map.Entry<Long, ChunkHolder> entry : pool.entrySet()) {
            ChunkHolder h = entry.getValue();
            h.keepFrame--;
            if (h.keepFrame <= 0) {
                removeKeys.add(entry.getKey());
                h.chunk.destroy();
            }
        }
        for (long k : removeKeys) pool.remove(k);
    }

    public List<ChunkHolder> getVisibleChunks() {
        return visibleCache;
    }

    public List<Chunk> getDirtyChunks() {
        List<Chunk> list = new ArrayList<>();
        for (ChunkHolder holder : pool.values()) {
            if (holder.chunk.dirty) {
                list.add(holder.chunk);
            }
        }
        return list;
    }

    public Collection<ChunkHolder> getAllChunks() {
        return pool.values();
    }

    public void clearAll() {
        for (ChunkHolder holder : pool.values()) {
            holder.chunk.destroy();
        }
        pool.clear();
    }
}