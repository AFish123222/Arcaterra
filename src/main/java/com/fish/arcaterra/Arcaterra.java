package com.fish.arcaterra;

import com.fish.arcaterra.debug.DebugRegistry;
import com.fish.arcaterra.debug.DebugWindow;
import com.fish.arcaterra.debug.IDebugWindowPrintRegistry;
import com.fish.arcaterra.demsamplerender.DemSampleRender;
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
import com.fish.arcaterra.ui.hud.HudManager;
import com.fish.arcaterra.worldgen.terrarium.DemTerrainProvider;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.swing.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

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
public class Arcaterra implements IDebugWindowPrintRegistry {
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
        debugParamRegister();

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

//        Matrix4f projMatrix = new Matrix4f().setFrustum(
//                -aspect * 0.1f, aspect * 0.1f,  // left, right
//                -0.1f, 0.1f,                    // bottom, top
//                0.1f, 2000f                     // near, far
//        );
        Matrix4f projMatrix = new Matrix4f();
        projMatrix.setPerspective((float) Math.toRadians(70), aspect, 20000000f, 0.1f);        // 注意：near 和 far 反过来了！far 在前，near 在后(z冲突->反转深度)

        glDepthFunc(GL_GREATER);  // 原来默认是 GL_LESS
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
//        if (Config.worldGenMode == Config.WorldGenMode.NOISE) this.world = new World(
//                new NoiseTerrainProvider()
//        );
        // 使用dem
//        if (Config.worldGenMode == Config.WorldGenMode.DEM) {
            demTerrainProvider = new DemTerrainProvider(//https://lbs.qq.com/getPoint
//                        107.1, 34.3, //?
                    107.371805,34.392071 ,// 陕西宝鸡陈仓区
                    Config.DEMGeneratorConfig.meterPerBlockXZ,
                    Config.DEMGeneratorConfig.meterPerBlockY
            );
            this.world = new World(demTerrainProvider);
//        }

        float spawnX = 50;
        float spawnZ = 30;
        float groundHeight = world.getTerrainProvider().getHeight(spawnX, spawnZ);
        float spawnY = groundHeight + 1.5f; // 站在地面以上

        player = new Player(world);
        player.setPos(spawnX, spawnY, spawnZ);

        demTerrainProvider.prefetchAround(player.x,player.z,Config.DemSampleLodConfig.renderRadius);

        // init()
        demSampleRender = DemSampleRender.getInstance();
        demSampleRender.init(world, player);



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

        running = true;
        frameTaskStep = 0;


        particlePool = new ParticlePool(5000); // 最多 5000 个粒子
    }

    private DemTerrainProvider demTerrainProvider;
    private DemSampleRender demSampleRender;

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
    private double delta = 0.0;


    private void loop() {
        // 初始化时间
        double lastTime = glfwGetTime();

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
                    DemTerrainProvider provider = (DemTerrainProvider) world.getTerrainProvider();
                    provider.update(player.x,player.z,Config.DemSampleLodConfig.renderRadius+2);
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

//            try(DebugTimer timer = new DebugTimer("render")) {
//                demSimpleLod();
            demSampleRender.updateVerticesIfReady();
            demSampleRender.render();
//            }


//            if(Config.renderMode == Config.RenderMode.ORIGINAL){
//                for (Chunk c : world.getVisibleChunks()) {
//                    c.render(player.x, player.y, player.z);
//                    if (Config.showAllChunkBound) c.renderChunkBounds();
//                } //可见区块渲染
//            }

            if(Config.renderMode == Config.RenderMode.TREE_LOD){
                LODManager.treeWorld.render(player.x, player.y, player.z);
            }
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

//            Renderer.INSTANCE.drawEyeRay(player.x, player.y, player.z, player.xRot, player.yRot);

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

        demTerrainProvider.shutdown();
        demSampleRender.shutdown();

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

    @Override
    public void debugParamRegister() {
        DebugRegistry.register("groundHeight",()-> world.getTerrainProvider().getHeight(player.x,player.z));
    }

    public int getFrameCounter() {
        return frameCounter;
    }


    /// treeLOD
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


    private float[] vArr;
    private int[] iArr;
    private FloatBuffer vBuf;
    private IntBuffer iBuf;
    private int lodVao = 0, lodVbo = 0, lodIbo = 0;
    /// dem采样lod;推顶点；<br>
    /// 配置参数：
    /// @see Config.DemSampleLodConfig
    private void demSimpleLod() {
        int radius = Config.DemSampleLodConfig.renderRadius;
        int step = Config.DemSampleLodConfig.sampleStep;
        float minX = player.x - radius * Chunk.SIZE;
        float maxX = player.x + radius * Chunk.SIZE;
        float minZ = player.z - radius * Chunk.SIZE;
        float maxZ = player.z + radius * Chunk.SIZE;
        int cols = (int) ((maxX - minX) / step) + 1;
        int rows = (int) ((maxZ - minZ) / step) + 1;

        // 复用数组（如果长度变化再重新分配）
        if (vArr == null || vArr.length != cols * rows * 3) {
            vArr = new float[cols * rows * 3];
        }
        if (iArr == null || iArr.length != (cols - 1) * (rows - 1) * 6) {
            iArr = new int[(cols - 1) * (rows - 1) * 6];
        }

        int idx = 0;
        for (int zi = 0; zi < rows; zi++) {
            float z = minZ + zi * step;
            for (int xi = 0; xi < cols; xi++) {
                float x = minX + xi * step;
                float alignedX = (float) (Math.floor(x / Chunk.SIZE) * Chunk.SIZE);
                float alignedZ = (float) (Math.floor(z / Chunk.SIZE) * Chunk.SIZE);
                float h = world.getTerrainProvider().getHeight(alignedX, alignedZ);
                vArr[idx++] = alignedX;
                vArr[idx++] = h;
                vArr[idx++] = alignedZ;
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
                iArr[iIdx++] = i2;
                iArr[iIdx++] = i3;
                iArr[iIdx++] = i0;
                iArr[iIdx++] = i3;
                iArr[iIdx++] = i1;
            }
        }

        // 创建或复用 VBO
        if (lodVao == 0) {
            lodVao = glGenVertexArrays();
            lodVbo = glGenBuffers();
            lodIbo = glGenBuffers();
        }

        glBindVertexArray(lodVao);

        // === 顶点数据 ===
        glBindBuffer(GL_ARRAY_BUFFER, lodVbo);
        // 复用 FloatBuffer
        if (vBuf == null || vBuf.capacity() < vArr.length) {
            if (vBuf != null) MemoryUtil.memFree(vBuf);
            vBuf = MemoryUtil.memAllocFloat(vArr.length);
            vBuf.put(vArr).flip();
            glBufferData(GL_ARRAY_BUFFER, vBuf, GL_DYNAMIC_DRAW);
        } else {
            vBuf.clear();
            vBuf.put(vArr).flip();
            // 注意：glBufferSubData 需要先分配足够的空间（用 glBufferData 分配过）
            glBufferSubData(GL_ARRAY_BUFFER, 0, vBuf);
        }
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);

        // === 索引数据 ===
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lodIbo);
        if (iBuf == null || iBuf.capacity() < iArr.length) {
            if (iBuf != null) MemoryUtil.memFree(iBuf);
            iBuf = MemoryUtil.memAllocInt(iArr.length);
            iBuf.put(iArr).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuf, GL_DYNAMIC_DRAW);
        } else {
            iBuf.clear();
            iBuf.put(iArr).flip();
            glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, iBuf);
        }

        glBindVertexArray(0);

        // 绘制
        renderLodMesh();
    }

    // 绘制方法（单独抽取）
    private void renderLodMesh() {
        if (lodVao == 0 || iArr == null || iArr.length == 0) return;
//        glDisable(GL_CULL_FACE);
        glBindVertexArray(lodVao);
        glDisable(GL_DEPTH_TEST);
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glColor3f(1.0f, 0.0f, 0.0f);
        glDrawElements(GL_TRIANGLES, iArr.length, GL_UNSIGNED_INT, 0);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(0);
    }
}