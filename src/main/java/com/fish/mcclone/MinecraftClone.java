package com.fish.mcclone;

import com.fish.mcclone.level.Chunk;
import com.fish.mcclone.level.Level;
import com.fish.mcclone.level.LevelRenderer;
import com.fish.util.PrinterUtils;
import org.joml.Matrix4f;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.opengl.GLUtil.*;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import javax.swing.JOptionPane;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/// # MinecraftClone
/// 这是JourneyToBaoji的前置项目。本阶段会重新实现并优化Minecraft1.21.5+Fabric+Terrarium+LOD(DH/Voxy/自研文件树LOD)
///
/// 作者会同步学习游戏开发知识 并写好JavaDoc
public class MinecraftClone {
    /// ###### 窗口宽度 width
    private int width = 1024;
    /// ###### 窗口高度 height
    private int height = 768;
    /// ###### GLFW窗口句柄 window
    private static long window; // GLFW 窗口句柄

    private FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);
    private Timer timer = new Timer(60.0F);
    private Level level;
    private LevelRenderer levelRenderer;
    private Player player;

    /// 鼠标位置跟踪（用于计算 dx/dy）
    private double lastMouseX, lastMouseY;
    /// 事件队列（模拟 LWJGL 2 的事件轮询）
    private final Queue<int[]> keyEvents = new LinkedList<>();
    private final Queue<int[]> mouseButtonEvents = new LinkedList<>();

    private IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult = null;

    public static long getWindow() {
        return window;
    }

    public void init() throws IOException {
        // 1. 初始化 GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        // 2. 配置窗口属性
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // 3. 创建窗口
        window = glfwCreateWindow(width, height, "RubyDung", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // 4. 设置事件回调
        setupCallbacks();

        // 5. 窗口居中
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // 6. 初始化 OpenGL 上下文
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // 启用 V-Sync
        glfwShowWindow(window);
        GL.createCapabilities();

        // 7. 初始化鼠标（抓取模式 + 初始位置）
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            lastMouseX = x.get(0);
            lastMouseY = y.get(0);
        }

        // 8. 原有游戏逻辑初始化
        int col = 920330;
        float fr = 0.5F, fg = 0.8F, fb = 1.0F;
        fogColor.put(new float[]{(col >> 16 & 0xFF) / 255.0F, (col >> 8 & 0xFF) / 255.0F, (col & 0xFF) / 255.0F, 1.0F}).flip();

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(fr, fg, fb, 0.0F);
        glClearDepth(1.0D);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);

        level = new Level(256, 256, 64);
        levelRenderer = new LevelRenderer(level);
        player = new Player(level);
    }

    private void setupCallbacks() {
        // 键盘事件回调（存入队列）
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                keyEvents.add(new int[]{key, action});
            }
        });

        // 鼠标按钮事件回调（存入队列）
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                mouseButtonEvents.add(new int[]{button, action});
            }
        });
    }

    public void destroy() {
        level.save();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public void run() {
        try {
            init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.toString(), "Failed to start RubyDung", 0);
            System.exit(0);
        }

        long lastTime = System.currentTimeMillis();
        int frames = 0;

        try {
            // 主循环（退出条件：窗口关闭或 ESC 按下）
            while (!glfwWindowShouldClose(window) && glfwGetKey(window, GLFW_KEY_ESCAPE) != GLFW_PRESS) {
                timer.advanceTime();
                for (int i = 0; i < timer.ticks; i++) tick();
                render(timer.a);

                // FPS 计数
                frames++;
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    System.out.println(frames + " fps, " + Chunk.updates);
                    Chunk.updates = 0;
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    public void tick() {
        player.tick();
    }

    /// 帧渲染
    public void render(float a) {
        // ==========================================
        // 第一阶段：输入处理（不碰任何 OpenGL 渲染状态）
        // ==========================================

        // 1. 计算鼠标移动 delta
        double currentMouseX, currentMouseY;
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            currentMouseX = x.get(0);
            currentMouseY = y.get(0);
        }
        float xo = (float) (currentMouseX - lastMouseX);
        float yo = (float) (currentMouseY - lastMouseY);
        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;
        player.turn(xo, yo);

        // 2. 处理鼠标按钮事件
        while (!mouseButtonEvents.isEmpty()) {
            int[] event = mouseButtonEvents.poll();
            int button = event[0];
            boolean pressed = event[1] == GLFW_PRESS;

            if (button == GLFW_MOUSE_BUTTON_2 && pressed) {
                if (hitResult != null) level.setTile(hitResult.x, hitResult.y, hitResult.z, 0);
            }
            if (button == GLFW_MOUSE_BUTTON_1 && pressed) {
                if (hitResult != null) {
                    int x = hitResult.x, y = hitResult.y, z = hitResult.z;
                    if (hitResult.f == 0) y--;
                    if (hitResult.f == 1) y++;
                    if (hitResult.f == 2) z--;
                    if (hitResult.f == 3) z++;
                    if (hitResult.f == 4) x--;
                    if (hitResult.f == 5) x++;
                    level.setTile(x, y, z, 1);
                }
            }
        }

        // 3. 处理键盘事件
        while (!keyEvents.isEmpty()) {
            int[] event = keyEvents.poll();
            int key = event[0];
            boolean pressed = event[1] == GLFW_PRESS;
            if (key == GLFW_KEY_ENTER && pressed) level.save();
        }

        // ==========================================
        // 第二阶段：拾取（用矩阵栈保护状态）
        // ==========================================

        // 【关键】用 glPushMatrix/glPopMatrix 保存原矩阵，pick 完自动恢复
        glMatrixMode(GL_PROJECTION);
        glPushMatrix(); // 保存当前投影矩阵
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix(); // 保存当前模型视图矩阵

//        pick(a); // 执行拾取

        // 【关键】恢复矩阵（比 glLoadIdentity() 更安全）
        glMatrixMode(GL_PROJECTION);
        glPopMatrix(); // 恢复 pick 前的投影矩阵
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix(); // 恢复 pick 前的模型视图矩阵

        // ==========================================
        // 第三阶段：正式渲染（状态完全可控）
        // ==========================================

        // 1. 清屏 + 强制重置基础状态
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
//        glEnable(GL_CULL_FACE);
//        glCullFace(GL_BACK); // 明确剔除背面
//        glDisable(GL_CULL_FACE);//为修复bug-1,先看看是否搞反正反面
        // ========== 【强制全局状态重置，必须加】 ==========
// 深度测试：必须开，否则方块会被天空覆盖
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDepthMask(true); // 允许写入深度缓冲

// 面剔除：先关掉，避免正反面搞反了看不到
        glDisable(GL_CULL_FACE);

// 雾效：强制关掉，避免把方块染成天空色
        glDisable(GL_FOG);

// 纹理：先关掉，测试用纯色
        glDisable(GL_TEXTURE_2D);

// 渲染模式：强制填充，不是线框/点
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

// 颜色：强制白色，避免被之前的颜色污染
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
// ========================================================

        // 2. 设置摄像机
        setupCamera(a);

        // ========== 【新增：测试方块代码，直接复制】 ==========
        glDisable(GL_TEXTURE_2D); // 关掉纹理，用纯色
        glDisable(GL_FOG);        // 关掉雾效
        glColor3f(1.0f, 0.0f, 0.0f); // 强制红色，和蓝色天空强对比

    // 画一个在摄像机正前方5格，1x1x1的方块
        glBegin(GL_QUADS);
    // 前面（朝向摄像机）
        glVertex3f(-0.5f, -0.5f, -5.0f);
        glVertex3f( 0.5f, -0.5f, -5.0f);
        glVertex3f( 0.5f,  0.5f, -5.0f);
        glVertex3f(-0.5f,  0.5f, -5.0f);
        glEnd();

        glColor3f(1.0f, 1.0f, 1.0f); // 恢复白色
    // ========================================================





        // 3. 渲染地形（分两层：无雾/有雾，对应原代码逻辑）
        // 第一层：无雾（通常是天空或远处？）
        glDisable(GL_FOG);
        levelRenderer.render(player, 0);
        levelRenderer.render(player, 1);

        // 第二层：有雾（通常是近处地形）
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glFogf(GL_FOG_START, 0.0F); // 明确雾效起始距离
        glFogf(GL_FOG_END, 100.0F); // 明确雾效结束距离
        glFogfv(GL_FOG_COLOR, fogColor);
        levelRenderer.render(player, 1);

        // 4. 渲染选中框（不需要纹理和雾）
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        if (hitResult != null) {
            levelRenderer.renderHit(hitResult);
        }

        // ==========================================
        // 第四阶段：结束帧
        // ==========================================
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    // 以下辅助方法仅替换了 GL 常量，逻辑未变
    private void moveCameraToPlayer(float a) {
        glTranslatef(0.0F, 0.0F, -0.3F);
        glRotatef(player.xRot, 1.0F, 0.0F, 0.0F);
        glRotatef(player.yRot, 0.0F, 1.0F, 0.0F);
        float x = player.xo + (player.x - player.xo) * a;
        float y = player.yo + (player.y - player.yo) * a;
        float z = player.zo + (player.z - player.zo) * a;
        glTranslatef(-x, -y, -z);
    }

    private void setupCamera(float a) {
//        glMatrixMode(GL_PROJECTION);
//        glLoadIdentity();
//        GLU.gluPerspective(70.0F, (float) width / height, 0.05F, 1000.0F);
//        glMatrixMode(GL_MODELVIEW);
//        glLoadIdentity();
//        moveCameraToPlayer(a);
        // 1. 用 JOML 创建透视投影矩阵
        Matrix4f perspectiveMatrix = new Matrix4f();
        perspectiveMatrix.perspective(
                (float) Math.toRadians(70.0), // 视场角（原 gluPerspective 是角度，需转弧度）
                (float) width / height,        // 宽高比（与原参数一致）
                0.05f,                         // 近裁剪面（与原参数一致）
                1000.0f                        // 远裁剪面（与原参数一致）
        );

        // 2. 将 JOML 矩阵转换为 OpenGL 可用的 FloatBuffer
        FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
        perspectiveMatrix.get(matrixBuffer); // JOML 矩阵是列主序，与 OpenGL 完全兼容

        // 3. 加载到 OpenGL 投影矩阵栈
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(matrixBuffer);

        // 4. 后续模型视图矩阵设置（原逻辑完全不变）
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        moveCameraToPlayer(a);
    }

    private void setupPickCamera(float a, int x, int y) {
//        // 1. 读取视口参数（从 viewportBuffer 中获取 x, y, width, height）
//        viewportBuffer.clear();
//        glGetIntegerv(GL_VIEWPORT, viewportBuffer);
//        viewportBuffer.flip();
//        int vpX = viewportBuffer.get();
//        int vpY = viewportBuffer.get();
//        int vpWidth = viewportBuffer.get();
//        int vpHeight = viewportBuffer.get();
//
//        // 2. 用 JOML 实现 gluPickMatrix 逻辑
//        Matrix4f pickMatrix = new Matrix4f();
//        // 平移：将拾取区域中心移到视口中心
//        pickMatrix.translate(
//                (vpWidth - 2.0f * (x - vpX)) / vpWidth,
//                (vpHeight - 2.0f * (y - vpY)) / vpHeight,
//                0.0f
//        );
//        // 缩放：将拾取区域（5x5像素）缩放到整个视口大小
//        pickMatrix.scale(
//                vpWidth / 5.0f,
//                vpHeight / 5.0f,
//                1.0f
//        );
//
//        // 3. 用 JOML 实现 gluPerspective 逻辑（注意：JOML 用弧度）
//        Matrix4f perspectiveMatrix = new Matrix4f();
//        perspectiveMatrix.perspective(
//                (float) Math.toRadians(70.0), // 视场角（角度转弧度）
//                (float) width / height,        // 宽高比
//                0.05f,                         // 近裁剪面
//                1000.0f                        // 远裁剪面
//        );
//
//        // 4. 合并矩阵：Perspective * Pick（对应原 GLU 调用顺序）
//        Matrix4f projMatrix = new Matrix4f();
//        projMatrix.set(perspectiveMatrix).mul(pickMatrix);
//
//        // 5. 将最终矩阵加载到 OpenGL 投影矩阵栈
//        FloatBuffer fb = BufferUtils.createFloatBuffer(16);
//        projMatrix.get(fb); // JOML 矩阵是列主序，与 OpenGL 兼容
//        glMatrixMode(GL_PROJECTION);
//        glLoadMatrixf(fb);
//
//        // 6. 后续模型视图矩阵设置（原逻辑不变）
//        glMatrixMode(GL_MODELVIEW);
//        glLoadIdentity();
//        moveCameraToPlayer(a);
        // 1. 使用 MemoryStack 临时分配视口缓冲区（线程安全，自动释放）
        try (MemoryStack stack = stackPush()) {
            IntBuffer viewportBuffer = stack.mallocInt(4); // GL_VIEWPORT 固定 4 个 int

            // 2. 读取视口参数（简化流程，无状态残留）
            glGetIntegerv(GL_VIEWPORT, viewportBuffer);
            int vpX = viewportBuffer.get(0); // 直接用索引读，无需移动 position
            int vpY = viewportBuffer.get(1);
            int vpWidth = viewportBuffer.get(2);
            int vpHeight = viewportBuffer.get(3);

            // 3. JOML 实现 gluPickMatrix（逻辑不变）
            Matrix4f pickMatrix = new Matrix4f();
            pickMatrix.translate(
                    (vpWidth - 2.0f * (x - vpX)) / vpWidth,
                    (vpHeight - 2.0f * (y - vpY)) / vpHeight,
                    0.0f
            );
            pickMatrix.scale(vpWidth / 5.0f, vpHeight / 5.0f, 1.0f);

            // 4. JOML 实现 gluPerspective（逻辑不变）
            Matrix4f perspectiveMatrix = new Matrix4f();
            perspectiveMatrix.perspective(
                    (float) Math.toRadians(70.0),
                    (float) width / height,
                    0.05f,
                    1000.0f
            );

            // 5. 合并矩阵并加载到 OpenGL（逻辑不变）
            Matrix4f projMatrix = new Matrix4f();
            projMatrix.set(perspectiveMatrix).mul(pickMatrix);

            FloatBuffer fb = BufferUtils.createFloatBuffer(16);
            projMatrix.get(fb);
            glMatrixMode(GL_PROJECTION);
            glLoadMatrixf(fb);

            // 6. 后续模型视图矩阵设置（原逻辑不变）
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            moveCameraToPlayer(a);
        }
    }

    private void pick(float a) {
        selectBuffer.clear();
        glSelectBuffer(selectBuffer);
        glRenderMode(GL_SELECT);
        setupPickCamera(a, width / 2, height / 2);
        levelRenderer.pick(player);
        int hits = glRenderMode(GL_RENDER);
        selectBuffer.flip();
        selectBuffer.limit(selectBuffer.capacity());
        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;
        for (int i = 0; i < hits; i++) {
            int nameCount = selectBuffer.get();
            long minZ = selectBuffer.get();
            selectBuffer.get();
            long dist = minZ;
            if (dist < closest || i == 0) {
                closest = dist;
                hitNameCount = nameCount;
                for (int j = 0; j < nameCount; j++) names[j] = selectBuffer.get();
            } else {
                for (int j = 0; j < nameCount; j++) selectBuffer.get();
            }
        }
        hitResult = hitNameCount > 0 ? new HitResult(names[0], names[1], names[2], names[3], names[4]) : null;
    }

    public static void checkError() {
        int e = glGetError();
        if (e != 0) throw new IllegalStateException(getGLErrorString(e));
    }

    // OpenGL 错误码映射表（替代 GLU.gluErrorString）
    private static final Map<Integer, String> GL_ERROR_MESSAGES;
    static {
        Map<Integer, String> errors = new HashMap<>();
        errors.put(GL_NO_ERROR, "No error");
        errors.put(GL_INVALID_ENUM, "Invalid enum parameter");
        errors.put(GL_INVALID_VALUE, "Invalid value parameter");
        errors.put(GL_INVALID_OPERATION, "Invalid operation");
        errors.put(GL_STACK_OVERFLOW, "Stack overflow");
        errors.put(GL_STACK_UNDERFLOW, "Stack underflow");
        errors.put(GL_OUT_OF_MEMORY, "Out of memory");
        GL_ERROR_MESSAGES = Collections.unmodifiableMap(errors);
    }
    private static String getGLErrorString(int errorCode) {
        return GL_ERROR_MESSAGES.getOrDefault(
                errorCode,
                "Unknown OpenGL error (code: 0x" + Integer.toHexString(errorCode) + ")"
        );
    }

    static void main() {
        new MinecraftClone().run();
    }

}