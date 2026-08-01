package com.fish.arcaterra;

import com.fish.arcaterra.level.Chunk;

/// ### 配置类
public class Config {
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

    public class DEMGeneratorConfig {
        public static int meterPerBlockXZ = 2;
        public static float meterPerBlockY = 1f;

        public static boolean demTileLoadLog = true;
    }

    public static class DemSampleLodConfig {
        public static int renderRadius = 700; //单位;chunk
        public static int sampleStep = Math.max(Chunk.SIZE * renderRadius / 700, 1);
//        public static int sampleStep = 1;


        /**
         * 后台线程休眠期间的轮询间隔（毫秒）。<br>
         * 后台线程在两次 regenIntervalMs 之间处于休眠状态，<br>
         * 每隔 pollIntervalMs 唤醒一次，检查是否有 requestRebuild() 请求。<br>
         *<br>
         * 默认值 1000ms（1 秒），意味着传送后最多等待 1 秒才会开始生成。<br>
         * 调小此值可更快响应传送请求，但会增加 CPU 唤醒次数。<br>
         * 调大此值可减少 CPU 唤醒，但传送后响应会延迟。<br>
         */
        public static long pollIntervalMs = 1000;
        /**
         * 后台线程自动重新生成 LOD 的间隔时间（毫秒）。<br>
         * 即使没有触发 requestRebuild()，后台线程也会每隔此间隔重新生成一次 LOD 网格，<br>
         * 以保持地形与玩家位置同步（当玩家缓慢移动或地形数据更新时）。<br>
         *<br>
         * 默认值 20000ms（20 秒），适合玩家静止或缓慢移动的场景。<br>
         * 调小此值可提高地形更新频率，但会增加 CPU 负载。<br>
         * 调大此值可降低 CPU 负载，但地形更新会延迟。<br>
         */
        public static long regenIntervalMs = 5000;

        public static boolean debugWireframe = true;

//        private static int[] color = {255,0,0}; //纯红
//        private static int[] color = {180,155,120}; // 苍黄1 沙
//        private static int[] color = {160,140,110}; // 苍黄2 卡/ 苍2 卡其其
//        private static int[] color = {61,107,84}; //
//        private static int[] color = {79,138,107}; //
//        private static int[] color = {46,90,76}; //
//        private static int[] color = {58,95,73}; //
//        private static int[] color = {65,120,70}; //
        private static int[] color = {65,110,70}; //
//        private static int[] color = {60,105,65}; //



        public static float colorR = (float) color[0] /255;
        public static float colorG = (float) color[1] /255;
        public static float colorB = (float) color[2] /255;
    }
}
