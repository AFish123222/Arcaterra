package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunk {
    public static int texture = 0;

    // ====================== 渲染距离 / LOD 阈值 ======================
    public static final int LOD0_DIST = 16;
    public static final int LOD1_DIST = 512;
    public static final int LOD2_DIST = 512;
    public static final int LOD0_DIST_SQ = LOD0_DIST * LOD0_DIST;
    public static final int LOD1_DIST_SQ = LOD1_DIST * LOD1_DIST;
    public static final int LOD2_DIST_SQ = LOD2_DIST * LOD2_DIST;
    // LOD1 掩码 + 合并矩形缓存
    private boolean[][] lodOccupiedMask;
    private List<Rect> lodRectList;

    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    private static final int BASE_SIZE = 16;

    private final short[] blocks;
    private final int groundLevel;
    public final AABB aabb;

    // 缓存每个方块的遮挡面 (rx,ry,rz) → 6个面是否可见
    private final boolean[][][] faceVisible;

    private static class Rect {
        int x, z;
        int width;
        int height;
        Rect(int x, int z, int w, int h) {
            this.x = x;
            this.z = z;
            this.width = w;
            this.height = h;
        }
    }

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
        // 初始化遮挡缓存：6个面
        faceVisible = new boolean[BASE_SIZE][BASE_SIZE][BASE_SIZE * 6];
        // 原有代码不变，追加：
        lodOccupiedMask = new boolean[BASE_SIZE][BASE_SIZE];
        lodRectList = new ArrayList<>();

        initTerrain();
        // 首次生成后计算遮挡面
        recalcFaceVisible();
    }

    // 仅区块变脏时调用，不每帧执行
    private void recalcFaceVisible() {
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int ry = 0; ry < BASE_SIZE; ry++) {
                for (int rz = 0; rz < BASE_SIZE; rz++) {
                    if (getBlockLocal(rx, ry, rz) == 0) continue;

                    // 逐个面判断是否被遮挡
                    faceVisible[rx][ry][rz*6 + 0] = getBlockLocal(rx-1, ry, rz) == 0; // left
                    faceVisible[rx][ry][rz*6 + 1] = getBlockLocal(rx+1, ry, rz) == 0; // right
                    faceVisible[rx][ry][rz*6 + 2] = getBlockLocal(rx, ry-1, rz) == 0; // bottom
                    faceVisible[rx][ry][rz*6 + 3] = getBlockLocal(rx, ry+1, rz) == 0; // top
                    faceVisible[rx][ry][rz*6 + 4] = getBlockLocal(rx, ry, rz-1) == 0; // back
                    faceVisible[rx][ry][rz*6 + 5] = getBlockLocal(rx, ry, rz+1) == 0; // front
                }
            }
        }
    }

    private void initTerrain() {
        int baseRy = groundLevel - y0;
        if (baseRy < 0 || baseRy >= BASE_SIZE) return;

        int topHeight = 0;
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                topHeight = getTopHeight();
                for (int ry = 0; ry < topHeight; ry++) {
                    setBlockLocal(rx,ry, rz, Block.GRASS);
                }
            }
        }
    }

    private int getTopHeight() {
        return 64;
    }

    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if ((rx | ry | rz) < 0 || (rx | ry | rz) >= BASE_SIZE) return;
        blocks[(ry << 8) | (rz << 4) | rx] = (short) id;
        setDirty();
    }

    public int getBlockLocal(int rx, int ry, int rz) {
        if ((rx | ry | rz) < 0 || (rx | ry | rz) >= BASE_SIZE) return 0;
        return blocks[(ry << 8) | (rz << 4) | rx] & 0xFF;
    }

    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x - x0, y - y0, z - z0);
    }

    public void setDirty() {
        recalcFaceVisible();
        // 刷新LOD缓存
        buildOccupiedMask();
//        mergeToRectangles(lodOccupiedMask);
    }

    private boolean[][] buildOccupiedMask() {
        // ✅ 修复：双层循环清空二维boolean数组（删除错误的Arrays.fill）
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                lodOccupiedMask[rx][rz] = false;
            }
        }
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                for (int ry = 0; ry < BASE_SIZE; ry++) {
                    if (getBlockLocal(rx, ry, rz) != 0) {
                        lodOccupiedMask[rx][rz] = true;
                        break;
                    }
                }
            }
        }
        return lodOccupiedMask;
    }

    private List<Rect> mergeToRectangles(boolean[][] mask) {
        List<Rect> rectList = new ArrayList<>();
        boolean[][] used = new boolean[BASE_SIZE][BASE_SIZE];

        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                if (mask[rx][rz] && !used[rx][rz]) {
                    int w = 1;
                    while (rx + w < BASE_SIZE && mask[rx + w][rz] && !used[rx + w][rz]) {
                        w++;
                    }
                    int h = 1;
                    boolean canExpand;
                    do {
                        canExpand = true;
                        for (int i = 0; i < w; i++) {
                            int currRx = rx + i;
                            int currRz = rz + h;
                            if (currRz >= BASE_SIZE || !mask[currRx][currRz] || used[currRx][currRz]) {
                                canExpand = false;
                                break;
                            }
                        }
                        if (canExpand) h++;
                    } while (canExpand);

                    for (int i = 0; i < w; i++) {
                        for (int j = 0; j < h; j++) {
                            used[rx + i][rz + j] = true;
                        }
                    }
                    rectList.add(new Rect(rx, rz, w, h));
                }
            }
        }
        return rectList;
    }

    private void drawMergedRect(Rect rect, int baseY) {
        float xStart = x0 + rect.x;
        float zStart = z0 + rect.z;
        float xEnd = xStart + rect.width;
        float zEnd = zStart + rect.height;
        float yWorld = y0 + baseY;

        glVertex3f(xStart, yWorld, zStart);
        glVertex3f(xEnd,   yWorld, zStart);
        glVertex3f(xEnd,   yWorld, zStart);
        glVertex3f(xEnd,   yWorld, zEnd);
        glVertex3f(xEnd,   yWorld, zEnd);
        glVertex3f(xStart, yWorld, zEnd);
        glVertex3f(xStart, yWorld, zEnd);
        glVertex3f(xStart, yWorld, zStart);
    }

    // ====================== 【核心修复】渲染逻辑 ======================
    public void render(int layer, float playerX, float playerY, float playerZ) {
        float cx = x0 + 8;
        float cz = z0 + 8;
        float dx = playerX - cx;
        float dz = playerZ - cz;
        float distSq = dx * dx + dz * dz;

        if (distSq > LOD2_DIST_SQ) return;

        // 🔥 修复1：仅保留1次状态压栈，删除所有嵌套栈（根治矩阵错乱）
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_LINE_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(1.0f);
        glColor3f(0, 0, 0);

        // 🔥 修复2：删除方法内非法枚举！Java不允许在函数里定义enum
        // 固定开启三角面渲染
        boolean enableTriangle = true;

        // LOD0 近距离渲染
        if (distSq <= LOD0_DIST_SQ) {
            // 线框边框
            glBegin(GL_LINES);
            glColor3i( 0,0,0);
            for (int rx = 0; rx < BASE_SIZE; rx++) {
                for (int ry = 0; ry < BASE_SIZE; ry++) {
                    for (int rz = 0; rz < BASE_SIZE; rz++) {
                        int block = getBlockLocal(rx, ry, rz);
                        if (block == 0) continue;

                        float x = x0 + rx;
                        float y = y0 + ry;
                        float z = z0 + rz;

                        int idx = rz * 6;
                        boolean left   = faceVisible[rx][ry][idx + 0];
                        boolean right  = faceVisible[rx][ry][idx + 1];
                        boolean bottom = faceVisible[rx][ry][idx + 2];
                        boolean top    = faceVisible[rx][ry][idx + 3];
                        boolean back   = faceVisible[rx][ry][idx + 4];
                        boolean front  = faceVisible[rx][ry][idx + 5];

                        if (left) {glVertex3f(x, y, z);glVertex3f(x, y +1, z);glVertex3f(x, y +1, z);glVertex3f(x, y +1, z +1);glVertex3f(x, y +1, z +1);glVertex3f(x, y, z +1);glVertex3f(x, y, z +1);glVertex3f(x, y, z);}
                        if (right) {glVertex3f(x +1, y, z);glVertex3f(x +1, y +1, z);glVertex3f(x +1, y +1, z);glVertex3f(x +1, y +1, z +1);glVertex3f(x +1, y +1, z +1);glVertex3f(x +1, y, z +1);glVertex3f(x +1, y, z +1);glVertex3f(x +1, y, z);}
                        if (bottom) {glVertex3f(x, y, z);glVertex3f(x +1, y, z);glVertex3f(x +1, y, z);glVertex3f(x +1, y, z +1);glVertex3f(x +1, y, z +1);glVertex3f(x, y, z +1);glVertex3f(x, y, z +1);glVertex3f(x, y, z);}
                        if (top) {glVertex3f(x, y +1, z);glVertex3f(x +1, y +1, z);glVertex3f(x +1, y +1, z);glVertex3f(x +1, y +1, z +1);glVertex3f(x +1, y +1, z +1);glVertex3f(x, y +1, z +1);glVertex3f(x, y +1, z +1);glVertex3f(x, y +1, z);}
                        if (back) {glVertex3f(x, y, z);glVertex3f(x +1, y, z);glVertex3f(x +1, y, z);glVertex3f(x +1, y +1, z);glVertex3f(x +1, y +1, z);glVertex3f(x, y +1, z);glVertex3f(x, y +1, z);glVertex3f(x, y, z);}
                        if (front) {glVertex3f(x, y, z +1);glVertex3f(x +1, y, z +1);glVertex3f(x +1, y, z +1);glVertex3f(x +1, y +1, z +1);glVertex3f(x +1, y +1, z +1);glVertex3f(x, y +1, z +1);glVertex3f(x, y +1, z +1);glVertex3f(x, y, z +1);}
                    }
                }
            }
            glEnd();

            // 三角面填充（🔥 修复3：删除嵌套的glPushAttrib！）
            if (enableTriangle) {
                glColor3f(0.4f,0.65f,0.3f);
                glBegin(GL_TRIANGLES);
                for (int rx = 0; rx < BASE_SIZE; rx++) {
                    for (int ry = 0; ry < BASE_SIZE; ry++) {
                        for (int rz = 0; rz < BASE_SIZE; rz++) {
                            int block = getBlockLocal(rx, ry, rz);
                            if (block == 0) continue;

                            float x = x0 + rx;
                            float y = y0 + ry;
                            float z = z0 + rz;

                            int idx = rz * 6;
                            boolean left   = faceVisible[rx][ry][idx + 0];
                            boolean right  = faceVisible[rx][ry][idx + 1];
                            boolean bottom = faceVisible[rx][ry][idx + 2];
                            boolean top    = faceVisible[rx][ry][idx + 3];
                            boolean back   = faceVisible[rx][ry][idx + 4];
                            boolean front  = faceVisible[rx][ry][idx + 5];

                            float u0 = 0, u1 = 1;
                            float v0 = 0, v1 = 1;

                            if (left) {
                                glTexCoord2f(u0, v1); glVertex3f(x, y  , z  );
                                glTexCoord2f(u0, v0); glVertex3f(x, y+1, z  );
                                glTexCoord2f(u1, v0); glVertex3f(x, y+1, z+1);
                                glTexCoord2f(u0, v1); glVertex3f(x, y  , z  );
                                glTexCoord2f(u1, v0); glVertex3f(x, y+1, z+1);
                                glTexCoord2f(u1, v1); glVertex3f(x, y  , z+1);
                            }
                            if (right) {
                                glTexCoord2f(u0, v1); glVertex3f(x+1, y  , z  );
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                glTexCoord2f(u0, v0); glVertex3f(x+1, y+1, z  );
                                glTexCoord2f(u0, v1); glVertex3f(x+1, y  , z  );
                                glTexCoord2f(u1, v1); glVertex3f(x+1, y  , z+1);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                            }
                            if (bottom) {
                                glTexCoord2f(u0, v1); glVertex3f(x  , y, z  );
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y, z+1);
                                glTexCoord2f(u0, v0); glVertex3f(x+1, y, z  );
                                glTexCoord2f(u0, v1); glVertex3f(x  , y, z  );
                                glTexCoord2f(u1, v1); glVertex3f(x  , y, z+1);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y, z+1);
                            }
                            if (top) {
                                glTexCoord2f(u0, v1); glVertex3f(x  , y+1, z  );
                                glTexCoord2f(u0, v0); glVertex3f(x  , y+1, z+1);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                glTexCoord2f(u0, v1); glVertex3f(x  , y+1, z  );
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                glTexCoord2f(u1, v1); glVertex3f(x+1, y+1, z  );
                            }
                            if (back) {
                                glTexCoord2f(u0, v1); glVertex3f(x  , y  , z);
                                glTexCoord2f(u0, v0); glVertex3f(x  , y+1, z);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z);
                                glTexCoord2f(u0, v1); glVertex3f(x  , y  , z);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z);
                                glTexCoord2f(u1, v1); glVertex3f(x+1, y  , z);
                            }
                            if (front) {
                                glTexCoord2f(u0, v1); glVertex3f(x  , y  , z+1);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                glTexCoord2f(u0, v0); glVertex3f(x  , y+1, z+1);
                                glTexCoord2f(u0, v1); glVertex3f(x  , y  , z+1);
                                glTexCoord2f(u1, v1); glVertex3f(x+1, y  , z+1);
                                glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                            }
                        }
                    }
                }
                glEnd();
            }
        }
        // LOD1 贪心合并轮廓
        // LOD1 中距离：直接使用缓存结果，不再每帧计算
        else if (distSq <= LOD1_DIST_SQ) {
            glBegin(GL_LINES);
            int baseY = groundLevel - y0;
//            for (Rect rect : lodRectList) {
//                drawMergedRect(rect, baseY);
//            }
            glEnd();
        }

        // 玩家区块红框
        if (isPlayerInChunk(playerX, playerY, playerZ)) {
            renderChunkRedBorder();
        }

        // 🔥 修复4：唯一一次出栈，栈完美平衡
        glPopAttrib();
    }

    private boolean isPlayerInChunk(float px, float py, float pz) {
        return px >= x0 && px < x1 && py >= y0 && py < y1 && pz >= z0 && pz < z1;
    }

    private void renderChunkRedBorder() {
        glLineWidth(3.0f);
        glColor3f(1, 0, 0);
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
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlockWorld(x, y, z) != 0;
    }

    public void render(int layer) {
        render(layer, 0, 0, 0);
    }
}