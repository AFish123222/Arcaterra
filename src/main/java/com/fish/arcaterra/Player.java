package com.fish.arcaterra;

import com.fish.arcaterra.debug.IDebugWindowPrintRegistry;
import com.fish.arcaterra.debug.DebugRegistry;
import com.fish.arcaterra.level.World;
import com.fish.arcaterra.phys.AABB;
import com.fish.arcaterra.phys.BlockHit;


import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

public class Player implements IDebugWindowPrintRegistry {
    private final World world;
    private static final float FOOT_OFFSET = 0.02f;

    // ===== 移动参数（可调字段）=====
    public float walkSpeed = 0.1f;          // 地面移动速度
    public float airSpeed = 0.1f;          // 空中移动速度
    public float jumpSpeed = 0.1f;          // 跳跃速度
    public float gravity = 0.01f;           // 重力加速度
    public float frictionXZ = 0.91f;         // 水平阻尼（每帧）
    public float frictionY = 0.98f;          // 垂直阻尼
    public float groundFriction = 0.0f;      // 地面额外阻尼
    public float speedMultiplier = 60f;      // 帧率补偿倍数（用于 delta）

    // 坐标
    public float x, y, z;
    public float xo, yo, zo;
    public float xd, yd, zd;
    public float yRot, xRot;
    public AABB bb;
    public boolean onGround = false;
    public boolean flyable = true;

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
        setPos(0, 0, world.getTerrainProvider().getHeight(0,0));
        debugParamRegister(); // 自动注册调试信息
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float halfWidth = PLAYER_WIDTH / 2f;
        this.bb = new AABB(
                x - halfWidth,
                y + FOOT_OFFSET,
                z - halfWidth,
                x + halfWidth,
                y + PLAYER_HEIGHT + FOOT_OFFSET,
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
        boolean press = (action == GLFW_PRESS || action == GLFW_REPEAT);
        boolean release = (action == GLFW_RELEASE);
//        System.out.println("jumpPressed: " + jumpPressed + ", onGround: " + onGround + ", y: " + y);
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
//        System.out.println("jumpPressed: " + jumpPressed + ", onGround: " + onGround + ", y: " + y);
    }

    public void tick(float delta) {
        this.xo = x; this.yo = y; this.zo = z;

        float forward = 0, right = 0;
        if (keyW || keyUp) forward -= 1f;
        if (keyS || keyDown) forward += 1f;
        if (keyD || keyRight) right += 1f;
        if (keyA || keyLeft) right -= 1f;

        if (jumpPressed && (onGround || flyable)) {
            yd = jumpSpeed * delta * speedMultiplier;
//            jumpPressed = false;
        }
        if (this.y < -64) {
            float groundY = world.getTerrainProvider().getHeight(x, z);
            setPos(x, groundY + 1.5f, z);
            yd = 0;
        }

        float radY = (float) Math.toRadians(yRot);
        float cos = (float) Math.cos(radY);
        float sin = (float) Math.sin(radY);
        float worldX = right * cos + forward * sin;
        float worldZ = -right * sin + forward * cos;

        float speed = (onGround ? walkSpeed : airSpeed) * delta * speedMultiplier;
        moveRelative(worldX, worldZ, speed);
        yd -= gravity * delta * speedMultiplier;
        move(xd, yd, zd);

        xd *= frictionXZ;
        yd *= frictionY;
        zd *= frictionXZ;
        if (onGround) {
            xd *= groundFriction;
            zd *= groundFriction;
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
        List<AABB> colliders = world.getCollisionBox(this.bb.expand(xa, ya, za));

        for (AABB box : colliders) ya = box.clipYCollide(this.bb, ya);
        this.bb.move(0, ya, 0);

        for (AABB box : colliders) xa = box.clipXCollide(this.bb, xa);
        this.bb.move(xa, 0, 0);

        for (AABB box : colliders) za = box.clipZCollide(this.bb, za);
        this.bb.move(0, 0, za);

        this.onGround = ((int)yaOrg != (int)ya && (int)yaOrg <= 0);

        if (xaOrg != xa && Math.abs(xa) < 0.0001f) this.xd = 0.0F;
        if (yaOrg != ya && Math.abs(ya) < 0.0001f) this.yd = 0.0F;
        if (zaOrg != za && Math.abs(za) < 0.0001f) this.zd = 0.0F;

        this.x = (bb.x0 + bb.x1) * 0.5f;
        this.y = bb.y0 + EYE_HEIGHT;
        this.z = (bb.z0 + bb.z1) * 0.5f;
    }

    public BlockHit raycast(float maxDist) {
        float pitch = (float) Math.toRadians(xRot);
        float yaw = (float) Math.toRadians(yRot);
        float dy = (float) (Math.sin(pitch));
        float dx = (float) (-Math.cos(pitch) * Math.sin(yaw));
        float dz = (float) (-Math.cos(pitch) * Math.cos(yaw));
        float px = this.x;
        float py = this.y;
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
            short blockId = world.getBlock(x, y, z);
//            Arcaterra.particlePool.spawn(x,y,z,0,0,0,20,0.1f,1f,0,0,0.5f, false);
            if (blockId != 0) {
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

    @Override
    public void debugParamRegister() {
        DebugRegistry.register("Player", "X", () -> x);
        DebugRegistry.register("Player", "Y", () -> y);
        DebugRegistry.register("Player", "Z", () -> z);
        DebugRegistry.register("Player", "XD", () -> xd);
        DebugRegistry.register("Player", "YD", () -> yd);
        DebugRegistry.register("Player", "ZD", () -> zd);
        DebugRegistry.register("Player", "OnGround", () -> onGround);
        DebugRegistry.register("Player", "YRot", () -> yRot);
        DebugRegistry.register("Player", "XRot", () -> xRot);
        DebugRegistry.register("Player", "WalkSpeed", () -> walkSpeed);
        DebugRegistry.register("Player", "AirSpeed", () -> airSpeed);
        DebugRegistry.register("Player", "JumpSpeed", () -> jumpSpeed);
        DebugRegistry.register("Player", "Gravity", () -> gravity);
        DebugRegistry.register("Player", "FrictionXZ", () -> frictionXZ);
        DebugRegistry.register("Player", "FrictionY", () -> frictionY);
        DebugRegistry.register("Player", "GroundFriction", () -> groundFriction);
        DebugRegistry.register("Player", "SpeedMultiplier", () -> speedMultiplier);
    }
}