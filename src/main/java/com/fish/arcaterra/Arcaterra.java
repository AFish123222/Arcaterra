package com.fish.arcaterra;

import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import com.fish.arcaterra.level.ChunkPool;
import com.fish.arcaterra.render.Renderer;
import com.fish.arcaterra.phys.AABB;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static java.lang.IO.print;
import static java.lang.IO.println;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Arcaterra {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    private boolean running;

    private World world;
    private Player player;
    private int frameTaskStep;

    public void run() {
        init();
        loop();
        shutdown();
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
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        glfwShowWindow(window);

        GL.createCapabilities();
        glClearColor(0.4f, 0.7f, 1.0f, 1f);
        glEnable(GL_CULL_FACE);

        // 修复：加大远裁剪距离，地面不会被裁掉
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
        glFrustum(-aspect * 0.1f, aspect * 0.1f, -0.1f, 0.1f, 0.1f, 2000f);
        glMatrixMode(GL_MODELVIEW);

        world = new World();
        player = new Player(world);
        // 修复：降低出生高度，直接踩在地面上方
        player.x = 30;
        player.y = 68;
        player.z = 50;
        player.setPos(30,68,50);

        running = true;
        frameTaskStep = 0;
    }

    private void loop() {
        while (running && !glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);



            switch (frameTaskStep) {
                case 0:
                    player.tick();
                    break;
                case 1:
                    world.updateChunks(player.x, player.y, player.z);
                    break;
                case 2:
                    var dirtyList = world.getDirtyChunks();
                    for (Chunk c : dirtyList) {
                        c.rebuildMesh();
                    }
                    break;
                case 3:
                    Renderer.INSTANCE.beginWorldRender();
                    // 修复：渲染前推入玩家相机矩阵
                    glRotatef(-player.xRot, 1, 0, 0);
                    glRotatef(-player.yRot, 0, 1, 0);
                    glTranslatef(-player.x, -player.y, -player.z);

                    for (ChunkPool.ChunkHolder holder : world.getAllChunkHolders()) {
                        Chunk c = holder.chunk;
                        c.render(player.x, player.y, player.z);
                    }
                    Renderer.INSTANCE.endWorldRender();
                    break;
                case 4:
                    break;
            }
            frameTaskStep = (frameTaskStep + 1) % 5;

            Renderer.INSTANCE.beginWorldRender();
            glRotatef(-player.xRot, 1, 0, 0);
            glRotatef(-player.yRot, 0, 1, 0);
            glTranslatef(-player.x, -player.y, -player.z);

            // testcube
            Renderer.INSTANCE.drawTestCubeVao(30,60,50);
            Renderer.INSTANCE.endWorldRender();

            println(player.x + " " + player.y + " " + player.z);

            glfwSwapBuffers(window);
            glfwPollEvents();
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
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            running = false;
        }
        player.handleKey(key, action);
    }

    private void mouseMoveCallback(long win, double x, double y) {
        float dx = (float) (x - WIDTH / 2.0);
        float dy = (float) (y - HEIGHT / 2.0);
        player.turn(-dx, dy);
        glfwSetCursorPos(win, WIDTH / 2.0, HEIGHT / 2.0);
    }

    public long getWindow() {
        return window;
    }

    public static void main(String[] args) {
        new Arcaterra().run();
    }
}