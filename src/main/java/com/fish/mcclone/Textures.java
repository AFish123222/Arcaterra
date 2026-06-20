package com.fish.mcclone;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
@Deprecated
public class Textures {
    private static HashMap<String, Integer> idMap = new HashMap<String, Integer>();

    private static int lastId = -9999999;

    public static int loadTexture(String resourceName, int mode) {
        try {
            if (idMap.containsKey(resourceName))
                return ((Integer)idMap.get(resourceName)).intValue();
            IntBuffer ib = BufferUtils.createIntBuffer(1);
            GL11.glGenTextures(ib);
            int id = ib.get(0);
            bind(id);
            GL11.glTexParameteri(3553, 10241, mode);
            GL11.glTexParameteri(3553, 10240, mode);
            BufferedImage img = ImageIO.read(Objects.requireNonNull(Textures.class.getResourceAsStream(resourceName)));
            int w = img.getWidth();
            int h = img.getHeight();
            ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
            int[] rawPixels = new int[w * h];
            img.getRGB(0, 0, w, h, rawPixels, 0, w);
            for (int i = 0; i < rawPixels.length; i++) {
                int a = rawPixels[i] >> 24 & 0xFF;
                int r = rawPixels[i] >> 16 & 0xFF;
                int g = rawPixels[i] >> 8 & 0xFF;
                int b = rawPixels[i] & 0xFF;
                rawPixels[i] = a << 24 | b << 16 | g << 8 | r;
            }
            pixels.asIntBuffer().put(rawPixels);
//            GLU.gluBuild2DMipmaps(3553, 6408, w, h, 6408, 5121, pixels);
            // 1. 上传第 0 层（基础层）纹理
            glTexImage2D(
                    GL_TEXTURE_2D,    // target: 3553 → GL_TEXTURE_2D
                    0,                 // level: 基础层（mipmap 第 0 层）
                    GL_RGBA,           // internalformat: 6408 → GL_RGBA（十进制 0x1908 = 6408）
                    w, h,              // width, height
                    0,                 // border: 必须为 0
                    GL_RGBA,           // format: 6408 → GL_RGBA
                    GL_UNSIGNED_BYTE,  // type: 5121 → GL_UNSIGNED_BYTE（十进制 0x1401 = 5121）
                    pixels             // pixels: 纹理数据缓冲区
            );

// 2. 自动生成所有 mipmap 层
            glGenerateMipmap(GL_TEXTURE_2D);
            return id;
        } catch (IOException e) {
            throw new RuntimeException("!!");
        }
    }

    public static void bind(int id) {
        if (id != lastId) {
            GL11.glBindTexture(3553, id);
            lastId = id;
        }
    }
}
