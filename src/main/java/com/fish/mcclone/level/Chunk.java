package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;
import java.util.ArrayList;
import java.util.List;

public class Chunk {
    public static int texture = 0;

    // ====================== 渲染距离 / LOD 阈值（可自行调整） ======================
    public static final int LOD0_DIST = 16;     // 近距离：完整单方块渲染
    public static final int LOD1_DIST = 512;     // 中距离：合并大方块渲染（本次优化目标）
    public static final int LOD2_DIST = 512;     // 远距离：仅区块边框
    public static final int LOD0_DIST_SQ = LOD0_DIST * LOD0_DIST;
    public static final int LOD1_DIST_SQ = LOD1_DIST * LOD1_DIST;
    public static final int LOD2_DIST_SQ = LOD2_DIST * LOD2_DIST;

    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    private static final int BASE_SIZE = 16;

    private final short[] blocks;
    private final int groundLevel;
    public final AABB aabb;

    // 矩形实体：用于LOD1贪心合并（本地rx/rz坐标）
    private static class Rect {
        int x, z;       // 矩形起点
        int width;      // X方向宽度
        int height;     // Z方向高度
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
        initTerrain();
    }

    /// 地形生成
    private void initTerrain() {
        int baseRy = groundLevel - y0;
        if (baseRy < 0 || baseRy >= BASE_SIZE) return;

        // rx,ry,rz均只是区块内坐标
        int topHeight = 0;
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
//                int h1 = rx + rz;
                //START get height
//                topHeight = Math.max(baseRy, h1);
                topHeight = getTopHeight();
                //END get height
                for (int ry = 0; ry < topHeight; ry++) {
                    setBlockLocal(rx,ry, rz, Block.GRASS);
                }
            }
        }
    }

    // 读取高度图某点
    private int getTopHeight() {
        int[] heightMap;
        // todo
        int topHeight = 64;
        return topHeight;
    }

    // 方块写入（本地坐标）
    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if ((rx | ry | rz) < 0 || (rx | ry | rz) >= BASE_SIZE) return;
        blocks[(ry << 8) | (rz << 4) | rx] = (short) id;
        setDirty();
    }

    // 方块读取（本地坐标）
    public int getBlockLocal(int rx, int ry, int rz) {
        if ((rx | ry | rz) < 0 || (rx | ry | rz) >= BASE_SIZE) return 0;
        return blocks[(ry << 8) | (rz << 4) | rx] & 0xFF;
    }

    // 方块读取（世界坐标）
    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x - x0, y - y0, z - z0);
    }

    // 要求的空方法
    public void setDirty() {}

    // ====================== 基础方块面绘制（LOD0 使用） ======================

    // ====================== LOD1 专用：贪心合并辅助方法 ======================
    /**
     * 构建XZ平面占用掩码：标记每个(rx,rz)列是否存在实心方块
     */
    private boolean[][] buildOccupiedMask() {
        boolean[][] mask = new boolean[BASE_SIZE][BASE_SIZE];
        // 遍历所有Y层，只要该列有方块即标记为占用
        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                for (int ry = 0; ry < BASE_SIZE; ry++) {
                    if (getBlockLocal(rx, ry, rz) != 0) {
                        mask[rx][rz] = true;
                        break;
                    }
                }
            }
        }
        return mask;
    }

    /**
     * 贪心算法：将掩码合并为连续矩形（核心合并逻辑）
     */
    private List<Rect> mergeToRectangles(boolean[][] mask) {
        List<Rect> rectList = new ArrayList<>();
        boolean[][] used = new boolean[BASE_SIZE][BASE_SIZE];

        for (int rx = 0; rx < BASE_SIZE; rx++) {
            for (int rz = 0; rz < BASE_SIZE; rz++) {
                // 找到未使用的实心格子，开始合并
                if (mask[rx][rz] && !used[rx][rz]) {
                    int w = 1;
                    // 向右扩展宽度
                    while (rx + w < BASE_SIZE && mask[rx + w][rz] && !used[rx + w][rz]) {
                        w++;
                    }
                    int h = 1;
                    boolean canExpand;
                    // 向下扩展高度
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

                    // 标记已合并区域
                    for (int i = 0; i < w; i++) {
                        for (int j = 0; j < h; j++) {
                            used[rx + i][rz + j] = true;
                        }
                    }
                    // 加入矩形列表
                    rectList.add(new Rect(rx, rz, w, h));
                }
            }
        }
        return rectList;
    }

    /**
     * 绘制合并后的大矩形外框（LOD1 专用，仅画轮廓、无内部线条）
     */
    private void drawMergedRect(Rect rect, int baseY) {
        // 本地坐标转世界坐标
        float xStart = x0 + rect.x;
        float zStart = z0 + rect.z;
        float xEnd = xStart + rect.width;
        float zEnd = zStart + rect.height;
        float yWorld = y0 + baseY;

        // 绘制矩形顶面外框（只画最上层轮廓）
        glVertex3f(xStart, yWorld, zStart);
        glVertex3f(xEnd,   yWorld, zStart);

        glVertex3f(xEnd,   yWorld, zStart);
        glVertex3f(xEnd,   yWorld, zEnd);

        glVertex3f(xEnd,   yWorld, zEnd);
        glVertex3f(xStart, yWorld, zEnd);

        glVertex3f(xStart, yWorld, zEnd);
        glVertex3f(xStart, yWorld, zStart);
    }

    // ====================== 主渲染逻辑 ======================
    public void render(int layer, float playerX, float playerY, float playerZ) {
        // 计算区块中心到玩家的水平距离平方
        float cx = x0 + 8;
        float cz = z0 + 8;
        float dx = playerX - cx;
        float dz = playerZ - cz;
        float distSq = dx * dx + dz * dz;

        // LOD3：超远距离，直接跳过渲染
        if (distSq > LOD2_DIST_SQ) return;

        // 统一全局GL状态（一次设置，全程复用）
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(1.0f);
        glColor3f(0, 0, 0);

        enum State {
            LINE,TRIANGLE
        }
        State state = State.TRIANGLE;
        // LOD0 近距离：完整单方块渲染 + 可见面剔除（高细节）
        if (distSq <= LOD0_DIST_SQ) {

            // 线框模式
            if (true) {
                glBegin(GL_LINES);
                for (int rx = 0; rx < BASE_SIZE; rx++) {
                    for (int ry = 0; ry < BASE_SIZE; ry++) {
                        for (int rz = 0; rz < BASE_SIZE; rz++) {
                            int block = getBlockLocal(rx, ry, rz);
                            if (block == 0) continue;

                            float x = x0 + rx;
                            float y = y0 + ry;
                            float z = z0 + rz;

                            // 相邻方块遮挡剔除
                            boolean left   = getBlockLocal(rx-1, ry, rz) == 0;
                            boolean right  = getBlockLocal(rx+1, ry, rz) == 0;
                            boolean bottom = getBlockLocal(rx, ry-1, rz) == 0;
                            boolean top    = getBlockLocal(rx, ry+1, rz) == 0;
                            boolean back   = getBlockLocal(rx, ry, rz-1) == 0;
                            boolean front  = getBlockLocal(rx, ry, rz+1) == 0;

                            // 推顶点绘制
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
                // 三角面模式 //线框直接当边框
                if (state == State.TRIANGLE) {
                    // 统一全局GL状态（一次设置，全程复用）
                    glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT);
                    glDisable(GL_TEXTURE_2D);
                    glDisable(GL_LIGHTING);
                    glLineWidth(1f);
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

                                // 遮挡剔除
                                boolean left   = getBlockLocal(rx-1, ry, rz) == 0;
                                boolean right  = getBlockLocal(rx+1, ry, rz) == 0;
                                boolean bottom = getBlockLocal(rx, ry-1, rz) == 0;
                                boolean top    = getBlockLocal(rx, ry+1, rz) == 0;
                                boolean back   = getBlockLocal(rx, ry, rz-1) == 0;
                                boolean front  = getBlockLocal(rx, ry, rz+1) == 0;

                                float u0 = 0, u1 = 1;
                                float v0 = 0, v1 = 1;

                                // 左面 X-
                                if (left) {
                                    glTexCoord2f(u0, v1); glVertex3f(x, y  , z  );
                                    glTexCoord2f(u0, v0); glVertex3f(x, y+1, z  );
                                    glTexCoord2f(u1, v0); glVertex3f(x, y+1, z+1);

                                    glTexCoord2f(u0, v1); glVertex3f(x, y  , z  );
                                    glTexCoord2f(u1, v0); glVertex3f(x, y+1, z+1);
                                    glTexCoord2f(u1, v1); glVertex3f(x, y  , z+1);
                                }
                                // 右面 X+
                                if (right) {
                                    glTexCoord2f(u0, v1); glVertex3f(x+1, y  , z  );
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                    glTexCoord2f(u0, v0); glVertex3f(x+1, y+1, z  );

                                    glTexCoord2f(u0, v1); glVertex3f(x+1, y  , z  );
                                    glTexCoord2f(u1, v1); glVertex3f(x+1, y  , z+1);
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                }
                                // 下面 Y-
                                if (bottom) {
                                    glTexCoord2f(u0, v1); glVertex3f(x  , y, z  );
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y, z+1);
                                    glTexCoord2f(u0, v0); glVertex3f(x+1, y, z  );

                                    glTexCoord2f(u0, v1); glVertex3f(x  , y, z  );
                                    glTexCoord2f(u1, v1); glVertex3f(x  , y, z+1);
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y, z+1);
                                }
                                // 上面 Y+
                                if (top) {
                                    glTexCoord2f(u0, v1); glVertex3f(x  , y+1, z  );
                                    glTexCoord2f(u0, v0); glVertex3f(x  , y+1, z+1);
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);

                                    glTexCoord2f(u0, v1); glVertex3f(x  , y+1, z  );
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z+1);
                                    glTexCoord2f(u1, v1); glVertex3f(x+1, y+1, z  );
                                }
                                // 后面 Z-
                                if (back) {
                                    glTexCoord2f(u0, v1); glVertex3f(x  , y  , z);
                                    glTexCoord2f(u0, v0); glVertex3f(x  , y+1, z);
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z);

                                    glTexCoord2f(u0, v1); glVertex3f(x  , y  , z);
                                    glTexCoord2f(u1, v0); glVertex3f(x+1, y+1, z);
                                    glTexCoord2f(u1, v1); glVertex3f(x+1, y  , z);
                                }
                                // 前面 Z+
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
                    glDisable(GL_TEXTURE_2D);
                }
            }

        }
        // LOD1 中距离：【重点】贪心合并大方块，仅绘制外轮廓
        else if (distSq <= LOD1_DIST_SQ) {
            // 1. 生成占用掩码
            boolean[][] occupied = buildOccupiedMask();
            // 2. 贪心合并为大矩形
            List<Rect> rectList = mergeToRectangles(occupied);

            glBegin(GL_LINES);
            // 以地面高度为基准绘制合并轮廓
            int baseY = groundLevel - y0;
            for (Rect rect : rectList) {
                drawMergedRect(rect, baseY);
            }
            glEnd();
        }
        // LOD2 远距离：不绘制方块，仅保留区块红色边框

        // 玩家所在区块：强制绘制红色外框
        if (isPlayerInChunk(playerX, playerY, playerZ)) {
            renderChunkRedBorder();
        }

        glPopAttrib();
    }

    // 判断玩家是否在当前区块内
    private boolean isPlayerInChunk(float px, float py, float pz) {
        return px >= x0 && px < x1 && py >= y0 && py < y1 && pz >= z0 && pz < z1;
    }

    // 绘制区块红色边框
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

    // 兼容方法
    public boolean isSolid(int x, int y, int z) {
        return getBlockWorld(x, y, z) != 0;
    }

    public void render(int layer) {
        render(layer, 0, 0, 0);
    }
}