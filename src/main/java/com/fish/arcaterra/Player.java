package com.fish.arcaterra;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.phys.AABB;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

public class Player {
    private final World world;

    // 坐标
    public float x, y, z;
    public float xo, yo, zo; // 上一帧坐标

    // 速度
    public float xd, yd, zd;

    // 视角旋转
    public float yRot; // 水平左右
    public float xRot; // 俯仰上下

    // 碰撞包围盒
    public AABB bb;
    public boolean onGround = false;

    // 按键缓存
    private boolean keyW, keyS, keyA, keyD;
    private boolean keyUp, keyDown, keyLeft, keyRight;
    private boolean keySpace;
    private boolean keyR;

    public Player(World world) {
        this.world = world;
        resetPos();
        this.xRot = 0.0F;
        this.yRot = 0.0F;
        // 初始出生坐标
        this.x = 30;
        this.y = 80;
        this.z = 50;
    }

    // 重置出生点（无限地图无边界，固定安全高空）
    private void resetPos() {
        float x = 30 + (float) Math.random() * 20;
        float y = 100;
        float z = 50 + (float) Math.random() * 20;
        setPos(x, y, z);
    }

    // 同步坐标并更新碰撞盒
    private void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float w = 0.3F;
        float h = 0.9F;
        this.bb = new AABB(x - w, y - h, z - w, x + w, y + h, z + w);
    }

    // 鼠标视角旋转
    public void turn(float dx, float dy) {
        this.yRot += dx * 0.15F;
        this.xRot -= dy * 0.15F;
        // 俯仰限制 ±90度，防止倒转
        if (this.xRot < -90.0F) xRot = -90.0F;
        if (this.xRot > 90.0F) xRot = 90.0F;
    }

    // 按键事件接收，缓存按键状态
    public void handleKey(int key, int action) {
        boolean press = action == GLFW_PRESS;
        switch (key) {
            case GLFW_KEY_W: keyW = press; break;
            case GLFW_KEY_S: keyS = press; break;
            case GLFW_KEY_A: keyA = press; break;
            case GLFW_KEY_D: keyD = press; break;

            case GLFW_KEY_UP: keyUp = press; break;
            case GLFW_KEY_DOWN: keyDown = press; break;
            case GLFW_KEY_LEFT: keyLeft = press; break;
            case GLFW_KEY_RIGHT: keyRight = press; break;

            case GLFW_KEY_SPACE: keySpace = press; break;
            case GLFW_KEY_R:
                if (press) resetPos();
                break;
        }
    }

    // 每帧玩家逻辑更新（重力、移动、碰撞）
    public void tick() {
        // 保存上帧坐标
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        float xa = 0.0F;
        float za = 0.0F;

        // 前后左右输入叠加
        if (keyW || keyUp) xa--;
        if (keyS || keyDown) xa++;
        if (keyA || keyLeft) za--;
        if (keyD || keyRight) za++;

        // 跳跃判定
        if (keySpace && this.onGround) {
            this.yd = 0.12F;
        }

        // 相对视角移动
        moveRelative(xa, za, this.onGround ? 0.02F : 0.005F);

        // 重力加速度
        this.yd -= 0.005D;

        // 执行碰撞移动
        move(this.xd, this.yd, this.zd);

        // 空气/地面摩擦力
        this.xd *= 0.91F;
        this.zd *= 0.91F;
        if (this.onGround) {
            this.xd *= 0.8F;
            this.zd *= 0.8F;
        }
    }

    // 基于视角的相对移动（方向向量旋转）
    public void moveRelative(float xa, float za, float speed) {
        float dist = xa * xa + za * za;
        if (dist < 0.01F) return;
        dist = speed / (float) Math.sqrt(dist);
        xa *= dist;
        za *= dist;

        float radY = (float) Math.toRadians(yRot);
        float sin = (float) Math.sin(radY);
        float cos = (float) Math.cos(radY);

        this.xd += xa * cos - za * sin;
        this.zd += za * cos + xa * sin;
    }

    // 碰撞移动分步判定（Y→X→Z）
    public void move(float xa, float ya, float za) {
        float xaOrg = xa;
        float yaOrg = ya;
        float zaOrg = za;

        List<AABB> colliders = this.world.getCollisionBox(this.bb.expand(xa, ya, za));

        // Y轴碰撞（重力、落地判定）
        for (AABB box : colliders) {
            ya = box.clipYCollide(this.bb, ya);
        }
        this.bb.move(0.0F, ya, 0.0F);

        // X轴碰撞
        for (AABB box : colliders) {
            xa = box.clipXCollide(this.bb, xa);
        }
        this.bb.move(xa, 0.0F, 0.0F);

        // Z轴碰撞
        for (AABB box : colliders) {
            za = box.clipZCollide(this.bb, za);
        }
        this.bb.move(0.0F, 0.0F, za);

        // 是否落地
        this.onGround = (yaOrg != ya && yaOrg < 0.0F);

        // 撞墙清零对应速度
        if (xaOrg != xa) this.xd = 0.0F;
        if (yaOrg != ya) this.yd = 0.0F;
        if (zaOrg != za) this.zd = 0.0F;

        // 同步玩家坐标到包围盒中心
        this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
        this.y = this.bb.y0 + 1.62F;
        this.z = (this.bb.z0 + this.bb.z1) / 2.0F;
    }
}