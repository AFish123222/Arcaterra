package com.fish.arcaterra;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import com.fish.arcaterra.render.Renderer;
import com.fish.arcaterra.phys.AABB;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Arcaterra {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    private boolean running;

    // 游戏核心
    private World world;
    private Player player;
    private int frameTaskStep; // 分片任务调度 0~4循环

    public void run() {
        init();
        loop();
        shutdown();
    }

    private void init() {
        // GLFW 错误回调
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW初始化失败");

        // 窗口配置
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Arcaterra", NULL, NULL);
        if (window == NULL) throw new RuntimeException("窗口创建失败");

        glfwSetKeyCallback(window, this::keyCallback);
        glfwSetCursorPosCallback(window, this::mouseMoveCallback);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // 关闭垂直同步，解除帧率锁定
        glfwShowWindow(window);

        // 绑定OpenGL上下文
        GL.createCapabilities();
        glClearColor(0.4f, 0.7f, 1.0f, 1f);

        // 透视投影只初始化一次，不每帧重复设置
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
        glFrustum(-aspect * 0.1f, aspect * 0.1f, -0.1f, 0.1f, 0.1f, 1000f);
        glMatrixMode(GL_MODELVIEW);

        // 初始化世界与玩家
        world = new World();
        player = new Player(world);
        running = true;
        frameTaskStep = 0;
    }

    private void loop() {
        while (running && !glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // ========== 分片任务调度，分摊单帧压力，杜绝卡死 ==========
            switch (frameTaskStep) {
                case 0:
                    // 输入+玩家物理更新
                    player.tick();
                    break;
                case 1:
                    // 区块加载/延迟卸载
                    world.updateChunks(player.x, player.y, player.z);
                    break;
                case 2:
                    // 单次最多重建2个脏区块网格，避免瞬间大量计算卡死
                    var dirtyList = world.getDirtyChunks();
                    int limit = Math.min(2, dirtyList.size());
                    for (int i = 0; i < limit; i++) {
                        dirtyList.get(i).rebuildMesh();
                    }
                    break;
                case 3:
                    Renderer.INSTANCE.beginWorldRender();
                    for (ChunkPool.ChunkHolder holder : world.getAllChunkHolders()) {
                        Chunk c = holder.chunk;
                        c.render(player.x, player.y, player.z);
                    }
                    Renderer.INSTANCE.endWorldRender();
                    break;
                case 4:
                    // 拾取+选中高亮（按需）
                    // pickLogic();
                    // renderHitOverlay();
                    break;
            }
            frameTaskStep = (frameTaskStep + 1) % 5;

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void shutdown() {
        world.destroyAllChunks();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // 键盘回调
    private void keyCallback(long win, int key, int scan, int action, int mods) {
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            running = false;
        }
        player.handleKey(key, action);
    }

    // 鼠标视角
    private void mouseMoveCallback(long win, double x, double y) {
        float dx = (float) (x - WIDTH / 2.0);
        float dy = (float) (y - HEIGHT / 2.0);
        player.turn(dx, dy);
        // 鼠标居中，持续视角控制
        glfwSetCursorPos(win, WIDTH / 2.0, HEIGHT / 2.0);
    }

    // 对外提供窗口句柄（供Player读取按键）
    public long getWindow() {
        return window;
    }

    public static void main(String[] args) {
        new Arcaterra().run();
    }
}
