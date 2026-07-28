package com.fish.arcaterra;

import com.fish.arcaterra.debug.DebugRegistry;
import com.fish.arcaterra.debug.DebugWindow;
import com.fish.arcaterra.level.World;
import com.fish.arcaterra.level.Chunk;
import com.fish.arcaterra.particle.ParticlePool;
import com.fish.arcaterra.phys.BlockHit;
import com.fish.arcaterra.render.Frustum;
import com.fish.arcaterra.render.Renderer;
import com.fish.arcaterra.tree.TreeNetChunk;
import com.fish.arcaterra.tree.TreeNetWorld;
import com.fish.arcaterra.tree.TreePath;
import com.fish.arcaterra.tree.terrain.NoiseLodTerrainProvider;
import com.fish.arcaterra.ui.hud.Crosshair;
import com.fish.arcaterra.ui.hud.DebugIndicators;
import com.fish.arcaterra.ui.hud.HudManager;
import com.fish.arcaterra.worldgen.TerrainProvider;
import com.fish.arcaterra.worldgen.noise.NoiseTerrainProvider;
import com.fish.arcaterra.worldgen.terrarium.DemTerrainProvider;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.swing.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.fish.arcaterra.level.ChunkPool.LOAD_RADIUS;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/// # 桃源Arcaterra
/// ## 桃源引
///   大丈夫志在碧海，暮宿苍梧；少年心厌樊笼，梦归南山。<br>
///   昔中考前，每乘暇日，独驾单车，登彼北塬。时维初夏，序属清和。天风浩荡，自终南而北来；麦浪参差，从足下而远山。绿接青冥，一望无际；波摇翠影，万顷同辉。倚孤树而旷然，望晚山以兴叹，感一身之渺然。<br>
///   然余身羁俗网，足困尘阡。非无远志，实未能前。欲翻南山之巅，以穷千里之目；奈何双足如缚，竟阻咫尺之途。每北望而长嗟，对清风而自问：何日得脱此樊笼，凌绝顶而览四方？<br>
///   于是另开户牖，名曰桃源。入此境中，余不慕斗粟，唯求心闲。精研造极，术业穷深；万象之美，浩若烟云。<br>
///   或问于人，众议纷纭。有默然者，有长叹者，更有笑而嘲曰：“陶元亮其憨，徐霞客其顽。”<br>
///   余闻之，俯首良久，仰而应曰：
/// “人生如寄，倏忽百年。或屈于俗，终身不遂己愿；或纵于己，尽付流水高山。吾今虽不能杖履千峰，然心向往之，故为桃源。聊以自足，慰此余年。”<br>
/// 丙午年五月廿八日 序 <br>
/// ===================== <br>
/// 这是继承terrarium的项目，离开了minecraft,我会优化Arcaterra到极致，颜值与速度并存<br>
/// 我会永远开发一份开源的，哪怕另一份作为商业项目<br>
/// 本阶段会重新实现并优化Minecraft1.21.5+Fabric+Terrarium+LOD(DH/Voxy/自研文件树LOD)
///
/// 作者会同步学习游戏开发知识 并完善JavaDoc，便利开发
public class Arcaterra {
    private long window;
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    private boolean running;
    private boolean mouseCaptured = true;

    private long lastDebugUpdate = System.currentTimeMillis();
    private int fpsCounter = 0;
    private int currentFps = 0;

    private World world;
    private Player player;
    private int frameTaskStep;
    public static ParticlePool particlePool;

    private Frustum frustum;

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
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_HIDDEN); //隐藏鼠标

        GL.createCapabilities();
        glClearColor(0.4f, 0.7f, 1.0f, 1f);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
//        glFrustum(-aspect * 0.1f, aspect * 0.1f, -0.1f, 0.1f, 0.1f, 2000f);
//        glMatrixMode(GL_MODELVIEW);
//        Frustum frustum = new Frustum(glGetFloatv());
        // 在 init() 中，不再调用 glFrustum，而是用 JOML 计算并上传
        Matrix4f projMatrix = new Matrix4f().setFrustum(
                -aspect * 0.1f, aspect * 0.1f,  // left, right
                -0.1f, 0.1f,                    // bottom, top
                0.1f, 2000f                     // near, far
        );
        // 上传到 OpenGL（固定管线）
        FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
        projMatrix.get(projBuf);
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projBuf);
        glMatrixMode(GL_MODELVIEW);
        // 创建 Frustum（视图矩阵后续更新）
        Matrix4f viewMatrix = new Matrix4f();
        Frustum frustum = new Frustum(projMatrix, viewMatrix);


        // 在 init() 中
        if (Config.renderMode == Config.RenderMode.TREE_LOD) {
            LODManager.treeWorld = new TreeNetWorld(new NoiseLodTerrainProvider());
        }

        // 使用噪声地形
        if (Config.worldGenMode == Config.WorldGenMode.NOISE) this.world = new World(new NoiseTerrainProvider());
        // 使用dem
        if (Config.worldGenMode == Config.WorldGenMode.DEM) this.world = new World(new DemTerrainProvider(107.1, 34.3,1,1));



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
        world.updateChunks(player.x, player.y, player.z,frustum);
        for (Chunk c : world.getDirtyChunks()) {
            c.rebuildMesh();
        }

        // 初始化调试窗口（但不显示）
        initDebugWindow();
        System.out.println("press F3 to debug");

        // hud
        hudManager = new HudManager();
        hudManager.add(new Crosshair());
        hudManager.add(DebugIndicators.getDebugIndicators());

        running = true;
        frameTaskStep = 0;


        particlePool = new ParticlePool(5000); // 最多 5000 个粒子
    }

    private HudManager hudManager;

    private void initDebugWindow() {
        SwingUtilities.invokeLater(() -> {
            DebugWindow.getInstance().setStatus("初始化完成");
        });
        // 在 init 中
        DebugRegistry.register("System", "FPS", () -> currentFps);
        DebugRegistry.register("System", "Delta", () -> delta);
        DebugRegistry.register("System", "Memory", () ->
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0) + " MB");

        DebugRegistry.register("World", "VisibleChunks", () -> world.getVisibleChunks().size());
        DebugRegistry.register("World", "DirtyChunks", () -> world.getDirtyChunks().size());
        DebugRegistry.register("World", "GroundHeight", () -> world.getTerrainProvider().getHeight(player.x, player.z));
    }

    private int frameCounter = 0;
    private double lastTime = 0.0;
    private double delta = 0.0;

    private int lodVao = 0;
    private int lodVbo = 0;
    private int lodIbo = 0;

    private void loop() {
        // 初始化时间
        lastTime = glfwGetTime();

        if (Config.renderMode == Config.RenderMode.TREE_LOD){
            TreePath playerPath = LODManager.worldToPath(player.x, player.y, player.z);
            LODManager.treeWorld.updatePlayerPath(playerPath);
        }


        while (running && !glfwWindowShouldClose(window)) {
//            try (DebugTimer timer = new DebugTimer("Loop")) {

            double now = glfwGetTime();
            delta = now - lastTime;
            lastTime = now;

            // 限制 delta 最大值（防止跳帧时突变）
            if (delta > 0.05) delta = 0.05;   // 最多 50ms

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            player.tick((float) delta);
            switch (frameTaskStep) {
                case 0:

                    particlePool.update((float) delta);// 传入 delta
                    break;
                case 1:
                    world.updateChunks(player.x, player.y, player.z,frustum);
                    break;
                case 2:
                    rebuildSomeDirtyChunks(16);
                    break;
            }
            frameTaskStep = (frameTaskStep + 1) % 3;


            Renderer.INSTANCE.beginWorldRender();
            glRotatef(-player.xRot, 1, 0, 0);
            glRotatef(-player.yRot, 0, 1, 0);
            glTranslatef(-player.x, -player.y, -player.z);

            particlePool.render(player.x, player.y, player.z);

//            lodMesh.update(player.x, player.z, world.getTerrainProvider(), 8, 4.0f);

            DebugIndicators.getDebugIndicators().setPlayerPos(player.x, player.y , player.z);
            DebugIndicators.getDebugIndicators().setRotation(player.yRot, player.xRot);

            if(Config.renderMode == Config.RenderMode.ORIGINAL){
                for (Chunk c : world.getVisibleChunks()) {
//                    c.render(player.x, player.y, player.z);
                    if (Config.showAllChunkBound) c.renderChunkBounds();
                } //可见区块渲染
            }

            if(Config.renderMode == Config.RenderMode.TREE_LOD){
                LODManager.treeWorld.render(player.x, player.y, player.z);
            }

//            lodMesh.render();
            ///////////
            boolean pushVituces = true;
            int radius = 5;
            int step = 1;
            float minX = player.x - radius * Chunk.SIZE;
            float maxX = player.x + radius * Chunk.SIZE;
            float minZ = player.z - radius * Chunk.SIZE;
            float maxZ = player.z + radius * Chunk.SIZE;

// ---- 远方高度图 LOD（直接推顶点，无索引） ----
            if (pushVituces == false) {
                glColor3f(0.5f, 0.6f, 0.4f);
//            glBegin(GL_TRIANGLES);

                glBegin(GL_LINES);
                glLineWidth(2);

                for (float z = minZ; z < maxZ; z += step) {
                    for (float x = minX; x < maxX; x += step) {
                        // 四个角的高度
                        float h00 = world.getTerrainProvider().getHeight(x, z);
                        float h10 = world.getTerrainProvider().getHeight(x + step, z);
                        float h01 = world.getTerrainProvider().getHeight(x, z + step);
                        float h11 = world.getTerrainProvider().getHeight(x + step, z + step);

                        // 三角形1: (x,z) -> (x+step,z) -> (x,z+step)
                        glVertex3f(x, h00, z);
                        glVertex3f(x + step, h10, z);
                        glVertex3f(x, h01, z + step);
                        // 三角形2: (x+step,z) -> (x+step,z+step) -> (x,z+step)
                        glVertex3f(x + step, h10, z);
                        glVertex3f(x + step, h11, z + step);
                        glVertex3f(x, h01, z + step);
                    }
                }
                glEnd();


            }
            if (pushVituces == true) {
// 在 Arcaterra.loop() 中（每帧执行）
// ---- 远方高度图 LOD（VBO 推顶点） ----

                int cols = (int) ((maxX - minX) / step) + 1;
                int rows = (int) ((maxZ - minZ) / step) + 1;

// 分配数组
                float[] vArr = new float[cols * rows * 3];
                int[] iArr = new int[(cols - 1) * (rows - 1) * 6];

                int idx = 0;
                for (float z = minZ; z <= maxZ; z += step) {
                    for (float x = minX; x <= maxX; x += step) {
                        float h = world.getTerrainProvider().getHeight(x, z);
                        vArr[idx++] = x;
                        vArr[idx++] = h;
                        vArr[idx++] = z;
                    }
                }

                int iIdx = 0;
                for (int r = 0; r < rows - 1; r++) {
                    for (int c = 0; c < cols - 1; c++) {
                        int i0 = c + r * cols;
                        int i1 = (c + 1) + r * cols;
                        int i2 = c + (r + 1) * cols;
                        int i3 = (c + 1) + (r + 1) * cols;
                        iArr[iIdx++] = i0;
                        iArr[iIdx++] = i1;
                        iArr[iIdx++] = i2;
                        iArr[iIdx++] = i1;
                        iArr[iIdx++] = i3;
                        iArr[iIdx++] = i2;
                    }
                }

// 上传到 VBO（可复用）
                if (lodVao == 0) {
                    lodVao = glGenVertexArrays();
                    lodVbo = glGenBuffers();
                    lodIbo = glGenBuffers();
                }

                glBindVertexArray(lodVao);

// 顶点数据
                glBindBuffer(GL_ARRAY_BUFFER, lodVbo);
                FloatBuffer vBuf = MemoryUtil.memAllocFloat(vArr.length);
                vBuf.put(vArr).flip();
                glBufferData(GL_ARRAY_BUFFER, vBuf, GL_DYNAMIC_DRAW);
                MemoryUtil.memFree(vBuf);
                glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
                glEnableVertexAttribArray(0);

// 索引数据
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lodIbo);
                IntBuffer iBuf = MemoryUtil.memAllocInt(iArr.length);
                iBuf.put(iArr).flip();
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuf, GL_DYNAMIC_DRAW);
                MemoryUtil.memFree(iBuf);

                glBindVertexArray(0);

// 绘制
                // 绘制
// 绘制
                glBindVertexArray(lodVao);
                glDisable(GL_DEPTH_TEST); // 线框不被遮挡

// 线框模式
                glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                glColor3f(1.0f, 0.0f, 0.0f); // 红色，确保可见
                glDrawElements(GL_TRIANGLES, iArr.length, GL_UNSIGNED_INT, 0);

// 恢复状态
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glEnable(GL_DEPTH_TEST);
                glBindVertexArray(0);
            }

        /////////////

            // 渲染玩家所在区块的边界
            if (Config.showChunkBoundPlayerAt) {
                try{
                world.getChunk(
                        player.x,
                        player.y,
                        player.z
                ).renderChunkBounds();}catch (NullPointerException e){
                    System.out.println("player chunk npe");
                    //todo
                }
            }




            Renderer.INSTANCE.drawEyeRay(player.x, player.y, player.z, player.xRot, player.yRot);

            Renderer.INSTANCE.endWorldRender();

            hudManager.render();

            glfwSwapBuffers(window);
            debugUpdate();
            glfwPollEvents();
        }
//        }
    }

    private void debugUpdate() {
        fpsCounter++;
        long now = System.currentTimeMillis();
        if (now - lastDebugUpdate >= 1000) {
            currentFps = fpsCounter;
            fpsCounter = 0;
            lastDebugUpdate = now;

            // 刷新调试窗口（内部调用 snapshot）
            DebugWindow.getInstance().refresh();
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

        if (lodVao != 0) {
            glDeleteVertexArrays(lodVao);
            glDeleteBuffers(lodVbo);
            glDeleteBuffers(lodIbo);
            lodVao = lodVbo = lodIbo = 0;
        }

        // 保留调试窗口，不释放 //todo:support config to chose between sameshut and twiceshut
        System.out.println("游戏已退出，调试窗口仍然保留。");
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
                    glfwSetCursorPos(window, WIDTH / 2.0, HEIGHT / 2.0);
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
        if (hit == null) {
            return;
        }
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            world.setBlock(hit.x, hit.y, hit.z, (short) 0);
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            int nx = hit.x + hit.nx;
            int ny = hit.y + hit.ny;
            int nz = hit.z + hit.nz;
            world.setBlock(nx, ny, nz, (short) 1);
        }

        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            // 5. 放置：在相邻位置放置石块
            int nx = hit.x + hit.nx;
            int ny = hit.y + hit.ny;
            int nz = hit.z + hit.nz;
            world.setBlock(nx, ny, nz, (short) 1);

            Chunk c = world.getChunkIfLoaded(nx, ny, nz);
            if (c != null) {
                c.rebuildMesh();
                c.dirty = false;
            }

        }
    }

    public static void main(String[] args) {
        new Arcaterra().run();
    }


    /// ### 配置类
    public static class Config {
        /// 渲染玩家所在区块边界，空黄实绿
        public static boolean showChunkBoundPlayerAt = true;
        /// 渲染所有区块边界，空黄实绿
        public static boolean showAllChunkBound = false;
        /// 启用lodRender
        public static RenderMode renderMode = RenderMode.ORIGINAL;
        public enum RenderMode {
            /// 运行World,Chunk (com.fish.arcaterra.level)
            ORIGINAL,
            /// 运行com.fish.arcaterra.tree
            TREE_LOD,
        }
        /// 世界生成器
        public static WorldGenMode worldGenMode = WorldGenMode.DEM;
        public enum WorldGenMode {
            /// 噪声地形
            NOISE,
            /// Terrarium
            DEM
        }
    }

    /// LOD
    static class LODManager {
        // 在 Arcaterra.java 中
        public static TreeNetWorld treeWorld;

        /**
         * 将世界坐标转换为 TreePath。
         * @param x, y, z 世界坐标
         * @return 从根到该位置的叶子节点路径
         */
        public static TreePath worldToPath(float x, float y, float z) {
            int size = TreeNetChunk.ROOT_SIZE;
            long code = 0;
            int depth = 0;

            // 偏移到根节点范围 [0, ROOT_SIZE)
            float px = x + size / 2f;
            float py = y + size / 2f;
            float pz = z + size / 2f;

            while (size > TreeNetChunk.LEAF_SIZE) {
                int half = size >> 1;
                int dir = 0;
                if (px >= half) { px -= half; dir |= 1; }
                else px += half;
                if (py >= half) { py -= half; dir |= 2; }
                else py += half;
                if (pz >= half) { pz -= half; dir |= 4; }
                else pz += half;
                code = (code << 3) | dir;
                depth++;
                size = half;
            }
            return new TreePath(code, depth);
        }
    }

//    private LODMesh lodMesh=new LODMesh();
    /**
     * 高度图 LOD 网格，使用一个 VBO 覆盖玩家周围整个区域。
     * 每帧或玩家移动超过阈值时重建。
     */
    public class LODMesh {
        private int vao;
        private int vbo;
        private int ibo;
        private int vertexCount;
        private int indexCount;

        private float lastPlayerX, lastPlayerZ;
        private static final float UPDATE_THRESHOLD = 8.0f; // 移动超过 8 格才重建

        /**
         * 根据玩家位置更新网格。
         * @param playerX 玩家 X
         * @param playerZ 玩家 Z
         * @param provider 地形提供者
         * @param radius 覆盖半径（区块数）
         * @param step 采样步长（格）
         */
        public void update(float playerX, float playerZ, TerrainProvider provider, int radius, float step) {
//            if (Math.abs(playerX - lastPlayerX) < UPDATE_THRESHOLD &&
//                    Math.abs(playerZ - lastPlayerZ) < UPDATE_THRESHOLD) {
//                return; // 位置变化不大，不重建
//            }
            lastPlayerX = playerX;
            lastPlayerZ = playerZ;

            // 计算覆盖范围
            int halfSize = radius * 16; // 假设区块大小为 16
            float minX = playerX - halfSize;
            float maxX = playerX + halfSize;
            float minZ = playerZ - halfSize;
            float maxZ = playerZ + halfSize;

            int cols = (int) ((maxX - minX) / step) + 1;
            int rows = (int) ((maxZ - minZ) / step) + 1;

            List<Float> vertices = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            // 1. 生成顶点
            for (float z = minZ; z <= maxZ; z += step) {
                for (float x = minX; x <= maxX; x += step) {
                    float height = provider.getHeight(x, z);
                    vertices.add(x);
                    vertices.add(height);
                    vertices.add(z);
                }
            }

            // 2. 生成索引
            for (int r = 0; r < rows - 1; r++) {
                for (int c = 0; c < cols - 1; c++) {
                    int i0 = c + r * cols;
                    int i1 = (c + 1) + r * cols;
                    int i2 = c + (r + 1) * cols;
                    int i3 = (c + 1) + (r + 1) * cols;
                    indices.add(i0);
                    indices.add(i1);
                    indices.add(i2);
                    indices.add(i1);
                    indices.add(i3);
                    indices.add(i2);
                }
            }

            vertexCount = vertices.size() / 3;
            indexCount = indices.size();

            // 转换为数组
            float[] vArr = new float[vertices.size()];
            int[] iArr = new int[indices.size()];
            for (int i = 0; i < vArr.length; i++) vArr[i] = vertices.get(i);
            for (int i = 0; i < iArr.length; i++) iArr[i] = indices.get(i);

            // 上传到 GPU
            if (vao == 0) {
                vao = glGenVertexArrays();
                vbo = glGenBuffers();
                ibo = glGenBuffers();
            }

            glBindVertexArray(vao);

            // 顶点数据
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            FloatBuffer vBuf = MemoryUtil.memAllocFloat(vArr.length);
            vBuf.put(vArr).flip();
            glBufferData(GL_ARRAY_BUFFER, vBuf, GL_STATIC_DRAW);
            MemoryUtil.memFree(vBuf);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
            glEnableVertexAttribArray(0);

            // 索引数据
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
            IntBuffer iBuf = MemoryUtil.memAllocInt(iArr.length);
            iBuf.put(iArr).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuf, GL_STATIC_DRAW);
            MemoryUtil.memFree(iBuf);

            glBindVertexArray(0);
        }

        /**
         * 渲染 LOD 网格。
         */
        public void render() {
            if (indexCount == 0) return;
            glBindVertexArray(vao);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }

        public void destroy() {
            if (vao != 0) {
                glDeleteVertexArrays(vao);
                glDeleteBuffers(vbo);
                glDeleteBuffers(ibo);
                vao = vbo = ibo = 0;
            }
        }
    }
}