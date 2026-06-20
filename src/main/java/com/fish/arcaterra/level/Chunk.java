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
    }

    // 生成地面，y=60为地表
    private void generateTerrain() {
        int groundBase = world.groundY - y0;
        for(int rx = 0; rx < SIZE; rx++){
            for(int rz = 0; rz < SIZE; rz++){
                for(int ry = 0; ry <= groundBase; ry++){
                    setBlock(rx, ry, rz, (short)1);
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

        glPushMatrix();
        glTranslatef(x0, y0, z0);

        // 绘制所有方块可见面
        for (int rx = 0; rx < SIZE; rx++) {
            for (int ry = 0; ry < SIZE; ry++) {
                for (int rz = 0; rz < SIZE; rz++) {
                    short b = getBlock(rx, ry, rz);
                    if (b == 0) continue;

                    boolean left  = getBlock(rx-1, ry, rz) == 0;
                    boolean right = getBlock(rx+1, ry, rz) == 0;
                    boolean down  = getBlock(rx, ry-1, rz) == 0;
                    boolean up    = getBlock(rx, ry+1, rz) == 0;
                    boolean back  = getBlock(rx, ry, rz-1) == 0;
                    boolean front = getBlock(rx, ry, rz+1) == 0;

                    glBegin(GL_QUADS);
                    // 左
                    if(left){
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx, ry+1, rz);
                        glVertex3f(rx, ry+1, rz+1);
                        glVertex3f(rx, ry, rz+1);
                    }
                    // 右
                    if(right){
                        glVertex3f(rx+1, ry, rz);
                        glVertex3f(rx+1, ry+1, rz);
                        glVertex3f(rx+1, ry+1, rz+1);
                        glVertex3f(rx+1, ry, rz+1);
                    }
                    // 底
                    if(down){
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx+1, ry, rz);
                        glVertex3f(rx+1, ry, rz+1);
                        glVertex3f(rx, ry, rz+1);
                    }
                    // 顶
                    if(up){
                        glVertex3f(rx, ry+1, rz);
                        glVertex3f(rx+1, ry+1, rz);
                        glVertex3f(rx+1, ry+1, rz+1);
                        glVertex3f(rx, ry+1, rz+1);
                    }
                    // 后
                    if(back){
                        glVertex3f(rx, ry, rz);
                        glVertex3f(rx+1, ry, rz);
                        glVertex3f(rx+1, ry+1, rz);
                        glVertex3f(rx, ry+1, rz);
                    }
                    // 前
                    if(front){
                        glVertex3f(rx, ry, rz+1);
                        glVertex3f(rx+1, ry, rz+1);
                        glVertex3f(rx+1, ry+1, rz+1);
                        glVertex3f(rx, ry+1, rz+1);
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