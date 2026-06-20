package com.fish.arcaterra.level;

import com.fish.arcaterra.phys.AABB;
import java.util.*;

public class World {
    public final int groundY = 8;
    private final ChunkPool chunkPool;
    private final LightSystem lightSystem;

    public World() {
        this.chunkPool = new ChunkPool(this);
        this.lightSystem = new LightSystem(groundY);
    }

    // 获取或创建区块（用于加载和修改）
    public Chunk getChunk(int wx, int wy, int wz) {
        return chunkPool.getOrCreateChunk(wx, wy, wz);
    }

    // 仅查询区块，不存在返回 null
    public Chunk getChunkIfLoaded(int wx, int wy, int wz) {
        return chunkPool.getChunkIfLoaded(wx, wy, wz);
    }

    // 安全获取方块，区块不存在时返回 0（空气）
    public short getBlockSafe(int wx, int wy, int wz) {
        Chunk c = getChunkIfLoaded(wx, wy, wz);
        if (c == null) return 0;
        int rx = wx - c.getCx() * Chunk.SIZE;
        int ry = wy - c.getCy() * Chunk.SIZE;
        int rz = wz - c.getCz() * Chunk.SIZE;
        return c.getBlock(rx, ry, rz);
    }

    // 对外公开的 getBlock，直接调用 getBlockSafe
    public short getBlock(int wx, int wy, int wz) {
        return getBlockSafe(wx, wy, wz);
    }

    // 设置方块，若区块不存在则创建
    public void setBlock(int wx, int wy, int wz, short id) {
        Chunk c = getChunk(wx, wy, wz);
        int rx = wx - c.getCx() * Chunk.SIZE;
        int ry = wy - c.getCy() * Chunk.SIZE;
        int rz = wz - c.getCz() * Chunk.SIZE;
        c.setBlock(rx, ry, rz, id);
        c.dirty = true;
        markNeighborDirty(wx, wy, wz);
    }

    private void markNeighborDirty(int wx, int wy, int wz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Chunk c = getChunkIfLoaded(wx + dx, wy + dy, wz + dz);
                    if (c != null) c.dirty = true;
                }
            }
        }
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

    public void calcLightArea(int x0, int z0, int x1, int z1) {
        lightSystem.calcLightDepths(x0, z0, x1, z1, this);
    }

    public float getBrightness(int x, int y, int z) {
        return lightSystem.getBrightness(x, y, z);
    }

    public List<AABB> getCollisionBox(AABB box) {
        List<AABB> result = new ArrayList<>();
        // 将范围略微扩大，防止边界遗漏
        int minX = (int) Math.floor(box.x0 - 0.001f);
        int maxX = (int) Math.floor(box.x1 + 0.001f);
        int minY = (int) Math.floor(box.y0 - 0.001f);
        int maxY = (int) Math.floor(box.y1 + 0.001f);
        int minZ = (int) Math.floor(box.z0 - 0.001f);
        int maxZ = (int) Math.floor(box.z1 + 0.001f);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (getBlockSafe(x, y, z) != 0) {
                        result.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                    }
                }
            }
        }
        return result;
    }

    public List<ChunkPool.ChunkHolder> getVisibleChunkHolders() {
        return chunkPool.getVisibleChunks();
    }
}