package com.fish.arcaterra;

import com.fish.arcaterra.debug.DebugWindow;
import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import com.fish.arcaterra.level.ChunkPool;
import com.fish.arcaterra.phys.BlockHit;
import com.fish.arcaterra.render.Renderer;
import com.fish.arcaterra.terrarium.DemTerrainProvider;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.swing.*;
import java.util.List;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Arcaterra {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    private boolean running;
    private boolean mouseCaptured = true;

    private long lastDebugUpdate = System.currentTimeMillis();
    private int fpsCounter = 0;
    private int currentFps = 0;

    private World world;
    private Player player;
    private int frameTaskStep;

    public void run() {
        init();
        loop();
        shutdown();
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        mouseCaptured = true;
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW初始化失败");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Arcaterra", NULL, NULL);
        if (window == NULL) throw new RuntimeException("窗口创建失败");

        glfwSetKeyCallback(window, this::keyCallback);
        glfwSetCursorPosCallback(window, this::mouseMoveCallback);
        glfwSetMouseButtonCallback(window, this::mouseButtonCallback);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        glfwShowWindow(window);

        GL.createCapabilities();
        glClearColor(0.4f, 0.7f, 1.0f, 1f);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
        glFrustum(-aspect * 0.1f, aspect * 0.1f, -0.1f, 0.1f, 0.1f, 2000f);
        glMatrixMode(GL_MODELVIEW);



        // 使用噪声地形（默认）
         world = new World();

//        // 使用 DEM（如果文件存在）
//        try {
//            DemTerrainProvider dem = new DemTerrainProvider(
//                    "path/to/your/dem.tif",   // 替换为实际文件路径
//                    -1000, 1000,              // minX, maxX（根据你的DEM实际范围修改）
//                    -1000, 1000               // minZ, maxZ
//            );
//            world = new World(dem);
//        } catch (Exception e) {
//            e.printStackTrace();
//            world = new World(); // fallback 到噪声
//        }

        float spawnX = 30;
        float spawnZ = 50;
        float groundHeight = world.getTerrainProvider().getHeight(spawnX, spawnZ);
        float spawnY = groundHeight + 1.5f; // 站在地面以上

        player = new Player(world);
        player.setPos(spawnX, spawnY, spawnZ);

        // 加载并重建周围区块
        world.updateChunks(player.x, player.y, player.z);
        for (Chunk c : world.getDirtyChunks()) {
            c.rebuildMesh();
        }

        // 初始化调试窗口（但不显示）
        initDebugWindow();
        System.out.println("press F3 to debug");

        running = true;
        frameTaskStep = 0;
    }

    private void initDebugWindow() {
        SwingUtilities.invokeLater(() -> {
            DebugWindow.getInstance().setStatus("初始化完成");
        });
    }

    private int frameCounter = 0;

    private void loop() {
        while (running && !glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            switch (frameTaskStep) {
                case 0:
                    player.tick();
                    break;
                case 1:
                    // 关键：更新区块（加载/卸载）
                    world.updateChunks(player.x, player.y, player.z);
                    break;
                case 2:
                    rebuildSomeDirtyChunks(16);
                    break;
            }
            frameTaskStep = (frameTaskStep + 1) % 3;

            // 渲染每帧都执行
            Renderer.INSTANCE.beginWorldRender();
            glRotatef(-player.xRot, 1, 0, 0);
            glRotatef(-player.yRot, 0, 1, 0);
            glTranslatef(-player.x, -player.y, -player.z);

            //lod渲染
//            world.getLodManager().render(player.x, player.y, player.z);
            //全量渲染
            for (ChunkPool.ChunkHolder holder : world.getVisibleChunkHolders()) {
                Chunk c = holder.chunk;
                c.render(player.x, player.y, player.z);
            }

            Renderer.INSTANCE.endWorldRender();

            glfwSwapBuffers(window);

            debugUpdate();

            glfwPollEvents();
        }
    }

    private void debugUpdate() {
        // 更新 FPS 计数
        fpsCounter++;
        long now = System.currentTimeMillis();
        if (now - lastDebugUpdate >= 1000) {
            currentFps = fpsCounter;
            fpsCounter = 0;
            lastDebugUpdate = now;

            // 获取调试数据
            float height = world.getTerrainProvider().getHeight(player.x, player.z);
            int chunkCount = world.getAllChunkHolders().size();
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // 更新调试窗口
            DebugWindow.getInstance().updateInfo(
                    player.x, player.y, player.z,
                    currentFps,
                    chunkCount,
                    height,
                    usedMemory
            );
        }
    }

    private void rebuildSomeDirtyChunks(int max) {
        List<Chunk> dirty = world.getDirtyChunks();
        int limit = Math.min(max, dirty.size());
        for (int i = 0; i < limit; i++) {
            dirty.get(i).rebuildMesh();
        }
    }

    private void shutdown() {
        world.destroyAllChunks();
        Renderer.INSTANCE.destroyCubeMesh();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void keyCallback(long win, int key, int scan, int action, int mods) {
        //ESC退出
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            running = false;
        }
        //ALT释放鼠标
        if (key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT) {
            if (action == GLFW_PRESS) {
                mouseCaptured = !mouseCaptured;
                glfwSetInputMode(window, GLFW_CURSOR, mouseCaptured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                if (!mouseCaptured) {
                    glfwSetCursorPos(window, WIDTH/2.0, HEIGHT/2.0);
                }
            }
            return; // 不传递给 player
        }
        //F3呼出debug窗口
        if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
            DebugWindow.getInstance().toggleVisibility();
        }
        player.handleKey(key, action);
    }

    private void mouseMoveCallback(long win, double x, double y) {
        if (!mouseCaptured) return;//释放鼠标
        float dx = (float) (x - WIDTH / 2.0);
        float dy = (float) (y - HEIGHT / 2.0);
        player.turn(-dx, dy);
        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
            glfwSetCursorPos(win, WIDTH / 2.0, HEIGHT / 2.0);
        }
    }

    private void mouseButtonCallback(long win, int button, int action, int mods) {
        if (action != GLFW_PRESS) return;
        BlockHit hit = player.raycast(5.0f);
        if (hit == null) return;

        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            world.setBlock(hit.x, hit.y, hit.z, (short) 0);
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            int nx = hit.x + hit.nx;
            int ny = hit.y + hit.ny;
            int nz = hit.z + hit.nz;
            world.setBlock(nx, ny, nz, (short) 1);
        }
    }

    public static void main(String[] args) {
        new Arcaterra().run();
    }
}