package com.fish.arcaterra.level;

import com.fish.arcaterra.level.mesh.ChunkMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static final int SIZE = 16;
    private static final float[] FACE_QUAD = {
            0,0,0, 1,0,0, 1,1,0, 0,1,0
    };
    private static final int[] FACE_INDEX = {0,1,2, 0,2,3};

    private final int cx, cy, cz;
    private final World world;
    private final ChunkMesh mesh;
    private final short[] blocks = new short[SIZE * SIZE * SIZE];
    public boolean dirty = true;  // 初始为true，需要重建网格

    public Chunk(int cx, int cy, int cz, World world) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.world = world;
        this.mesh = new ChunkMesh();
        generateTerrain();
        // 不在这里调用 rebuildMesh()，由外部统一重建
    }

    private void generateTerrain() {
        int groundY = 8;
        for (int rx = 0; rx < SIZE; rx++) {
            for (int rz = 0; rz < SIZE; rz++) {
                for (int ry = 0; ry < SIZE; ry++) {
                    short id = 0;
                    int worldY = cy * SIZE + ry;
                    if (cy == 0 && ry <= groundY) id = 1;       // 石头
                    else if (cy == 0 && ry == groundY + 1) id = 2; // 草
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

    // 世界坐标版本（供邻居查询，直接调用 world.getBlockSafe 避免递归）
    public short getBlockWorld(int wx, int wy, int wz) {
        // 直接委托给 world 的安全查询，不会创建新区块
        return world.getBlockSafe(wx, wy, wz);
    }

    public void rebuildMesh() {
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

                    // 使用 world.getBlockSafe 避免创建新区块
                    if (world.getBlockSafe(wx - 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx + 1, wy, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy - 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy + 1, wz) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy, wz - 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (world.getBlockSafe(wx, wy, wz + 1) == 0) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                }
            }
        }
    }

    private void addFace(List<Float> v, List<Integer> i, int x, int y, int z, int offset) {
        for (int j = 0; j < FACE_QUAD.length; j += 3) {
            v.add(FACE_QUAD[j] + x);
            v.add(FACE_QUAD[j + 1] + y);
            v.add(FACE_QUAD[j + 2] + z);
        }
        for (int idx : FACE_INDEX) i.add(idx + offset);
    }

    public void render(float px, float py, float pz) {
        if (mesh.indexCount <= 0) return;
        glPushMatrix();
        glTranslatef(cx * SIZE, cy * SIZE, cz * SIZE);
        // 临时颜色：石头灰色，草绿色（仅演示，实际应使用纹理）
        // 这里因为每个区块可能混合方块，简单全灰
        glColor3f(0.5f, 0.5f, 0.5f);
        mesh.render();
        glColor3f(1f, 1f, 1f); // 重置
        glPopMatrix();
    }

    public void destroy() {
        mesh.destroy();
    }

    public int getCx() { return cx; }
    public int getCy() { return cy; }
    public int getCz() { return cz; }
}