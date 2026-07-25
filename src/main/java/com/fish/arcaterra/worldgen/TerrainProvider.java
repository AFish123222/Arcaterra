package com.fish.arcaterra.worldgen;

public interface TerrainProvider {
    float getHeight(float worldX, float worldZ);

    default float getMinX() { return Float.NEGATIVE_INFINITY; }
    default float getMaxX() { return Float.POSITIVE_INFINITY; }
    default float getMinZ() { return Float.NEGATIVE_INFINITY; }
    default float getMaxZ() { return Float.POSITIVE_INFINITY; }
}
