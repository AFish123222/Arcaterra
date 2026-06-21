package com.fish.arcaterra.terrarium;

import com.fish.arcaterra.level.Chunk;

public class TerrainGenerator {
    private final TerrainProvider provider;
    private final int seaLevel = 8; // 海平面高度（未使用，可扩展）

    public TerrainGenerator(TerrainProvider provider) {
        this.provider = provider;
    }

    /**
     * 为指定区块填充地形方块
     */
    public void generateChunk(Chunk chunk) {
        int cx = chunk.getCx();
        int cz = chunk.getCz();
        int cy = chunk.getCy(); // 区块的 Y 坐标（实际游戏中可能用到）

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                // 世界坐标
                float worldX = cx * Chunk.SIZE + x;
                float worldZ = cz * Chunk.SIZE + z;
                float height = provider.getHeight(worldX, worldZ);
                int groundY = Math.round(height); // 四舍五入取整

                // 只在区块 cy 对应的 y 范围内生成
                int localBaseY = cy * Chunk.SIZE;
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int worldY = localBaseY + y;
                    short blockId = 0;
                    if (worldY < groundY) {
                        blockId = 1; // 石头
                    } else if (worldY == groundY) {
                        blockId = 2; // 草地
                    } else if (worldY > groundY && worldY < seaLevel) {
                        blockId = 0; // 水（暂不实现）
                    }
                    chunk.setBlock(x, y, z, blockId);
                }
            }
        }
    }
}