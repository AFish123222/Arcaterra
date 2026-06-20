package com.fish.arcaterra;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.phys.AABB;
import com.fish.arcaterra.phys.BlockHit;

import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

public class Player {
    private final World world;

    // 坐标（y 为脚底位置）
    public float x, y, z;
    public float xo, yo, zo;
    public float xd, yd, zd;
    public float yRot, xRot;
    public AABB bb;
    public boolean onGround = false;
    public boolean flyable = true;

    // 玩家尺寸（半宽0.3，全高1.8）
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;

    private boolean keyW, keyS, keyA, keyD;
    private boolean keyUp, keyDown, keyLeft, keyRight;
    private boolean jumpPressed;

    public Player(World world) {
        this.world = world;
        this.xRot = 0;
        this.yRot = 0;
        setPos(30, 10, 50); // 脚底 y=10
    }

    // 设置位置（y 为脚底）
    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float halfWidth = PLAYER_WIDTH / 2f;
        this.bb = new AABB(
                x - halfWidth, y,
                z - halfWidth,
                x + halfWidth, y + PLAYER_HEIGHT,
                z + halfWidth
        );
    }

    public void turn(float dx, float dy) {
        this.yRot += dx * 0.15F;
        this.xRot -= dy * 0.15F;
        if (this.xRot < -90.0F) xRot = -90.0F;
        if (this.xRot > 90.0F) xRot = 90.0F;
    }

    public void handleKey(int key, int action) {
        boolean press = (action == GLFW_PRESS);
        boolean release = (action == GLFW_RELEASE);
        switch (key) {
            case GLFW_KEY_W: keyW = press; break;
            case GLFW_KEY_S: keyS = press; break;
            case GLFW_KEY_A: keyA = press; break;
            case GLFW_KEY_D: keyD = press; break;
            case GLFW_KEY_UP: keyUp = press; break;
            case GLFW_KEY_DOWN: keyDown = press; break;
            case GLFW_KEY_LEFT: keyLeft = press; break;
            case GLFW_KEY_RIGHT: keyRight = press; break;
            case GLFW_KEY_SPACE:
                if (press) jumpPressed = true;
                else if (release) jumpPressed = false;
                break;
        }
    }

    public void tick() {
        this.xo = x; this.yo = y; this.zo = z;

        float forward = 0, right = 0;
        if (keyW || keyUp) forward -= 1f;
        if (keyS || keyDown) forward += 1f;
        if (keyD || keyRight) right += 1f;
        if (keyA || keyLeft) right -= 1f;

        // 跳跃（仅按下瞬间）
        if (jumpPressed && (onGround || flyable)) {
            yd = 0.12F;
            jumpPressed = false;
        }

        float radY = (float) Math.toRadians(yRot);
        float cos = (float) Math.cos(radY);
        float sin = (float) Math.sin(radY);
        float worldX = right * cos + forward * sin;
        float worldZ = -right * sin + forward * cos;

        moveRelative(worldX, worldZ, onGround ? 0.02F : 0.005F);
        yd -= 0.005F;
        move(xd, yd, zd);

        xd *= 0.91F;
        yd *= 0.98F;
        zd *= 0.91F;
        if (onGround) {
            xd *= 0.8F;
            zd *= 0.8F;
        }
    }

    private void moveRelative(float dx, float dz, float speed) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return;
        dx = dx / len * speed;
        dz = dz / len * speed;
        xd += dx;
        zd += dz;
    }

    public void move(float xa, float ya, float za) {
        float xaOrg = xa, yaOrg = ya, zaOrg = za;
        // 用扩展后的包围盒获取碰撞体
        List<AABB> colliders = world.getCollisionBox(this.bb.expand(xa, ya, za));

        // Y轴
        for (AABB box : colliders) ya = box.clipYCollide(this.bb, ya);
        this.bb.move(0, ya, 0);
        // X轴
        for (AABB box : colliders) xa = box.clipXCollide(this.bb, xa);
        this.bb.move(xa, 0, 0);
        // Z轴
        for (AABB box : colliders) za = box.clipZCollide(this.bb, za);
        this.bb.move(0, 0, za);

        this.onGround = (yaOrg != ya && yaOrg < 0.0F);
        if (xaOrg != xa) this.xd = 0.0F;
        if (yaOrg != ya) this.yd = 0.0F;
        if (zaOrg != za) this.zd = 0.0F;

        // 同步脚底坐标（bb.y0 即脚底）
        this.x = (bb.x0 + bb.x1) / 2.0F;
        this.y = bb.y0;
        this.z = (bb.z0 + bb.z1) / 2.0F;
    }

    // 射线检测（起点在眼睛位置）
    public BlockHit raycast(float maxDist) {
        float pitch = (float) Math.toRadians(xRot);
        float yaw = (float) Math.toRadians(yRot);
        float dx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float dy = (float) (-Math.sin(pitch));
        float dz = (float) (Math.cos(pitch) * Math.cos(yaw));

        float px = this.x;
        float py = this.y + EYE_HEIGHT;  // 眼睛高度
        float pz = this.z;

        float stepX = (dx > 0) ? 1 : -1;
        float stepY = (dy > 0) ? 1 : -1;
        float stepZ = (dz > 0) ? 1 : -1;
        float tMaxX = (dx != 0) ? (stepX > 0 ? ((int)px + 1 - px) / dx : (px - (int)px) / -dx) : Float.MAX_VALUE;
        float tMaxY = (dy != 0) ? (stepY > 0 ? ((int)py + 1 - py) / dy : (py - (int)py) / -dy) : Float.MAX_VALUE;
        float tMaxZ = (dz != 0) ? (stepZ > 0 ? ((int)pz + 1 - pz) / dz : (pz - (int)pz) / -dz) : Float.MAX_VALUE;
        float tDeltaX = (dx != 0) ? 1.0f / Math.abs(dx) : Float.MAX_VALUE;
        float tDeltaY = (dy != 0) ? 1.0f / Math.abs(dy) : Float.MAX_VALUE;
        float tDeltaZ = (dz != 0) ? 1.0f / Math.abs(dz) : Float.MAX_VALUE;

        int x = (int) Math.floor(px);
        int y = (int) Math.floor(py);
        int z = (int) Math.floor(pz);
        int nx = 0, ny = 0, nz = 0;

        float t = 0;
        while (t < maxDist) {
            if (world.getBlock(x, y, z) != 0) {
                return new BlockHit(x, y, z, nx, ny, nz);
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += (int) stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    nx = (int) -stepX; ny = 0; nz = 0;
                } else {
                    z += (int) stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    nx = 0; ny = 0; nz = (int) -stepZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += (int) stepY;
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    nx = 0; ny = (int) -stepY; nz = 0;
                } else {
                    z += (int) stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    nx = 0; ny = 0; nz = (int) -stepZ;
                }
            }
        }
        return null;
    }
}