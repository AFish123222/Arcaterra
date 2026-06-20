package com.fish.arcaterra.level;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import com.fish.arcaterra.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static final int SIZE = 16;
    public static final int LOD0_DIST_SQ = 256;
    public static final int LOD1_DIST_SQ = 262144;

    public final World world;
    public final int cx, cy, cz;
    public final int x0, y0, z0, x1, y1, z1;
    public final AABB aabb;

    private short[] blocks;
    public boolean dirty;
    private ChunkMesh lod0Mesh;

    public Chunk(World world, int cx, int cy, int cz) {
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;

        this.x0 = cx * SIZE;
        this.y0 = cy * SIZE;
        this.z0 = cz * SIZE;
        this.x1 = x0 + SIZE;
        this.y1 = y0 + SIZE;
        this.z1 = z0 + SIZE;
        this.aabb = new AABB(x0, y0, z0, x1, y1, z1);

        blocks = new short[SIZE * SIZE * SIZE];
        lod0Mesh = new ChunkMesh();
        dirty = true;

        generateTerrain();
        rebuildMesh();
    }

    private void generateTerrain() {
        int groundY = world.groundY;
        int base = groundY - y0;

        for (int rx = 0; rx < SIZE; rx++) {
            for (int rz = 0; rz < SIZE; rz++) {
                for (int ry = 0; ry <= base; ry++) {
                    setBlock(rx, ry, rz, (short) 1);
                }
            }
        }
    }

    public void setBlock(int rx, int ry, int rz, short id) {
        if (rx < 0 || rx >= SIZE || ry < 0 || ry >= SIZE || rz < 0 || rz >= SIZE) return;
        blocks[rx + ry * SIZE + rz * SIZE * SIZE] = id;
        dirty = true;
    }

    public short getBlock(int rx, int ry, int rz) {
        if (rx < 0 || rx >= SIZE || ry < 0 || ry >= SIZE || rz < 0 || rz >= SIZE) return 0;
        return blocks[rx + ry * SIZE + rz * SIZE * SIZE];
    }

    public void rebuildMesh() {
        dirty = false;
    }

    public void render(float px, float py, float pz) {
        float dx = (x0 + 8) - px;
        float dz = (z0 + 8) - pz;
        float distSq = dx * dx + dz * dz;
        if (distSq > 4096) return;

        glPushMatrix();
        glTranslatef(x0, y0, z0);

        for (int rx = 0; rx < SIZE; rx++) {
            for (int ry = 0; ry < SIZE; ry++) {
                for (int rz = 0; rz < SIZE; rz++) {
                    short block = getBlock(rx, ry, rz);
                    if (block == 0) continue;

                    boolean l = getBlock(rx - 1, ry, rz) == 0;
                    boolean r = getBlock(rx + 1, ry, rz) == 0;
                    boolean d = getBlock(rx, ry - 1, rz) == 0;
                    boolean u = getBlock(rx, ry + 1, rz) == 0;
                    boolean b = getBlock(rx, ry, rz - 1) == 0;
                    boolean f = getBlock(rx, ry, rz + 1) == 0;

                    glBegin(GL_QUADS);
                    if (l) {
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx, ry + 1, rz);
                        glVertex3f(rx, ry + 1, rz + 1);
                        glVertex3f(rx, ry, rz + 1);
                    }
                    if (r) {
                        glVertex3f(rx + 1, ry, rz);
                        glVertex3f(rx + 1, ry + 1, rz);
                        glVertex3f(rx + 1, ry + 1, rz + 1);
                        glVertex3f(rx + 1, ry, rz + 1);
                    }
                    if (d) {
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx + 1, ry, rz);
                        glVertex3f(rx + 1, ry, rz + 1);
                        glVertex3f(rx, ry, rz + 1);
                    }
                    if (u) {
                        glVertex3f(rx, ry + 1, rz);
                        glVertex3f(rx + 1, ry + 1, rz);
                        glVertex3f(rx + 1, ry + 1, rz + 1);
                        glVertex3f(rx, ry + 1, rz + 1);
                    }
                    if (b) {
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx + 1, ry, rz);
                        glVertex3f(rx + 1, ry + 1, rz);
                        glVertex3f(rx, ry + 1, rz);
                    }
                    if (f) {
                        glVertex3f(rx, ry, rz + 1);
                        glVertex3f(rx + 1, ry, rz + 1);
                        glVertex3f(rx + 1, ry + 1, rz + 1);
                        glVertex3f(rx, ry + 1, rz + 1);
                    }
                    glEnd();
                }
            }
        }

        glPopMatrix();
    }

    public boolean isSolid(int x, int y, int z) {
        int rx = x - x0;
        int ry = y - y0;
        int rz = z - z0;
        return getBlock(rx, ry, rz) != 0;
    }

    public void destroy() {}
}