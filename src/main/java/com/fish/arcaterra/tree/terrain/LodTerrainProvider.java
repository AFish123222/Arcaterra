package com.fish.arcaterra.tree.terrain;

import com.fish.arcaterra.worldgen.TerrainProvider;

/**
 * 专为 LOD 系统设计的地形提供者。
 * 支持多分辨率体素数据生成和高度图查询。
 */
public interface LodTerrainProvider extends TerrainProvider {
    /**
     * 获取指定区域的高度图（用于 LOD 简化网格）。
     * @param worldX 世界 X 坐标（角点）
     * @param worldZ 世界 Z 坐标（角点）
     * @param resolution 采样分辨率（每边采样点数）
     * @return 高度图数组 [resolution * resolution]
     */
    float[] getHeightmap(float worldX, float worldZ, int resolution);

    /**
     * 生成一个叶子节点的体素数据（4×4×4）。
     * @param offsetX, offsetY, offsetZ 叶子节点的世界角点坐标
     * @return 体素数组 [LEAF_SIZE^3]
     */
    short[] generateVoxels(float offsetX, float offsetY, float offsetZ);

    /**
     * 判断指定位置是否可通行（用于碰撞检测）。
     */
    boolean isSolid(float worldX, float worldY, float worldZ);
}
