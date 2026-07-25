package com.fish.arcaterra.worldgen.noise;

import com.fish.arcaterra.worldgen.TerrainProvider;

import java.util.Random;

public class NoiseTerrainProvider implements TerrainProvider {
    private final Random random = new Random(0);
    private final float scale = 0.05f; // 频率
    private final float amplitude = 20f; // 振幅

    @Override
    public float getHeight(float worldX, float worldZ) {
        // 简单的值噪声（可替换为 Perlin）
        float vx = worldX * scale;
        float vz = worldZ * scale;
        // 用两个正弦波模拟噪声
        float h = (float)(Math.sin(vx) * Math.cos(vz) + Math.sin(vx * 0.5f + 1.2f) * Math.cos(vz * 0.7f + 0.8f));
        return 8 + h * amplitude; // 基础高度 8
    }
}