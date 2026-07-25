package com.fish.arcaterra.worldgen.terrarium;

import com.fish.arcaterra.worldgen.TerrainProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 AWS Terrain Tiles 在线获取 DEM 数据。<br>
 * 构造时只需传入：原点经纬度、每个方块对应的水平米数。<br>
 * zoom 级别自动计算，使每个 DEM 像素尽量对应一个方块。<br>
 * 高度值直接使用 DEM 返回的米数（垂直方向不缩放）。
 */
public class DemTerrainProvider implements TerrainProvider {
    private static final String TILE_URL =
            "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";

    private final Map<Long, BufferedImage> cache = new HashMap<>();
    private final int zoom;
    private final double originLon;
    private final double originLat;
    private final double lonPerBlock; // 每个方块对应的经度增量（度/格）
    private final double latPerBlock; // 每个方块对应的纬度增量（度/格）

    /**
     * 构造器：自动计算最佳 zoom。
     * @param originLon 游戏世界原点 (0,0) 对应的经度（度）
     * @param originLat 游戏世界原点 (0,0) 对应的纬度（度）
     * @param meterPerBlock 每个方块在水平方向对应的实际米数（例如 1.0 表示 1 方块 = 1 米）
     */
    public DemTerrainProvider(double originLon, double originLat, double meterPerBlock) {
        this.originLon = originLon;
        this.originLat = originLat;

        // 计算每度经纬度对应的米数（使用原点纬度近似）
        double latRad = Math.toRadians(originLat);
        double metersPerDegreeLon = 111320 * Math.cos(latRad);
        double metersPerDegreeLat = 111320;

        // 每个方块对应的经纬度增量
        this.lonPerBlock = meterPerBlock / metersPerDegreeLon;
        this.latPerBlock = meterPerBlock / metersPerDegreeLat;

        // 自动选择 zoom：使每个像素对应的地面距离 ≈ meterPerBlock
        // 像素分辨率 = metersPerDegreeLat / (256 * 2^zoom)
        // 令其等于 meterPerBlock，解出 zoom
        double idealZoom = Math.log(metersPerDegreeLat / (256.0 * meterPerBlock))/Math.log(2); //log()->ln
        int z = (int) Math.round(idealZoom);
        z = Math.max(10, Math.min(14, z)); // 限制在 10~14 之间
        this.zoom = z;
        System.out.println("DemTerrainProvider: zoom=" + zoom + " (meterPerBlock=" + meterPerBlock + ")");
    }

    @Override
    public float getHeight(float worldX, float worldZ) {
        // 游戏坐标（方块）→ 经纬度（度）
        double lng = originLon + worldX * lonPerBlock;
        double lat = originLat + worldZ * latPerBlock;

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
            System.out.println("successfully fetch tile: " + url);
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