package com.fish.mcclone.level.chunk;

import com.fish.mcclone.level.Chunk;
import com.fish.mcclone.level.Level;

// 未来你要写的 LOD 区块（八叉树）
public class LODChunk extends Chunk {
    public LODChunk(Level level, int x0, int y0, int z0, int size, int lodLevel) {
        super(level, x0, y0, z0, x0+size, y0+size, z0+size);
//        this.lodLevel = lodLevel;
//        this.children = new Chunk[8]; // 八叉树8个子节点
    }

    // 合并子区块，生成简化LOD网格
    public void buildLODMesh() {
        // 未来实现LOD逻辑
    }
}