package com.fish.arcaterra.level;

import java.util.HashMap;
import java.util.Map;

public class LightSystem {
    private final Map<Long, Integer> lightDepths;
    private final int groundY;

    public LightSystem(int groundY) {
        this.groundY = groundY;
        lightDepths = new HashMap<>();
    }

    private long getLightKey(int x, int z) {
        long offset = 0x80000000L;
        return ((x + offset) & 0xFFFFFFFFL) | (((z + offset) & 0xFFFFFFFFL) << 32);
    }

    public void calcLightDepths(int x0, int z0, int x1, int z1, World world) {
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                int maxY = groundY + 10;
                int lightY = maxY;
                while (lightY > 0) {
                    if (world.getBlockSafe(x, lightY, z) != 0) break;
                    lightY--;
                }
                lightDepths.put(getLightKey(x, z), lightY);
            }
        }
    }
    public float getBrightness(int x, int y, int z) {
        int top = lightDepths.getOrDefault(getLightKey(x, z), groundY);
        return y < top ? 0.8f : 1.0f;
    }

    public void clear() {
        lightDepths.clear();
    }
}