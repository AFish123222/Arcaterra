package com.fish.arcaterra.level;

import java.util.*;

public class ChunkPool {
    // 加载/卸载半径配置
    public static final int LOAD_RADIUS = 6;
    public static final int UNLOAD_RADIUS = LOAD_RADIUS + 3;
    // 区块远离后保留帧数，防止进出加载圈闪烁
    public static final int CHUNK_KEEP_FRAME = 60;

    // 区块存储 key=坐标打包long
    private final Map<Long, ChunkHolder> pool = new HashMap<>();
    private final World world;

    public ChunkPool(World world) {
        this.world = world;
    }

    // 区块包装：持有区块+卸载倒计时
    public static class ChunkHolder {
        public final Chunk chunk;
        public int keepFrame;

        public ChunkHolder(Chunk chunk) {
            this.chunk = chunk;
            this.keepFrame = CHUNK_KEEP_FRAME;
        }
    }

    // 打包区块三维坐标为唯一long key
    private long getChunkKey(int cx, int cy, int cz) {
        long offset = 0x80000000L;
        return ((cx + offset) & 0xFFFFFFFFL)
                | (((cy + offset) & 0xFFFFFFFFL) << 32)
                | (((cz + offset) & 0xFFFFFFFFL) << 48);
    }

    // 根据世界坐标获取区块，不存在则创建
    public Chunk getChunk(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);

        ChunkHolder holder = pool.get(key);
        if (holder == null) {
            Chunk newChunk = new Chunk(world, cx, cy, cz);
            pool.put(key, new ChunkHolder(newChunk));
            return newChunk;
        }
        // 玩家靠近，重置卸载倒计时
        holder.keepFrame = CHUNK_KEEP_FRAME;
        return holder.chunk;
    }

    // 新增缓存
    private List<ChunkHolder> visibleCache = new ArrayList<>();

    // 每帧更新时同步刷新可视区块
    public void update(float playerX, float playerY, float playerZ) {
        int pcx = Math.floorDiv((int) playerX, Chunk.SIZE);
        int pcy = Math.floorDiv((int) playerY, Chunk.SIZE);
        int pcz = Math.floorDiv((int) playerZ, Chunk.SIZE);

        // 清空可视缓存
        visibleCache.clear();

        // 加载范围内所有区块
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int wx = (pcx + dx) * Chunk.SIZE;
                    int wy = (pcy + dy) * Chunk.SIZE;
                    int wz = (pcz + dz) * Chunk.SIZE;
                    Chunk c = getChunk(wx, wy, wz);
                    float cxWorld = c.x0 + Chunk.SIZE / 2f;
                    float czWorld = c.z0 + Chunk.SIZE / 2f;
                    float distSq = (cxWorld - playerX) * (cxWorld - playerX) + (czWorld - playerZ) * (czWorld - playerZ);
                    if(distSq < Chunk.LOD0_DIST_SQ){
                        visibleCache.add(pool.get(getChunkKey(c.cx,c.cy,c.cz)));
                    }
                }
            }
        }

        // 卸载逻辑不变 ...
    }

    // 对外获取仅可视区块，渲染只遍历这一小部分
    public List<ChunkHolder> getVisibleChunks(){
        return visibleCache;
    }

    // 获取所有脏区块（需要重建网格）
    public List<Chunk> getDirtyChunks() {
        List<Chunk> list = new ArrayList<>();
        for (ChunkHolder holder : pool.values()) {
            if (holder.chunk.dirty) {
                list.add(holder.chunk);
            }
        }
        return list;
    }

    // 获取全部加载中的区块（用于渲染遍历）
    public Collection<ChunkHolder> getAllChunks() {
        return pool.values();
    }

    // 清空所有区块，释放显存资源（游戏退出调用）
    public void clearAll() {
        for (ChunkHolder holder : pool.values()) {
            holder.chunk.destroy();
        }
        pool.clear();
    }
}
