package com.fish.arcaterra.level;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import com.fish.arcaterra.terrarium.TerrainProvider;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static final int SIZE = 16;
    private final int cx, cy, cz;
    private final World world;
    private ChunkMesh mesh;
    private final short[] blocks = new short[SIZE * SIZE * SIZE];
    public boolean dirty = true;

    // ★ 调试开关：设为 true 则只画线框，false 则正常填充（默认）
    private static final boolean DEBUG_WIREFRAME_ONLY = false;

    public Chunk(int cx, int cy, int cz, World world) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.world = world;
        this.mesh = new ChunkMesh();
        generateTerrain();
    }

    private void generateTerrain() {
        TerrainProvider provider = world.getTerrainProvider();
        for (int rx = 0; rx < SIZE; rx++) {
            for (int rz = 0; rz < SIZE; rz++) {
                int worldX = cx * SIZE + rx;
                int worldZ = cz * SIZE + rz;
                float height = provider.getHeight(worldX, worldZ);
                int groundY = Math.round(height); // 四舍五入

                int localBaseY = cy * SIZE;
                for (int ry = 0; ry < SIZE; ry++) {
                    int worldY = localBaseY + ry;
                    short id = 0;
                    if (worldY < groundY) {
                        id = 1; // 石头
                    } else if (worldY == groundY) {
                        id = 1; // 也可以换为草方块，但这里统一石头
                    } else if (worldY == groundY + 1) {
                        id = 2; // 草地
                    }
                    setBlock(rx, ry, rz, id);
                }
            }
        }
    }

    public void setBlock(int rx, int ry, int rz, short id) {
        if (rx < 0 || rx >= SIZE || ry < 0 || ry >= SIZE || rz < 0 || rz >= SIZE) return;
        int index = rx + ry * SIZE + rz * SIZE * SIZE;
        blocks[index] = id;
        dirty = true;
    }

    public short getBlock(int rx, int ry, int rz) {
        if (rx < 0 || rx >= SIZE || ry < 0 || ry >= SIZE || rz < 0 || rz >= SIZE) return 0;
        int index = rx + ry * SIZE + rz * SIZE * SIZE;
        return blocks[index];
    }

    public void rebuildMesh() {
        // 释放旧网格（如果有）
        if (mesh != null) {
            mesh.destroy();
        }
        mesh = new ChunkMesh();
        // 然后构建顶点，上传...

        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        buildBlockFaces(vertices, indices);

        float[] vArr = new float[vertices.size()];
        int[] iArr = new int[indices.size()];
        for (int i = 0; i < vArr.length; i++) vArr[i] = vertices.get(i);
        for (int i = 0; i < iArr.length; i++) iArr[i] = indices.get(i);

        FloatBuffer vBuf = MemoryUtil.memAllocFloat(vArr.length);
        IntBuffer iBuf = MemoryUtil.memAllocInt(iArr.length);
        vBuf.put(vArr).flip();
        iBuf.put(iArr).flip();

        mesh.upload(vBuf, iBuf);
        MemoryUtil.memFree(vBuf);
        MemoryUtil.memFree(iBuf);
        dirty = false;
    }

    // 方向枚举
    private enum Direction {
        POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z
    }

    private void buildBlockFaces(List<Float> verts, List<Integer> indices) {
        int offset = 0;
        for (int rx = 0; rx < SIZE; rx++) {
            for (int ry = 0; ry < SIZE; ry++) {
                for (int rz = 0; rz < SIZE; rz++) {
                    short b = getBlock(rx, ry, rz);
                    if (b == 0) continue;
                    int wx = cx * SIZE + rx;
                    int wy = cy * SIZE + ry;
                    int wz = cz * SIZE + rz;

                    if (world.getBlockSafe(wx - 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_X, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx + 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_X, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy - 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_Y, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy + 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_Y, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy, wz - 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.NEG_Z, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy, wz + 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, Direction.POS_Z, offset);
                        offset += 4;
                    }
                }
            }
        }
    }

    private void addFace(List<Float> v, List<Integer> i, int x, int y, int z, Direction dir, int offset) {
        float[][] faceVerts;
        switch (dir) {
            case POS_X:
                faceVerts = new float[][]{{1,0,0}, {1,0,1}, {1,1,1}, {1,1,0}};
                break;
            case NEG_X:
                faceVerts = new float[][]{{0,0,1}, {0,0,0}, {0,1,0}, {0,1,1}};
                break;
            case POS_Y:
                faceVerts = new float[][]{{0,1,0}, {1,1,0}, {1,1,1}, {0,1,1}};
                break;
            case NEG_Y:
                faceVerts = new float[][]{{0,0,0}, {0,0,1}, {1,0,1}, {1,0,0}};
                break;
            case POS_Z:
                faceVerts = new float[][]{{0,0,1}, {1,0,1}, {1,1,1}, {0,1,1}};
                break;
            case NEG_Z:
                faceVerts = new float[][]{{1,0,0}, {0,0,0}, {0,1,0}, {1,1,0}};
                break;
            default: return;
        }
        for (float[] vert : faceVerts) {
            v.add(vert[0] + x);
            v.add(vert[1] + y);
            v.add(vert[2] + z);
        }
        i.add(offset);
        i.add(offset + 1);
        i.add(offset + 2);
        i.add(offset);
        i.add(offset + 2);
        i.add(offset + 3);
    }

    // ★★★ 渲染：支持填充 + 线框叠加（调试用）★★★
    public void render(float px, float py, float pz) {
        if (mesh.indexCount <= 0) return;

        glPushMatrix();
        glTranslatef(cx * SIZE, cy * SIZE, cz * SIZE);

        // ★ 临时禁用剔除，让所有面可见（调试用）
        glDisable(GL_CULL_FACE);

        // 填充颜色（灰色）
        glColor3f(0.7f, 0.7f, 0.7f);
        mesh.render();

        // 线框叠加（红色）
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glColor3f(1.0f, 0.0f, 0.0f);
        mesh.render();
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

        // 恢复剔除（正式版可保留此行）
        glEnable(GL_CULL_FACE);

        glPopMatrix();
    }

    public void destroy() { mesh.destroy(); }

    public int getCx() { return cx; }
    public int getCy() { return cy; }
    public int getCz() { return cz; }
}