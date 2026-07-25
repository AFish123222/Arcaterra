package com.fish.arcaterra.terrarium;

import com.fish.arcaterra.worldgen.TerrainProvider;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;

/**
 * 使用标准 Java ImageIO + imageio-ext 插件读取 GeoTIFF。
 * 需要手动提供 DEM 覆盖的地理范围（minX, maxX, minZ, maxZ）。
 */
@Deprecated
public class DemTerrainProvider implements TerrainProvider {
    private final Raster raster;
    private final int width, height;
    private final double minX, maxX, minZ, maxZ;
    private final double pixelSizeX, pixelSizeZ;

    /**
     * @param filePath .tif 文件路径
     * @param minX     地理最小 X（例如 -1000）
     * @param maxX     地理最大 X（例如 1000）
     * @param minZ     地理最小 Z（例如 -1000）
     * @param maxZ     地理最大 Z（例如 1000）
     * @throws IOException 读取失败
     */
    public DemTerrainProvider(String filePath, double minX, double maxX, double minZ, double maxZ) throws IOException {
        // 注册 imageio-ext 插件（通常自动注册，但安全起见）
        ImageIO.scanForPlugins();

        File file = new File(filePath);
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            ImageReader reader = ImageIO.getImageReaders(iis).next();
            reader.setInput(iis);
            // 读取第一个图像（假设单波段）
            var image = reader.read(0);
            this.raster = image.getRaster();
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.pixelSizeX = (maxX - minX) / width;
        this.pixelSizeZ = (maxZ - minZ) / height;

        System.out.println("DEM 加载成功: " + width + "x" + height +
                ", 范围: [" + minX + ", " + maxX + "] x [" + minZ + ", " + maxZ + "]");
    }

    @Override
    public float getHeight(float worldX, float worldZ) {
        // 坐标超出范围返回 0（或可以插值到最近边界）
        if (worldX < minX || worldX > maxX || worldZ < minZ || worldZ > maxZ) {
            return 0f;
        }
        // 将世界坐标映射到像素坐标（注意：Z 轴可能反转，取决于 DEM 的投影）
        int px = (int) ((worldX - minX) / pixelSizeX);
        int pz = (int) ((worldZ - minZ) / pixelSizeZ);
        // 钳制到有效范围
        if (px < 0) px = 0;
        if (px >= width) px = width - 1;
        if (pz < 0) pz = 0;
        if (pz >= height) pz = height - 1;

        try {
            // 尝试读取浮点型像素（如果 DEM 是浮点）
            float[] pixel = new float[1];
            raster.getPixel(px, pz, pixel);
            return pixel[0];
        } catch (Exception e) {
            // 如果是整型，取 sample
            int val = raster.getSample(px, pz, 0);
            return val;
        }
    }

    @Override
    public float getMinX() { return (float) minX; }
    @Override
    public float getMaxX() { return (float) maxX; }
    @Override
    public float getMinZ() { return (float) minZ; }
    @Override
    public float getMaxZ() { return (float) maxZ; }
}