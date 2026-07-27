package com.fish.arcaterra.level;

import com.fish.arcaterra.render.Frustum;

import java.util.*;

public class ChunkPool {
    public static final int LOAD_RADIUS = 5; // 增大实际加载半径
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
            // 使用 DEM 判断该区块是否包含地形
            if (!hasTerrain(cx, cy, cz)) {
                return null; // 不创建空区块
            }

            chunk = new Chunk(cx, cy, cz, world);
            pool.put(key, chunk);
        }
        return chunk;
    }

    private boolean hasTerrain(int cx, int cy, int cz) {
        // 对区块内的所有 (x,z) 采样高度，判断是否有方块落在该区块的 Y 范围内
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;
                float height = world.getTerrainProvider().getHeight(wx, wz);
                int groundY = Math.round(height);
//                ///////
//                System.out.println("区块: " + cx + "," + cy + "," + cz + " | Y范围:[" + (cy*Chunk.SIZE) + "," + ((cy+1)*Chunk.SIZE-1) + "] | wx=" + wx + ", wz=" + wz + " | groundY=" + groundY + " | 包含? " + (groundY >= cy*Chunk.SIZE && groundY < (cy+1)*Chunk.SIZE));
//                /////////////
                // 如果地面高度落在该区块的 Y 范围内（cy*16 ~ cy*16+15），则有地形
                if (groundY >= cy * Chunk.SIZE && groundY < (cy + 2) * Chunk.SIZE) {
                    return true;
                }
                }
        }
        ///////////
//        return true;
        return false;
        ////////
    }

    public Chunk getChunkIfLoaded(int worldX, int worldY, int worldZ) {
        int cx = Math.floorDiv(worldX, Chunk.SIZE);
        int cy = Math.floorDiv(worldY, Chunk.SIZE);
        int cz = Math.floorDiv(worldZ, Chunk.SIZE);
        long key = getChunkKey(cx, cy, cz);
        return pool.get(key);
    }

    public void update(float playerX, float playerY, float playerZ, Frustum frustum) {
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
                    Chunk c = getOrCreateChunk(wx, wy, wz);


                    if(c==null
                        &&!(dx == 0 && dy == 0 && dz == 0 // 玩家区块
                            // todo: 毗邻区块，不然npe
                        )
                    ) continue;

                    // 距离筛选
                    float cxWorld = c.getCx() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float czWorld = c.getCz() * Chunk.SIZE + Chunk.SIZE / 2f;
                    float distSq = (cxWorld - playerX)*(cxWorld - playerX)
                            + (czWorld - playerZ)*(czWorld - playerZ);
                    if (distSq > LOAD_DIST_SQ
                    ) continue;

                    // 视锥体剔除
                    if (frustum != null) {
                        int minX = c.getCx() * Chunk.SIZE;
                        int maxX = minX + Chunk.SIZE;
                        int minY = c.getCy() * Chunk.SIZE;
                        int maxY = minY + Chunk.SIZE;
                        int minZ = c.getCz() * Chunk.SIZE;
                        int maxZ = minZ + Chunk.SIZE;
                        if (!frustum.isAABBVisible(minX, maxX, minY, maxY, minZ, maxZ)) {
                            continue;
                        }
                    }
                    visibleCache.add(c);
                }
            }
        }

        // 卸载逻辑（保持不变）
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