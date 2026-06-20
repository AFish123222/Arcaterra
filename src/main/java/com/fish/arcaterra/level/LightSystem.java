package com.fish.arcaterra.level;

import java.util.HashMap;
import java.util.Map;

public class LightSystem {
    // 存储平面坐标最高透光Y
    private final Map<Long, Integer> lightDepths;
    private final int groundY;

    public LightSystem(int groundY) {
        this.groundY = groundY;
        lightDepths = new HashMap<>();
    }

    // 打包XZ平面坐标key，支持正负
    private long getLightKey(int x, int z) {
        long offset = 0x80000000L;
        return ((x + offset) & 0xFFFFFFFFL) | (((z + offset) & 0xFFFFFFFFL) << 32);
    }

    // 计算一片区域的透光高度
    public void calcLightDepths(int x0, int z0, int x1, int z1, World world) {
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                int maxY = groundY + 10;
                int lightY = maxY;
                while (lightY > 0) {
                    Chunk chunk = world.getChunk(x, lightY, z);
                    if (chunk.isSolid(x, lightY, z)) break;
                    lightY--;
                }
                long key = getLightKey(x, z);
                lightDepths.put(key, lightY);
            }
        }
    }

    // 获取该坐标亮度
    public float getBrightness(int x, int y, int z) {
        long key = getLightKey(x, z);
        int topLightY = lightDepths.getOrDefault(key, groundY);
        return y < topLightY ? 0.8f : 1.0f;
    }

    // 清空缓存（切换世界/重启调用）
    public void clear() {
        lightDepths.clear();
    }
}