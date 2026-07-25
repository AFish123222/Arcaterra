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
 * 瓦片格式：Terrarium (RGB 编码高程)。 <br>
 * example: <br>
 * // 宝鸡中心：107.1°E, 34.3°N <br>
 * // 1°经度 ≈ 111320 * cos(34.3°) ≈ 92000 米 <br>
 * // 1°纬度 ≈ 111320 米 <br>
 * DemTerrainProvider dem = new DemTerrainProvider(12, 107.1, 34.3, 92000.0, 111320.0); <br>
 * world = new World(dem); <br>
 */
public class DemTerrainProvider implements TerrainProvider {
    // 在线瓦片服务（国内推荐用 TellusCN 镜像，需要自己查具体地址）
    private static final String TILE_URL =
            "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";//2026-7-25：通的
    private final Map<Long, BufferedImage> cache = new HashMap<>();
    private final int zoom;

    private final double originLon;
    private final double originLat;
    private final double lonPerMeter; // 每米对应的经度增量（度/米）
    private final double latPerMeter; // 每米对应的纬度增量（度/米）

    /**
     * @param zoom 瓦片缩放级别（10~14），越高越精细
     * @param originLon 游戏世界原点 (0,0) 对应的经度（度）
     * @param originLat 游戏世界原点 (0,0) 对应的纬度（度）
     * @param metersPerDegreeLon 每度经度对应的米数（在原点附近），例如宝鸡约 92000 米/度
     * @param metersPerDegreeLat 每度纬度对应的米数，约 111320 米/度（可近似）
     */
    public DemTerrainProvider(int zoom, double originLon, double originLat,
                              double metersPerDegreeLon, double metersPerDegreeLat) {
        this.zoom = zoom;
        this.originLon = originLon;
        this.originLat = originLat;
        this.lonPerMeter = 1.0 / metersPerDegreeLon;
        this.latPerMeter = 1.0 / metersPerDegreeLat;
    }

    @Override
    public float getHeight(float worldX, float worldZ) {

        // 经纬度 → 瓦片坐标
        // 游戏坐标（米）→ 经纬度（度）
        double lng = originLon + worldX * lonPerMeter;
        double lat = originLat + worldZ * latPerMeter;
        int[] tile = latLngToTile(lat, lng, zoom);
        int tileX = tile[0], tileY = tile[1];

//        System.out.println("getHeight: worldX=" + worldX + ", worldZ=" + worldZ);
//        System.out.println("lat=" + lat + ", lng=" + lng);
//        System.out.println("tileX=" + tileX + ", tileY=" + tileY);

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
            System.out.println("Success to fetch tile: " + url);
            return ImageIO.read(conn.getInputStream());
        } catch (Exception e) {
            System.err.println("Failed to fetch tile: " + e.getMessage());
            // 返回全 0 的占位图
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
