package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static int texture = 0;
    public final Level level;

    // 区块世界坐标/尺寸 (标准化，支持树状LOD)
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    public final int SIZE_X, SIZE_Y, SIZE_Z;

    // ====================== 核心：Chunk 独立方块存储 ======================
    private final short[] blocks;
    private static final int BASE_SIZE = 16; // 基础区块尺寸(叶子节点)

    // ====================== LOD 预留字段（未来直接用） ======================
    protected int lodLevel = 0;          // LOD层级(0=原始16x, 1=32x, 2=64x...)
    protected Object lodMesh;            // LOD网格缓存(VertexBuffer/VAO)
    protected boolean lodDirty = true;   // LOD脏标记(修改方块后重建LOD)
    protected Chunk parent;              // 父LOD区块(树状结构)
    protected Chunk[] children;          // 子LOD区块(八叉树)

    // 渲染/地形
    private final int groundLevel;
    public final AABB aabb;

    // ====================== 构造函数（支持任意尺寸，为LODChunk准备） ======================
    public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.level = level;
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.SIZE_X = x1-x0; this.SIZE_Y = y1-y0; this.SIZE_Z = z1-z0;
        this.aabb = new AABB(x0,y0,z0,x1,y1,z1);
        this.groundLevel = level.groundY;

        // 独立方块数组：16x16x16（基础区块，LOD叶子节点）
        this.blocks = new short[BASE_SIZE * BASE_SIZE * BASE_SIZE];

        // 初始化地面（自己的方块数据）
        initTerrain();
    }

    // ====================== 独立地形生成 ======================
    private void initTerrain() {
        for (int rx=0; rx<BASE_SIZE; rx++) {
            for (int rz=0; rz<BASE_SIZE; rz++) {
                int worldY = groundLevel;
                int ry = worldY - y0;
                if (ry >=0 && ry < BASE_SIZE) {
                    setBlockLocal(rx, ry, rz, Block.GRASS);
                }
            }
        }
    }

    // ====================== 独立方块操作（本地坐标 0~15） ======================
    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if (rx<0||ry<0||rz<0||rx>=BASE_SIZE||ry>=BASE_SIZE||rz>=BASE_SIZE) return;
        blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] = (short) id;
        lodDirty = true; // 方块修改 → LOD脏
    }

    public int getBlockLocal(int rx, int ry, int rz) {
        if (rx<0||ry<0||rz<0||rx>=BASE_SIZE||ry>=BASE_SIZE||rz>=BASE_SIZE) return 0;
        return blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] & 0xFF;
    }

    // 世界坐标 → 本地坐标 获取方块
    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x-x0, y-y0, z-z0);
    }

    // ====================== 核心渲染：只渲染玩家区块 ======================
    public void render(int layer, float px, float py, float pz) {
        // 非玩家区块 → 跳过
        if (!isPlayerInside(px,py,pz)) return;

        // ====================== 未来：优先渲染LOD ======================
        // if(lodLevel > 0) { renderLod(); return; }

        Tesselator t = Tesselator.getInstance();
        t.init();

        for (int x=x0; x<x1; x++) {
            for (int y=y0; y<y1; y++) {
                for (int z=z0; z<z1; z++) {
                    int id = getBlockWorld(x,y,z);
                    if (id == 0) continue;

                    // 暴露面剔除
                    boolean exposed = !isSolid(x+1,y,z)||!isSolid(x-1,y,z)||
                            !isSolid(x,y+1,z)||!isSolid(x,y-1,z)||
                            !isSolid(x,y,z+1)||!isSolid(x,y,z-1);
                    if (!exposed) continue;

                    // 渲染方块
                    if (y < groundLevel) Tile.rock.render(t,level,layer,x,y,z);
                    else Tile.grass.render(t,level,layer,x,y,z);
                }
            }
        }

        t.flush();
        renderDebugBounds(px,py,pz);
    }

    // ====================== LOD 渲染占位（未来实现） ======================
    protected void renderLod() {
        // 未来：渲染简化的LOD网格
    }

    // ====================== 工具方法 ======================
    public boolean isSolid(int x, int y, int z) {
        return getBlockWorld(x,y,z) != 0;
    }

    private boolean isPlayerInside(float px, float py, float pz) {
        return px>=x0 && px<x1 && py>=y0 && py<y1 && pz>=z0 && pz<z1;
    }

    // 红色调试线框
    private void renderDebugBounds(float px,float py,float pz) {
        if (!isPlayerInside(px,py,pz)) return;
        glPushAttrib(GL_ENABLE_BIT|GL_CURRENT_BIT);
        glDisable(GL_TEXTURE_2D);
        glColor3f(1,0,0);
        glLineWidth(2);
        glBegin(GL_LINES);
        // 底面
        glVertex3f(x0,y0,z0);glVertex3f(x1,y0,z0);
        glVertex3f(x1,y0,z0);glVertex3f(x1,y0,z1);
        glVertex3f(x1,y0,z1);glVertex3f(x0,y0,z1);
        glVertex3f(x0,y0,z1);glVertex3f(x0,y0,z0);
        // 顶面
        glVertex3f(x0,y1,z0);glVertex3f(x1,y1,z0);
        glVertex3f(x1,y1,z0);glVertex3f(x1,y1,z1);
        glVertex3f(x1,y1,z1);glVertex3f(x0,y1,z1);
        glVertex3f(x0,y1,z1);glVertex3f(x0,y1,z0);
        // 垂直
        glVertex3f(x0,y0,z0);glVertex3f(x0,y1,z0);
        glVertex3f(x1,y0,z0);glVertex3f(x1,y1,z0);
        glVertex3f(x1,y0,z1);glVertex3f(x1,y1,z1);
        glVertex3f(x0,y0,z1);glVertex3f(x0,y1,z1);
        glEnd();
        glPopAttrib();
    }

    // 兼容方法
    public void render(int layer) { render(layer,0,0,0); }
    public void setDirty() { lodDirty = true; }
}