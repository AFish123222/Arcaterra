package com.fish.arcaterra.tree;

public class LODConfig {
    // 当节点边长 > 64 时，使用三角网格
    public static final int TRIANGLE_THRESHOLD = 64;
    // 当节点边长 > 128 时，使用高度图网格
    public static final int HEIGHTMAP_THRESHOLD = 128;
    // 最大 LOD 级别
    public static final int MAX_LOD = 3;
}