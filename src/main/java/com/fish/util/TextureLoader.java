package com.fish.util;

import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class TextureLoader {
    public static int loadTexture(String path) {
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        // 关键参数（解决紫色问题！）
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        try (InputStream is = TextureLoader.class.getResourceAsStream("/texture/" + path)) {
            BufferedImage img = ImageIO.read(is);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            // 翻转Y轴（适配OpenGL坐标）
            for (int y = 0; y < h / 2; y++) {
                for (int x = 0; x < w; x++) {
                    int temp = pixels[y * w + x];
                    pixels[y * w + x] = pixels[(h - 1 - y) * w + x];
                    pixels[(h - 1 - y) * w + x] = temp;
                }
            }

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } catch (Exception e) {
            e.printStackTrace();
            return 0; // 加载失败返回0（默认纹理，避免崩溃）
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texId;
    }
}