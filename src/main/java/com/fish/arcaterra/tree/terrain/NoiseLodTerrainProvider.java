package com.fish.arcaterra.tree.terrain;

import java.util.Random;

/**
 * 基于噪声的 LOD 地形提供者。
 * 使用确定性噪声，保证在任何分辨率下查询结果一致。
 */
public class NoiseLodTerrainProvider implements LodTerrainProvider {
    private final Random random = new Random(0);
    private final float scale = 0.05f;
    private final float amplitude = 20f;
    private final int groundY = 8;

    @Override
    public float getHeight(float worldX, float worldZ) {
        float vx = worldX * scale;
        float vz = worldZ * scale;
        float h = (float)(Math.sin(vx) * Math.cos(vz) +
                Math.sin(vx * 0.5f + 1.2f) * Math.cos(vz * 0.7f + 0.8f));
        return groundY + h * amplitude;
    }

    @Override
    public float[] getHeightmap(float worldX, float worldZ, int resolution) {
        float[] heights = new float[resolution * resolution];
        float step = 1.0f; // 每个采样点的世界步长（可调整）
        for (int x = 0; x < resolution; x++) {
            for (int z = 0; z < resolution; z++) {
                float wx = worldX + x * step;
                float wz = worldZ + z * step;
                heights[x + z * resolution] = getHeight(wx, wz);
            }
        }
        return heights;
    }

    @Override
    public short[] generateVoxels(float offsetX, float offsetY, float offsetZ) {
        int size = 4;
        short[] voxels = new short[size * size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float wx = offsetX + x + 0.5f;
                float wz = offsetZ + z + 0.5f;
                float height = getHeight(wx, wz);
                int groundY = Math.round(height);
                for (int y = 0; y < size; y++) {
                    int worldY = (int)(offsetY + y);
                    if (worldY < groundY) {
                        voxels[x + y * size + z * size * size] = 1; // 石头
                    } else if (worldY == groundY) {
                        voxels[x + y * size + z * size * size] = 1; // 地表
                    } else if (worldY == groundY + 1) {
                        voxels[x + y * size + z * size * size] = 2; // 草地
                    } else {
                        voxels[x + y * size + z * size * size] = 0; // 空气
                    }
                }
            }
        }
        return voxels;
    }

    @Override
    public boolean isSolid(float worldX, float worldY, float worldZ) {
        float height = getHeight(worldX, worldZ);
        return worldY < height;
    }
}
