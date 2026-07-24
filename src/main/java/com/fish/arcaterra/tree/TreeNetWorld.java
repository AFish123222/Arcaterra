package com.fish.arcaterra.tree;

import com.fish.arcaterra.terrarium.NoiseTerrainProvider;

public class TreeNetWorld {
    private TreeNetChunk root;

    public TreeNetWorld() {
        // 根节点路径为空
        this.root = new TreeNetChunk(new TreePath(0, 0),null,new NoiseTerrainProvider());
    }

    public void updatePlayerPath(TreePath playerPath) {
        root.markPlayerPath(playerPath);
    }

    public void render(float camX, float camY, float camZ) {
        root.render(camX, camY, camZ);
    }
}