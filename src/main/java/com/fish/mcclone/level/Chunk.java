package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static int texture = 0;
    // ========== 渲染半径配置 ==========
    public static final int RENDER_RADIUS = 32;
    public static final int RENDER_RADIUS_SQ = RENDER_RADIUS * RENDER_RADIUS;

    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    private static final int BASE_SIZE = 16;

    private final short[] blocks;
    private final int groundLevel;
    public final AABB aabb;

    // ====================== LOD 预留字段 ======================
    protected int lodLevel = 0;
    protected Object lodMesh;
    protected boolean lodDirty = true;
    protected Chunk parent;
    protected Chunk[] children;

    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);
        this.groundLevel = level.groundY;

        this.blocks = new short[BASE_SIZE * BASE_SIZE * BASE_SIZE];
        initTerrain();
    }

    private void initTerrain() {
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                int worldY = groundLevel;
                int ry = worldY - y0;
                if (ry >= 0 && ry < BASE_SIZE) {
                    setBlockLocal(rx, ry, rz, Block.GRASS);
                }
            }
        }
    }

    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if (rx < 0 || ry < 0 || rz < 0 || rx >= BASE_SIZE || ry >= BASE_SIZE || rz >= BASE_SIZE) return;
        blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] = (short) id;
        lodDirty = true;
    }

    public int getBlockLocal(int rx, int ry, int rz) {
        if (rx < 0 || ry < 0 || rz < 0 || rx >= BASE_SIZE || ry >= BASE_SIZE || rz >= BASE_SIZE) return 0;
        return blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] & 0xFF;
    }

    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x - x0, y - y0, z - z0);
    }

    public void render(int layer, float playerX, float playerY, float playerZ) {
        // ========== 距离裁剪：超出渲染半径直接不渲染 ==========
        float chunkCenterX = (x0 + x1) * 0.5f;
        float chunkCenterZ = (z0 + z1) * 0.5f;
        float dx = chunkCenterX - playerX;
        float dz = chunkCenterZ - playerZ;
        if (dx * dx + dz * dz > RENDER_RADIUS_SQ) {
            return;
        }

        Tesselator t = Tesselator.getInstance();
        t.init();

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    int id = getBlockWorld(x, y, z);
                    if (id == 0) continue;

                    boolean exposed = !isSolid(x+1,y,z) || !isSolid(x-1,y,z) ||
                            !isSolid(x,y+1,z) || !isSolid(x,y-1,z) ||
                            !isSolid(x,y,z+1) || !isSolid(x,y,z-1);
                    if (!exposed) continue;

                    if (y < groundLevel) {
                        Tile.rock.render(t, level, layer, x, y, z);
                    } else {
                        Tile.grass.render(t, level, layer, x, y, z);
                    }
                }
            }
        }
        t.flush();

        // 渲染所有方块细黑框
        renderAllBlockWireframe();

        // 玩家所在区块粗红框
        if (isPlayerInChunk(playerX, playerY, playerZ)) {
            renderChunkRedBorder();
        }
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlockWorld(x, y, z) != 0;
    }

    private boolean isPlayerInChunk(float px, float py, float pz) {
        return px >= x0 && px < x1 &&
                py >= y0 && py < y1 &&
                pz >= z0 && pz < z1;
    }

    private void renderAllBlockWireframe() {
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(1.0f);
        glColor3f(0.0f, 0.0f, 0.0f);

        glBegin(GL_LINES);
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (getBlockWorld(x, y, z) == 0) continue;
                    drawCube(x, y, z);
                }
            }
        }
        glEnd();
        glPopAttrib();
    }

    private void drawCube(int x, int y, int z) {
        float x1 = x + 1, y1 = y + 1, z1 = z + 1;
        glVertex3f(x,y,z); glVertex3f(x1,y,z);
        glVertex3f(x1,y,z); glVertex3f(x1,y,z1);
        glVertex3f(x1,y,z1); glVertex3f(x,y,z1);
        glVertex3f(x,y,z1); glVertex3f(x,y,z);

        glVertex3f(x,y1,z); glVertex3f(x1,y1,z);
        glVertex3f(x1,y1,z); glVertex3f(x1,y1,z1);
        glVertex3f(x1,y1,z1); glVertex3f(x,y1,z1);
        glVertex3f(x,y1,z1); glVertex3f(x,y1,z);

        glVertex3f(x,y,z); glVertex3f(x,y1,z);
        glVertex3f(x1,y,z); glVertex3f(x1,y1,z);
        glVertex3f(x1,y,z1); glVertex3f(x1,y1,z1);
        glVertex3f(x,y,z1); glVertex3f(x,y1,z1);
    }

    private void renderChunkRedBorder() {
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(3.0f);
        glColor3f(1.0f, 0.0f, 0.0f);

        glBegin(GL_LINES);
        glVertex3f(x0,y0,z0); glVertex3f(x1,y0,z0);
        glVertex3f(x1,y0,z0); glVertex3f(x1,y0,z1);
        glVertex3f(x1,y0,z1); glVertex3f(x0,y0,z1);
        glVertex3f(x0,y0,z1); glVertex3f(x0,y0,z0);

        glVertex3f(x0,y1,z0); glVertex3f(x1,y1,z0);
        glVertex3f(x1,y1,z0); glVertex3f(x1,y1,z1);
        glVertex3f(x1,y1,z1); glVertex3f(x0,y1,z1);
        glVertex3f(x0,y1,z1); glVertex3f(x0,y1,z0);

        glVertex3f(x0,y0,z0); glVertex3f(x0,y1,z0);
        glVertex3f(x1,y0,z0); glVertex3f(x1,y1,z0);
        glVertex3f(x1,y0,z1); glVertex3f(x1,y1,z1);
        glVertex3f(x0,y0,z1); glVertex3f(x0,y1,z1);
        glEnd();
        glPopAttrib();
    }

    public void render(int layer) { render(layer, 0, 0, 0); }
    public void setDirty() { lodDirty = true; }
}