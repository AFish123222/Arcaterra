package com.fish.arcaterra.level;

import java.util.*;

public class ChunkPool {
    /// 区块加载半径
    public static final int LOAD_RADIUS = 4;
    public static final int LOAD_DIST_SQ = LOAD_RADIUS*16 * LOAD_RADIUS*16;
    public static final int UNLOAD_RADIUS = LOAD_RADIUS + 3;
    public static final int CHUNK_KEEP_FRAME = 60;
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

    // 获取或创建区块（用于加载和修改）
    public Chunk getOrCreateChunk(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);

        ChunkHolder holder = pool.get(key);
        if (holder == null) {
            Chunk newChunk = new Chunk(cx, cy, cz, world);
            pool.put(key, new ChunkHolder(newChunk));
            return newChunk;
        }
        holder.keepFrame = CHUNK_KEEP_FRAME;
        return holder.chunk;
    }

    // 仅查询，不存在返回 null（用于邻居检测，避免递归）
    public Chunk getChunkIfLoaded(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);
        ChunkHolder holder = pool.get(key);
        return (holder == null) ? null : holder.chunk;
    }

    private List<ChunkHolder> visibleCache = new ArrayList<>();

    public void update(float playerX, float playerY, float playerZ) {
        int pcx = Math.floorDiv((int) playerX, Chunk.SIZE);
        int pcy = Math.floorDiv((int) playerY, Chunk.SIZE);
        int pcz = Math.floorDiv((int) playerZ, Chunk.SIZE);

        visibleCache.clear();

        // 加载可见区块（使用 getOrCreateChunk）
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
                        visibleCache.add(pool.get(getChunkKey(c.getCx(), c.getCy(), c.getCz())));
                    }
                }
            }
        }

        // 卸载
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