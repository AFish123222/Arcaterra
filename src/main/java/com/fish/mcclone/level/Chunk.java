package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static int texture = 0;
    public final Level level;

    // 区块世界坐标/尺寸
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    private static final int BASE_SIZE = 16;

    // 区块独立方块数据
    private final short[] blocks;
    private final int groundLevel;
    public final AABB aabb;

    // ====================== LOD 树预留字段（你要的完整版本） ======================
    protected int lodLevel = 0;          // LOD层级(0=原始16x, 1=32x, 2=64x...)
    protected Object lodMesh;            // LOD网格缓存(VAO/VBO)
    protected boolean lodDirty = true;    // LOD脏标记(方块修改后重建)
    protected Chunk parent;              // 父LOD区块(树状结构)
    protected Chunk[] children;          // 子LOD区块(八叉树8个子节点)

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

        // 初始化16x16x16方块数组
        this.blocks = new short[BASE_SIZE * BASE_SIZE * BASE_SIZE];
        initTerrain();
    }

    // 生成地面草方块
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

    // 本地坐标设置方块
    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if (rx < 0 || ry < 0 || rz < 0 || rx >= BASE_SIZE || ry >= BASE_SIZE || rz >= BASE_SIZE) return;
        blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] = (short) id;
        lodDirty = true; // 方块修改 → 标记LOD需要重建
    }

    // 本地坐标获取方块
    public int getBlockLocal(int rx, int ry, int rz) {
        if (rx < 0 || ry < 0 || rz < 0 || rx >= BASE_SIZE || ry >= BASE_SIZE || rz >= BASE_SIZE) return 0;
        return blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] & 0xFF;
    }

    // 世界坐标获取方块
    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x - x0, y - y0, z - z0);
    }

    // ==============================================
    // 核心渲染
    // 1. 渲染方块
    // 2. 所有方块：细黑线框
    // 3. 玩家区块：粗红框
    // ==============================================
    public void render(int layer, float playerX, float playerY, float playerZ) {
        Tesselator t = Tesselator.getInstance();
        t.init();

        // 渲染区块内所有方块
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    int id = getBlockWorld(x, y, z);
                    if (id == 0) continue;

                    // 暴露面剔除
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

        // 所有实体方块：细黑色线框
        renderAllBlockWireframe();

        // 玩家所在区块：粗红色外框
        if (isPlayerInChunk(playerX, playerY, playerZ)) {
            renderChunkRedBorder();
        }
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlockWorld(x, y, z) != 0;
    }

    // 判断玩家是否在当前区块
    private boolean isPlayerInChunk(float px, float py, float pz) {
        return px >= x0 && px < x1 && py >= y0 && py < y1 && pz >= z0 && pz < z1;
    }

    // 绘制单个方块的黑色线框
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

    // 绘制单个1×1×1立方体线框
    private void drawCube(int x, int y, int z) {
        float x1 = x + 1, y1 = y + 1, z1 = z + 1;
        // 底面
        glVertex3f(x,y,z); glVertex3f(x1,y,z);
        glVertex3f(x1,y,z); glVertex3f(x1,y,z1);
        glVertex3f(x1,y,z1); glVertex3f(x,y,z1);
        glVertex3f(x,y,z1); glVertex3f(x,y,z);
        // 顶面
        glVertex3f(x,y1,z); glVertex3f(x1,y1,z);
        glVertex3f(x1,y1,z); glVertex3f(x1,y1,z1);
        glVertex3f(x1,y1,z1); glVertex3f(x,y1,z1);
        glVertex3f(x,y1,z1); glVertex3f(x,y1,z);
        // 竖边
        glVertex3f(x,y,z); glVertex3f(x,y1,z);
        glVertex3f(x1,y,z); glVertex3f(x1,y1,z);
        glVertex3f(x1,y,z1); glVertex3f(x1,y1,z1);
        glVertex3f(x,y,z1); glVertex3f(x,y1,z1);
    }

    // 绘制玩家区块的红色粗外框
    private void renderChunkRedBorder() {
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(3.0f);
        glColor3f(1.0f, 0.0f, 0.0f);

        glBegin(GL_LINES);
        // 底面
        glVertex3f(x0,y0,z0); glVertex3f(x1,y0,z0);
        glVertex3f(x1,y0,z0); glVertex3f(x1,y0,z1);
        glVertex3f(x1,y0,z1); glVertex3f(x0,y0,z1);
        glVertex3f(x0,y0,z1); glVertex3f(x0,y0,z0);
        // 顶面
        glVertex3f(x0,y1,z0); glVertex3f(x1,y1,z0);
        glVertex3f(x1,y1,z0); glVertex3f(x1,y1,z1);
        glVertex3f(x1,y1,z1); glVertex3f(x0,y1,z1);
        glVertex3f(x0,y1,z1); glVertex3f(x0,y1,z0);
        // 竖边
        glVertex3f(x0,y0,z0); glVertex3f(x0,y1,z0);
        glVertex3f(x1,y0,z0); glVertex3f(x1,y1,z0);
        glVertex3f(x1,y0,z1); glVertex3f(x1,y1,z1);
        glVertex3f(x0,y0,z1); glVertex3f(x0,y1,z1);
        glEnd();
        glPopAttrib();
    }

    // 兼容方法
    public void render(int layer) { render(layer, 0, 0, 0); }
    public void setDirty() { lodDirty = true; }
}