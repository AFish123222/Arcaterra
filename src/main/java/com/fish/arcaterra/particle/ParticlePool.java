package com.fish.arcaterra.particle;

import static org.lwjgl.opengl.GL11.*;

/**
 * 粒子对象池。
 * 预先分配 N 个粒子，循环使用，避免 GC。
 */
public class ParticlePool {
    private final Particle[] particles;
    private final int capacity;
    private int nextIndex = 0;

    /**
     * @param capacity 最大粒子数（建议 1000~10000）
     */
    public ParticlePool(int capacity) {
        this.capacity = capacity;
        particles = new Particle[capacity];
        for (int i = 0; i < capacity; i++) {
            particles[i] = new Particle();
        }
    }

    /**
     * 生成一个粒子
     * @return 如果池已满则返回 null
     */
    public Particle spawn(float x, float y, float z,
                          float vx, float vy, float vz,
                          float life, float size,
                          float r, float g, float b, float a,
                          boolean gravity) {
        // 从上次取出的位置开始寻找（环形缓冲区）
        int start = nextIndex;
        for (int i = 0; i < capacity; i++) {
            int idx = (start + i) % capacity;
            if (!particles[idx].isAlive()) {
                particles[idx].spawn(x, y, z, vx, vy, vz, life, size, r, g, b, a, gravity);
                nextIndex = (idx + 1) % capacity;
                return particles[idx];
            }
        }
        // 池已满
        return null;
    }

    /**
     * 更新所有粒子
     */
    public void update(float delta) {
        for (Particle p : particles) {
            p.update(delta);
        }
    }

    /**
     * 渲染所有粒子（始终面向玩家，即公告板）
     * @param camX 玩家 X
     * @param camY 玩家 Y
     * @param camZ 玩家 Z
     */
    public void render(float camX, float camY, float camZ) {
        // 计算玩家的水平旋转（用于公告板朝向）
        // 注意：这里假设你传入的是玩家视角方向，简化版直接用玩家位置
        // 更精确的公告板需要计算右向量和上向量，但简单的点精灵或固定朝向也可以。
        // 此处用最简单的方式：将每个粒子画为一个扁四边形，面向玩家

        for (Particle p : particles) {
            if (!p.isAlive()) continue;

            // 计算透明度（根据剩余生命渐隐）
            float alpha = p.a * (p.life / p.maxLife);
            if (alpha < 0.01f) continue;

            float size = p.size * (0.5f + 0.5f * (p.life / p.maxLife)); // 大小渐隐

            glPushMatrix();
            glTranslatef(p.x, p.y, p.z);

            // 让四边形始终面向玩家（公告板）
            // 方法：取消旋转，直接在世界空间绘制，但让四边形始终对着相机方向
            // 这里用最通用的方式：用玩家的位置计算朝向
            float dx = p.x - camX;
            float dy = p.y - camY;
            float dz = p.z - camZ;
            float dist = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.001f) {
                glPopMatrix();
                continue;
            }
            // 计算朝向：让四边形法线指向玩家
            // 但 OpenGL 固定管线没有直接面向相机的 API，我们通过旋转实现
            // 简化版本：直接用 GL_POINTS 或 glSprite（但 LWJGL 不支持 glSprite）
            // 这里采用更直接的方式：计算四边形的四个顶点，使其垂直于视线方向

            // 构造面向相机的四边形（Billboard）
            // 获取相机上向量（这里假设 Y 轴向上）
            float upX = 0, upY = 1, upZ = 0;
            // 视线方向（从粒子指向相机）
            float lookX = camX - p.x;
            float lookY = camY - p.y;
            float lookZ = camZ - p.z;
            float len = (float) Math.sqrt(lookX*lookX + lookY*lookY + lookZ*lookZ);
            if (len < 0.001f) {
                glPopMatrix();
                continue;
            }
            lookX /= len;
            lookY /= len;
            lookZ /= len;

            // 右向量 = look × up
            float rightX = lookY * upZ - lookZ * upY;
            float rightY = lookZ * upX - lookX * upZ;
            float rightZ = lookX * upY - lookY * upX;
            float rightLen = (float) Math.sqrt(rightX*rightX + rightY*rightY + rightZ*rightZ);
            if (rightLen < 0.001f) {
                // 如果视线与 Y 轴平行，用 Z 轴作为上向量
                upX = 0; upY = 0; upZ = 1;
                rightX = lookY * upZ - lookZ * upY;
                rightY = lookZ * upX - lookX * upZ;
                rightZ = lookX * upY - lookY * upX;
                rightLen = (float) Math.sqrt(rightX*rightX + rightY*rightY + rightZ*rightZ);
                if (rightLen < 0.001f) {
                    glPopMatrix();
                    continue;
                }
            }
            rightX /= rightLen;
            rightY /= rightLen;
            rightZ /= rightLen;

            // 上向量 = right × look
            float up2X = rightY * lookZ - rightZ * lookY;
            float up2Y = rightZ * lookX - rightX * lookZ;
            float up2Z = rightX * lookY - rightY * lookX;

            // 四个顶点偏移
            float half = size * 0.5f;
            float v1x = -rightX * half + up2X * half;
            float v1y = -rightY * half + up2Y * half;
            float v1z = -rightZ * half + up2Z * half;
            float v2x =  rightX * half + up2X * half;
            float v2y =  rightY * half + up2Y * half;
            float v2z =  rightZ * half + up2Z * half;
            float v3x =  rightX * half - up2X * half;
            float v3y =  rightY * half - up2Y * half;
            float v3z =  rightZ * half - up2Z * half;
            float v4x = -rightX * half - up2X * half;
            float v4y = -rightY * half - up2Y * half;
            float v4z = -rightZ * half - up2Z * half;

            glColor4f(p.r, p.g, p.b, alpha);
            glBegin(GL_QUADS);
            glVertex3f(v1x, v1y, v1z);
            glVertex3f(v2x, v2y, v2z);
            glVertex3f(v3x, v3y, v3z);
            glVertex3f(v4x, v4y, v4z);
            glEnd();

            glPopMatrix();
        }
    }
}
