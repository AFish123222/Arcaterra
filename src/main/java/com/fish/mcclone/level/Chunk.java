package com.fish.mcclone.level;

import com.fish.mcclone.Textures;
import com.fish.mcclone.phys.AABB;
import org.lwjgl.opengl.GL11;

public class Chunk {
    public AABB aabb;
    public final Level level;
    public final int x0, y0, z0, x1, y1, z1;
    private boolean dirty = true;
    private int displayList = -1; // 显示列表（性能核心）
    private static final int texture = Textures.loadTexture("/terrain.png", 9728);
    private static Tesselator t = new Tesselator();
    public static int rebuiltThisFrame = 0;
    public static int updates = 0;

    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);
        this.displayList = GL11.glGenLists(2); // 创建2个显示列表（layer0/1）
    }

    // 仅脏块时重建显示列表（只重建一次，不是每帧）
    private void rebuild(int layer) {
        if (rebuiltThisFrame >= 2) return;
        this.dirty = false;
        updates++;
        rebuiltThisFrame++;

        GL11.glNewList(displayList + layer, GL11.GL_COMPILE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        t.init();

        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++)
                    if (level.isTile(x, y, z)) {
                        int tex = (y == level.depth * 2 / 3) ? 0 : 1;
                        if (tex == 0) Tile.rock.render(t, level, layer, x, y, z);
                        else Tile.grass.render(t, level, layer, x, y, z);
                    }

        t.flush();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEndList();
    }

    public void render(int layer) {
        // 脏块才重建，否则直接调用显示列表（极速渲染）
        if (dirty) {
            rebuild(0);
            rebuild(1);
        }
        GL11.glCallList(displayList + layer);
    }

    public void setDirty() {
        this.dirty = true;
    }
}