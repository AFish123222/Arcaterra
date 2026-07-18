package com.fish.arcaterra.particle;

/**
 * 单个粒子，使用对象池管理。
 * 属性可复用，死亡后重置。
 */
public class Particle {
    // 位置
    public float x, y, z;
    // 速度
    public float vx, vy, vz;
    // 生命值（当前/最大）
    public float life, maxLife;
    // 大小（半径）
    public float size;
    // 颜色 RGBA（0~1）
    public float r, g, b, a;
    // 是否受重力影响
    public boolean gravity;
    // 是否存活
    public boolean alive;

    public Particle() {
        alive = false;
    }

    /**
     * 从池中取出时调用，初始化粒子属性。
     */
    public void spawn(float x, float y, float z,
                      float vx, float vy, float vz,
                      float life, float size,
                      float r, float g, float b, float a,
                      boolean gravity) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.gravity = gravity;
        this.alive = true;
    }

    /**
     * 每帧更新
     * @param delta 帧间隔
     */
    public void update(float delta) {
        if (!alive) return;

        // 衰减生命
        life -= delta;
        if (life <= 0) {
            alive = false;
            return;
        }

        // 速度
        if (gravity) vy -= 0.5f * delta; // 重力加速度
        x += vx * delta;
        y += vy * delta;
        z += vz * delta;

        // 阻尼（可选，让粒子变慢）
        vx *= 0.98f;
        vy *= 0.98f;
        vz *= 0.98f;
    }

    /**
     * 粒子存活且生命值大于 0
     */
    public boolean isAlive() {
        return alive && life > 0;
    }
}
