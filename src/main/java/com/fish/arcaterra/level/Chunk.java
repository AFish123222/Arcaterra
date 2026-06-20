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

    private final int cx, cz;
    private final World world;
    private final ChunkMesh mesh;
    private final short[] blocks = new short[SIZE * SIZE * SIZE];
    // 补齐脏标记
    public boolean dirty = true;

    public Chunk(int cx, int cz, World world) {
        this.cx = cx;
        this.cz = cz;
        this.world = world;
        this.mesh = new ChunkMesh();
        generateTerrain();
        rebuildMesh();
    }

    private void generateTerrain() {
        int groundY = 8;
        for (int rx = 0; rx < SIZE; rx++) {
            for (int rz = 0; rz < SIZE; rz++) {
                for (int ry = 0; ry <= groundY; ry++) {
                    setBlock(rx, ry, rz, (short) 1);
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

    public boolean isAir(int rx, int ry, int rz) {
        return getBlock(rx, ry, rz) == 0;
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlock(x, y, z) != 0;
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

                    if (isAir(rx - 1, ry, rz)) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (isAir(rx + 1, ry, rz)) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (isAir(rx, ry - 1, rz)) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (isAir(rx, ry + 1, rz)) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (isAir(rx, ry, rz - 1)) {
                        addFace(verts, indices, rx, ry, rz, offset);
                        offset += 4;
                    }
                    if (isAir(rx, ry, rz + 1)) {
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
        System.out.println("区块 " + cx + "," + cz + " 索引数: " + mesh.indexCount);
        glPushMatrix();
        glTranslatef(cx * SIZE, 0, cz * SIZE);
        mesh.render();
        glPopMatrix();
    }

    // 补齐销毁显存方法
    public void destroy() {
        mesh.destroy();
    }

    public int getCx() {
        return cx;
    }

    public int getCz() {
        return cz;
    }
}