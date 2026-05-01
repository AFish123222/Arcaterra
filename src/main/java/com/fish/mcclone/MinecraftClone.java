package com.fish.mcclone;

import com.fish.mcclone.level.Chunk;
import com.fish.mcclone.level.Level;
import com.fish.mcclone.level.LevelRenderer;
import com.fish.util.PrinterUtils;
import com.fish.util.TextureLoader;
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

    private int terrainTexture;

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

        // 加载并绑定方块纹理
        terrainTexture = TextureLoader.loadTexture("terrain.png");
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, terrainTexture);
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
//                    System.out.println(frames + " fps, " + Chunk.updates);
                    System.out.println(frames + " fps, ");
                    System.out.println(player.xRot + " xRot ");
                    System.out.println(player.yRot + " yRot ");
//                    Chunk.updates = 0;
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
        // 性能优化 必开
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);   // 背面剔除，看不见的面直接不渲染
        glCullFace(GL_BACK);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);       // 暂时关雾，省性能

        // 鼠标移动 + Y轴反转
        double currentMouseX, currentMouseY;
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            currentMouseX = x.get(0);
            currentMouseY = y.get(0);
        }
        float xo = (float) (currentMouseX - lastMouseX);
        float yo = -(float) (currentMouseY - lastMouseY);
        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;
        player.turn(xo, yo);

        // 限制俯仰角，防止翻倒
        player.xRot = Math.max(-89, Math.min(89, player.xRot));

        // 3. 清屏 + 恢复正式渲染状态
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 开启关键状态（地形必须）
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);

        // 4. JOML透视投影
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        Matrix4f projMatrix = new Matrix4f();
        projMatrix.perspective((float) Math.toRadians(70.0f), (float) width / height, 0.05f, 1000.0f);
        FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
        projMatrix.get(matBuffer);
        glLoadMatrixf(matBuffer);

        // 5. 完美第一人称相机
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        moveCameraToPlayer(a);

        // 6. 渲染真实Chunk地形
        levelRenderer.render(player, 0);

        // 雾效 + 第二层地形
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glFogf(GL_FOG_START, 30.0f);
        glFogf(GL_FOG_END, 150.0f);
        glFogfv(GL_FOG_COLOR, fogColor);
        levelRenderer.render(player, 1);
        glDisable(GL_FOG);

        // 7. 渲染九宫格测试方块（保留，做参照）
        glDisable(GL_TEXTURE_2D);
        glColor3f(0.3f, 0.7f, 0.2f);
        int[][] grid = {
                {-1, -1}, {0, -1}, {1, -1},
                {-1, 0},  {0, 0},  {1, 0},
                {-1, 1},  {0, 1},  {1, 1}
        };
        float baseX = 30;
        float baseY = 60;
        float baseZ = 50;

        glBegin(GL_QUADS);
        for (int[] off : grid) {
            int ox = off[0];
            int oz = off[1];
            float x0 = baseX + ox;
            float x1 = baseX + ox + 1;
            float y0 = baseY;
            float y1 = baseY + 1;
            float z0 = baseZ + oz;
            float z1 = baseZ + oz + 1;

            glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
            glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
            glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
            glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0); glVertex3f(x0, y1, z0);
            glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
            glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1); glVertex3f(x1, y1, z0);
        }
        glEnd();
        glColor3f(1.0f, 1.0f, 1.0f);

        // 8. 选中框 + 拾取（原样保留）
        if (hitResult != null) {
            glDisable(GL_TEXTURE_2D);
            levelRenderer.renderHit(hitResult);
        }

        // 9. 拾取矩阵（原样保留）
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW); glPushMatrix();
        pick(a);
        glMatrixMode(GL_PROJECTION); glPopMatrix();
        glMatrixMode(GL_MODELVIEW); glPopMatrix();

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    // 以下辅助方法仅替换了 GL 常量，逻辑未变
    /// 旋转 平移
//    private void moveCameraToPlayer(float a) {
//        // 1. 获取玩家插值位置
//        float px = player.xo + (player.x - player.xo) * a;
//        float py = player.yo + (player.y - player.yo) * a;
//        float pz = player.zo + (player.z - player.zo) * a;
//
//        glRotatef(player.xRot, 1, 0, 0);  // 上下抬头/低头
//        glRotatef(player.yRot, 0, 1, 0);  // 左右转头
//        glTranslatef(-px, -py, -pz);      // 平移世界
//    }
    private void moveCameraToPlayer(float a) {
        glLoadIdentity();
        // 先旋转（绕自身相机原地转）
        glRotatef(player.xRot, 1, 0, 0);
        glRotatef(player.yRot, 0, 1, 0);
        // 后平移世界
        float px = player.xo + (player.x - player.xo) * a;
        float py = player.yo + (player.y - player.yo) * a;
        float pz = player.zo + (player.z - player.z) * a;
        glTranslatef(-px, -py, -pz);
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