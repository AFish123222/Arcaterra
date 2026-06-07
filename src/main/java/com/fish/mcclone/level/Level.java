package com.fish.mcclone.level;

import com.fish.mcclone.phys.AABB;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class Level {
    // ============== 无限地图核心配置 ==============
    public static final int CHUNK_SIZE = 16;
    public static final int WORLD_HEIGHT = 128;
    public static final int LOAD_RADIUS = 5;        // 水平加载半径
    public static final int UNLOAD_RADIUS = 7;      // 卸载半径（必须>加载半径）
    public static final int VERTICAL_LOAD_RANGE = 2;// 🔥 修复1：垂直只加载玩家上下2层区块（不是全量）

    // ============== DEM 高度图配置 ==============
    private int[] heightMap;
    private final int heightMapSize = 1024;
    private final float heightScale = 60;
    private final int baseHeight = 20;

    // ============== 动态区块存储 ==============
    final Map<Long, Chunk> chunkMap = new HashMap<>();
    public final int groundY;
    static int[] lightDepths;
    final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    // ============== 🔥 修复2：删除废弃无用变量（杜绝内存泄漏） ==============
    public final int width = Integer.MAX_VALUE;
    public final int height = Integer.MAX_VALUE;
    public final int depth = WORLD_HEIGHT;

    // ====================== 构造方法 ======================
    public Level(int w, int h, int d) {
        this.groundY = 60;
        lightDepths = new int[1024 * 1024];
        loadHeightmap();
    }

    // ====================== 加载高度图 ======================
    private void loadHeightmap() {
        try {
            BufferedImage image = ImageIO.read(new File("resources/heightmap.png"));
            heightMap = new int[heightMapSize * heightMapSize];
            for (int z = 0; z < heightMapSize; z++) {
                for (int x = 0; x < heightMapSize; x++) {
                    int rgb = image.getRGB(x, z);
                    int gray = (rgb >> 16) & 0xFF;
                    heightMap[x + z * heightMapSize] = baseHeight + (int) (gray / 255f * heightScale);
                }
            }
            System.out.println("✅ DEM高度图加载成功！");
        } catch (Exception e) {
            System.out.println("❌ 高度图加载失败，使用默认平地");
            heightMap = new int[heightMapSize * heightMapSize];
            Arrays.fill(heightMap, 48);
        }
    }

    // ====================== 获取地形高度 ======================
    public int getTopHeight(int worldX, int worldZ) {
        int x = (worldX % heightMapSize + heightMapSize) % heightMapSize;
        int z = (worldZ % heightMapSize + heightMapSize) % heightMapSize;
        return heightMap[x + z * heightMapSize];
    }

    // ====================== 区块坐标打包 ======================
    private long packChunkPos(int cx, int cy, int cz) {
        return ((long) cx & 0xFFFFFFL) << 40 | ((long) cy & 0xFFFFL) << 24 | ((long) cz & 0xFFFFFFL);
    }

    // ====================== 动态获取/创建区块 ======================
    public Chunk getChunkByWorldPos(int x, int y, int z) {
        if (y < 0 || y >= WORLD_HEIGHT) return null;

        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);

        long key = packChunkPos(cx, cy, cz);
        return chunkMap.computeIfAbsent(key, k -> {
            // 仅缺失时创建，杜绝重复创建
            int x0 = cx * CHUNK_SIZE;
            int y0 = cy * CHUNK_SIZE;
            int z0 = cz * CHUNK_SIZE;
            int x1 = x0 + CHUNK_SIZE;
            int y1 = Math.min(y0 + CHUNK_SIZE, WORLD_HEIGHT);
            int z1 = z0 + CHUNK_SIZE;
            return new Chunk(this, x0, y0, z0, x1, y1, z1);
        });
    }

    // ====================== 🔥 核心修复：区块更新/卸载（内存救星） ======================
    // ====================== 🔥 核心修复：正确顺序（先加载 → 后卸载） ======================
    public void updateChunks(float playerX, float playerY, float playerZ) {
        int px = (int) playerX;
        int py = (int) playerY;
        int pz = (int) playerZ;

        // 卸载距离平方（7*16）
        int unloadDistSq = UNLOAD_RADIUS * CHUNK_SIZE;
        unloadDistSq *= unloadDistSq;

        // ======================================
        // 🔥 修复1：【先加载】附近区块（必须第一步）
        // ======================================
        int cx0 = Math.floorDiv(px, CHUNK_SIZE);
        int cz0 = Math.floorDiv(pz, CHUNK_SIZE);
        int cy0 = Math.floorDiv(py, CHUNK_SIZE);

        // 水平范围加载
        for (int cx = cx0 - LOAD_RADIUS; cx <= cx0 + LOAD_RADIUS; cx++) {
            for (int cz = cz0 - LOAD_RADIUS; cz <= cz0 + LOAD_RADIUS; cz++) {
                // 🔥 修复2：垂直范围限制（仅0~7有效区块，杜绝超界）
                for (int cy = Math.max(0, cy0 - 2); cy <= Math.min(7, cy0 + 2); cy++) {
                    getChunkByWorldPos(cx * CHUNK_SIZE, cy * CHUNK_SIZE, cz * CHUNK_SIZE);
                }
            }
        }

        // ======================================
        // 🔥 修复3：【后卸载】远处区块（必须第二步）
        // ======================================
        Iterator<Map.Entry<Long, Chunk>> it = chunkMap.entrySet().iterator();
        int unloadCount = 0;
        while (it.hasNext()) {
            Chunk c = it.next().getValue();
            int chunkCenterX = c.x0 + 8;
            int chunkCenterZ = c.z0 + 8;
            int dx = chunkCenterX - px;
            int dz = chunkCenterZ - pz;
            int distSq = dx * dx + dz * dz;

            // 超出距离才卸载
            if (distSq > unloadDistSq) {
                it.remove();
                unloadCount++;
            }
        }
        if (unloadCount > 0) {
            System.out.println("♻️ 卸载远区块: " + unloadCount + " | 剩余区块: " + chunkMap.size());
        }
    }

    // ====================== 原有逻辑 ======================
    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int z = y0; z < y1; z++) {
                int y = WORLD_HEIGHT - 1;
                while (y > 0) {
                    Chunk chunk = getChunkByWorldPos(x, y, z);
                    if (chunk == null || chunk.isSolid(x, y, z)) break;
                    y--;
                }
                if (x >= 0 && z >= 0 && x < 1024 && z < 1024) {
                    lightDepths[x + z * 1024] = y;
                }
            }
        }
    }

    public ArrayList<AABB> getCubes(AABB aabb) {
        ArrayList<AABB> list = new ArrayList<>();
        int x0 = (int) aabb.x0;
        int x1 = (int) aabb.x1 + 1;
        int y0 = Math.max(0, (int) aabb.y0);
        int y1 = Math.min(WORLD_HEIGHT, (int) aabb.y1 + 1);
        int z0 = (int) aabb.z0;
        int z1 = (int) aabb.z1 + 1;

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Chunk chunk = getChunkByWorldPos(x, y, z);
                    if (chunk != null && chunk.isSolid(x, y, z)) {
                        list.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                    }
                }
            }
        }
        return list;
    }

    public float getBrightness(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= 1024 || z >= 1024) return 1.0f;
        return y < lightDepths[x + z * 1024] ? 0.8f : 1.0f;
    }
    public void addListener(LevelListener l) { levelListeners.add(l); }
    public void load() {}
    public void save() {}
}