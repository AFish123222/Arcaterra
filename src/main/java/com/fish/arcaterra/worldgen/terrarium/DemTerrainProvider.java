package com.fish.arcaterra.worldgen.terrarium;

import com.fish.arcaterra.worldgen.TerrainProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 Mapterhorn 或 AWS Terrain Tiles 在线获取 DEM 数据。<br>
 * 瓦片格式：Terrarium (RGB 编码高程)。<br>
 * 支持两种构造方式：<br>
 * 1. 传入每度经纬度对应的米数（精确）<br>
 * 2. 传入每个方块对应的水平/垂直米数（直观）<br>
 * <br>
 * 示例（直观方式，宝鸡附近，1方块=1米）：<br>
 * DemTerrainProvider dem = new DemTerrainProvider(12, 107.1, 34.3, 1.0, 1.0);
 */
public class DemTerrainProvider implements TerrainProvider {
    // 在线瓦片服务
    private static final String TILE_URL =
            "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";

    private final Map<Long, BufferedImage> cache = new HashMap<>();
    private final int zoom;

    private final double originLon;
    private final double originLat;
    private final double lonPerMeter; // 每米对应的经度增量（度/米）
    private final double latPerMeter; // 每米对应的纬度增量（度/米）

//    /**
//     * 构造器（精确方式）：直接传入每度经纬度对应的米数。
//     * @param zoom 瓦片缩放级别（10~14）
//     * @param originLon 游戏世界原点 (0,0) 对应的经度（度）
//     * @param originLat 游戏世界原点 (0,0) 对应的纬度（度）
//     * @param metersPerDegreeLon 每度经度对应的米数（在原点附近）
//     * @param metersPerDegreeLat 每度纬度对应的米数（约 111320）
//     */
//    public DemTerrainProvider(int zoom, double originLon, double originLat,
//                              double metersPerDegreeLon, double metersPerDegreeLat) {
//        this.zoom = zoom;
//        this.originLon = originLon;
//        this.originLat = originLat;
//        this.lonPerMeter = 1.0 / metersPerDegreeLon;
//        this.latPerMeter = 1.0 / metersPerDegreeLat;
//    }

    /**
     * 构造器（直观方式）：直接传入每个方块对应的水平/垂直米数。
     * 内部自动计算每度经纬度对应的米数（使用原点纬度近似）。
     * @param originLon 游戏世界原点 (0,0) 对应的经度（度）
     * @param originLat 游戏世界原点 (0,0) 对应的纬度（度）
     * @param meterPerBlock  每格对应几米
     */
    public DemTerrainProvider(double originLon, double originLat, double meterPerBlock) {
        this.originLon = originLon;
        this.originLat = originLat;
        // 计算最佳 zoom
        double latRad = Math.toRadians(originLat);
        double metersPerDegreeLon = 111320 * Math.cos(latRad);
        double metersPerDegreeLat = 111320;
        // 每个像素对应的纬度/经度跨度 = meterPerBlock / (每度米数)
        this.lonPerMeter = meterPerBlock / metersPerDegreeLon;
        this.latPerMeter = meterPerBlock / metersPerDegreeLat;
        // 计算最佳 zoom：使像素分辨率尽量接近 meterPerBlock
        // 像素分辨率 = (每度米数) / (256 * 2^zoom)
        // 让 (每度米数) / (256 * 2^zoom) ≈ meterPerBlock
        // 解得 zoom = log2(每度米数 / (256 * meterPerBlock))
        // 用纬度方向的米数（恒定）计算
        double targetResolution = meterPerBlock; // 我们希望每个像素对应 meterPerBlock 米
        double idealZoom = Math.log(metersPerDegreeLat / (256.0 * targetResolution))/Math.log(2);//log()->ln
        this.zoom = (int) Math.round(idealZoom);
        // 限制在 10~14 之间
        if (this.zoom < 10) this.zoom = 10;
        if (this.zoom > 14) this.zoom = 14;
    }
    @Override
    public float getHeight(float worldX, float worldZ) {
        // 游戏坐标（米）→ 经纬度（度）
        double lng = originLon + worldX * lonPerMeter;
        double lat = originLat + worldZ * latPerMeter;

        // 经纬度 → 瓦片坐标
        int[] tile = latLngToTile(lat, lng, zoom);
        int tileX = tile[0], tileY = tile[1];

        long key = ((long) tileX << 32) | (tileY & 0xFFFFFFFFL);
        BufferedImage img = cache.computeIfAbsent(key, k -> fetchTile(tileX, tileY));

        // 像素坐标
        double[] pixel = tileToPixel(lat, lng, tileX, tileY, zoom);
        int px = (int) Math.round(pixel[0]);
        int py = (int) Math.round(pixel[1]);
        px = Math.max(0, Math.min(px, 255));
        py = Math.max(0, Math.min(py, 255));

        int rgb = img.getRGB(px, py);
        return decodeTerrariumHeight(rgb);
    }

    private BufferedImage fetchTile(int x, int y) {
        try {
            String url = TILE_URL.replace("{z}", String.valueOf(zoom))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return ImageIO.read(conn.getInputStream());
        } catch (Exception e) {
            System.err.println("Failed to fetch tile: " + e.getMessage());
            return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        }
    }

    private int[] latLngToTile(double lat, double lng, int zoom) {
        int x = (int) Math.floor((lng + 180) / 360 * (1 << zoom));
        int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) +
                1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        return new int[]{x, y};
    }

    private double[] tileToPixel(double lat, double lng, int tx, int ty, int zoom) {
        double px = (lng + 180) / 360 * (1 << zoom);
        double py = (1 - Math.log(Math.tan(Math.toRadians(lat)) +
                1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
        return new double[]{(px - tx) * 256, (py - ty) * 256};
    }

    private float decodeTerrariumHeight(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r * 256 + g + b / 256f - 32768;
    }

    @Override
    public float getMinX() { return -180; }
    @Override
    public float getMaxX() { return 180; }
    @Override
    public float getMinZ() { return -90; }
    @Override
    public float getMaxZ() { return 90; }
}