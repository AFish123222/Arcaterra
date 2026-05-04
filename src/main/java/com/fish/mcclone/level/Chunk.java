package com.fish.mcclone.level;

import com.fish.mcclone.block.Block;
import com.fish.mcclone.phys.AABB;
import static org.lwjgl.opengl.GL11.*;

public class Chunk {
    public static int texture = 0;
    // 渲染半径
    public static final int RENDER_RADIUS = 64;
    public static final int RENDER_RADIUS_SQ = RENDER_RADIUS * RENDER_RADIUS;

    public final Level level;
    public final int x0, y0, z0;
    public final int x1, y1, z1;
    private static final int BASE_SIZE = 16;

    private final short[] blocks;
    private final int groundLevel;
    public final AABB aabb;

    // ====================== LOD 预留字段（完整保留） ======================
    protected int lodLevel = 0;
    protected Object lodMesh;
    protected boolean lodDirty = true;
    protected Chunk parent;
    protected Chunk[] children;

    // 贪心网格缓存
    private boolean meshDirty = true;
    private float[] greedyVertices;
    private float[] greedyTexCoords;

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

    // 生成地面
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

    // 方块操作
    public void setBlockLocal(int rx, int ry, int rz, int id) {
        if (rx <0||ry<0||rz<0||rx>=BASE_SIZE||ry>=BASE_SIZE||rz>=BASE_SIZE) return;
        blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] = (short) id;
        lodDirty = true;
        meshDirty = true;
    }

    public int getBlockLocal(int rx, int ry, int rz) {
        if (rx <0||ry<0||rz<0||rx>=BASE_SIZE||ry>=BASE_SIZE||rz>=BASE_SIZE) return 0;
        return blocks[(ry * BASE_SIZE + rz) * BASE_SIZE + rx] & 0xFF;
    }

    public int getBlockWorld(int x, int y, int z) {
        return getBlockLocal(x - x0, y - y0, z - z0);
    }

    // ====================== 核心渲染 ======================
    public void render(int layer, float playerX, float playerY, float playerZ) {
        // 距离裁剪
        float cx = (x0+x1)*0.5f, cz = (z0+z1)*0.5f;
        float dx = cx-playerX, dz=cz-playerZ;
        if (dx*dx + dz*dz > RENDER_RADIUS_SQ) return;

        // 重建贪心网格
        if (meshDirty) {
            buildGreedyMesh();
            meshDirty = false;
        }

        // 渲染贪心网格
        renderGreedyMesh();

        // 所有方块：细黑线框
        renderAllBlockWireframe();

        // 玩家区块：粗红线框
        if (isPlayerInChunk(playerX, playerY, playerZ)) {
            renderChunkRedBorder();
        }
    }

    // ====================== 贪心网格化核心 ======================
    private void buildGreedyMesh() {
        Tesselator t = Tesselator.getInstance();
        t.init();

        // 6个方向遍历
        for (int dir=0; dir<6; dir++) {
            boolean[][] mask = new boolean[BASE_SIZE][BASE_SIZE];

            // 构建可见性掩码
            for (int i=0; i<BASE_SIZE; i++) {
                for (int j=0; j<BASE_SIZE; j++) {
                    int x=x0+i, y=y0, z=z0+j;
                    if (dir==0) x=x0-1; if (dir==1) x=x0+16;
                    if (dir==2) y=y0-1; if (dir==3) y=y0+16;
                    if (dir==4) z=z0-1; if (dir==5) z=z0+16;

                    int self = getBlockWorld(x0+i, y0+((dir/3)==0?i:j), z0+j);
                    int neighbor = getBlockWorld(x,y,z);
                    mask[i][j] = (self !=0) && (neighbor ==0);
                }
            }

            // 贪心合并矩形
            for (int i=0; i<BASE_SIZE; i++) {
                for (int j=0; j<BASE_SIZE; j++) {
                    if (!mask[i][j]) continue;

                    // 扩展宽度
                    int w=1;
                    while (j+w<BASE_SIZE && mask[i][j+w]) w++;
                    // 扩展高度
                    int h=1;
                    boolean ok=true;
                    while (i+h<BASE_SIZE && ok) {
                        for (int ww=0; ww<w; ww++) if (!mask[i+h][j+ww]) ok=false;
                        if (ok) h++;
                    }

                    // 标记已合并
                    for (int hh=0; hh<h; hh++) for (int ww=0; ww<w; ww++) mask[i+hh][j+ww]=false;

                    // 渲染合并面
                    int bid = getBlockWorld(x0+i, y0+i, z0+j);
                    float fx=x0+i, fy=y0+i, fz=z0+j;
                    t.addFace(
                            dir%3-1, dir/3-1, dir%3-1,
                            fx, fy, fz,
                            fx+w, fy+h, fz+w,
                            bid
                    );
                }
            }
        }

        // 缓存网格数据
        greedyVertices = t.getVertices();
        greedyTexCoords = t.getTexCoords();
        t.flush();
    }

    // 渲染贪心网格
    private void renderGreedyMesh() {
        if (greedyVertices == null || greedyVertices.length ==0) return;
        Tesselator t = Tesselator.getInstance();
        t.init();
        for (float v : greedyVertices) t.vertex(v, v, v);
        t.flush();
    }

    // ====================== 线框渲染 ======================
    private boolean isPlayerInChunk(float px, float py, float pz) {
        return px>=x0&&px<x1 && py>=y0&&py<y1 && pz>=z0&&pz<z1;
    }

    // 所有方块：细黑线框
    private void renderAllBlockWireframe() {
        glPushAttrib(GL_ENABLE_BIT);
        glDisable(GL_TEXTURE_2D);
        glLineWidth(1.0f);
        glColor3f(0,0,0);

        glBegin(GL_LINES);
        for (int x=x0; x<x1; x++) {
            for (int y=y0; y<y1; y++) {
                for (int z=z0; z<z1; z++) {
                    if (getBlockWorld(x,y,z)==0) continue;
                    drawCube(x,y,z);
                }
            }
        }
        glEnd();
        glPopAttrib();
    }

    // 玩家区块：粗红线框
    private void renderChunkRedBorder() {
        glPushAttrib(GL_ENABLE_BIT);
        glDisable(GL_TEXTURE_2D);
        glLineWidth(3.0f);
        glColor3f(1,0,0);

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

    // 绘制单个方块线框
    private void drawCube(int x, int y, int z) {
        float x1=x+1,y1=y+1,z1=z+1;
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

    // 兼容方法
    public boolean isSolid(int x,int y,int z) { return getBlockWorld(x,y,z)!=0; }
    public void render(int layer) { render(layer,0,0,0); }
    public void setDirty() { lodDirty=true; meshDirty=true; }
}