package com.fish.arcaterra.level;

import com.fish.arcaterra.phys.AABB;
import com.fish.arcaterra.terrarium.TerrainProvider;
import com.fish.arcaterra.terrarium.NoiseTerrainProvider;

import java.util.*;

public class World {
    private final ChunkPool chunkPool;
    private final LightSystem lightSystem;
    private final TerrainProvider terrainProvider;

    public final int groundY = 8; // 仅作为 fallback，实际由 TerrainProvider 决定

    /**
     * 默认构造：使用噪声地形
     */
    public World() {
        this(new NoiseTerrainProvider());
    }

    /**
     * 构造时指定地形提供者（如 DEM 或噪声）
     * @param provider 地形高度提供者
     */
    public World(TerrainProvider provider) {
        this.terrainProvider = provider;
        this.chunkPool = new ChunkPool(this);
        this.lightSystem = new LightSystem(groundY);
    }

    // ========== 地形提供者访问 ==========
    public TerrainProvider getTerrainProvider() {
        return terrainProvider;
    }

    // ========== 区块管理 ==========
    public Chunk getChunk(int wx, int wy, int wz) {
        return chunkPool.getOrCreateChunk(wx, wy, wz);
    }

    public Chunk getChunkIfLoaded(int wx, int wy, int wz) {
        return chunkPool.getChunkIfLoaded(wx, wy, wz);
    }

    // ========== 方块查询/修改 ==========
    public short getBlockSafe(int wx, int wy, int wz) {
        Chunk c = getChunkIfLoaded(wx, wy, wz);
        if (c == null) return 0;
        int rx = wx - c.getCx() * Chunk.SIZE;
        int ry = wy - c.getCy() * Chunk.SIZE;
        int rz = wz - c.getCz() * Chunk.SIZE;
        return c.getBlock(rx, ry, rz);
    }

    public short getBlock(int wx, int wy, int wz) {
        return getBlockSafe(wx, wy, wz);
    }

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

    // ========== 世界更新与渲染 ==========
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

    public List<ChunkPool.ChunkHolder> getVisibleChunkHolders() {
        return chunkPool.getVisibleChunks();
    }

    // ========== 光照系统（保留，暂未使用） ==========
    public void calcLightArea(int x0, int z0, int x1, int z1) {
        lightSystem.calcLightDepths(x0, z0, x1, z1, this);
    }

    public float getBrightness(int x, int y, int z) {
        return lightSystem.getBrightness(x, y, z);
    }

    // ========== 碰撞检测 ==========
    public List<AABB> getCollisionBox(AABB box) {
        List<AABB> result = new ArrayList<>();
        int minX = (int) Math.floor(box.x0);
        int maxX = (int) Math.floor(box.x1 - 1e-6f);
        int minY = (int) Math.floor(box.y0);
        int maxY = (int) Math.floor(box.y1 - 1e-6f);
        int minZ = (int) Math.floor(box.z0);
        int maxZ = (int) Math.floor(box.z1 - 1e-6f);

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
}