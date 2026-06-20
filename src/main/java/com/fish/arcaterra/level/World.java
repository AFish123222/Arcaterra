package com.fish.arcaterra.level;

import com.fish.arcaterra.phys.AABB;

import java.util.*;

public class World {
    public final int groundY = 60;
    private final ChunkPool chunkPool;
    private final LightSystem lightSystem;

    public World() {
        this.chunkPool = new ChunkPool(this);
        this.lightSystem = new LightSystem(groundY);
    }

    public Chunk getChunk(int wx, int wy, int wz) {
        return chunkPool.getChunk(wx, wy, wz);
    }

    public void updateChunks(float playerX, float playerY, float playerZ) {
        chunkPool.update(playerX, playerY, playerZ);
    }

    public List<Chunk> getDirtyChunks() {
        return chunkPool.getDirtyChunks();
    }

    public Collection<ChunkPool.ChunkHolder> getAllChunkHolders() {
        return chunkPool.getAllChunks();
    }

    public void destroyAllChunks() {
        chunkPool.clearAll();
        lightSystem.clear();
    }

    // 对外光照接口
    public void calcLightArea(int x0, int z0, int x1, int z1) {
        lightSystem.calcLightDepths(x0, z0, x1, z1, this);
    }

    public float getBrightness(int x, int y, int z) {
        return lightSystem.getBrightness(x, y, z);
    }

    // 碰撞盒子查询
    public List<AABB> getCollisionBox(AABB box) {
        List<AABB> result = new ArrayList<>();
        int minX = (int) Math.floor(box.x0);
        int maxX = (int) Math.floor(box.x1);
        int minY = (int) Math.floor(box.y0);
        int maxY = (int) Math.floor(box.y1);
        int minZ = (int) Math.floor(box.z0);
        int maxZ = (int) Math.floor(box.z1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Chunk c = getChunk(x, y, z);
                    if (c.isSolid(x, y, z)) {
                        result.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                    }
                }
            }
        }
        return result;
    }
}