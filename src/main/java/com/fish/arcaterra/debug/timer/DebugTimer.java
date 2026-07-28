package com.fish.arcaterra.debug.timer;

import java.util.HashMap;
import java.util.Map;

/**
 * 轻量级调试计时器，使用 try-with-resources 自动计时。
 * 使用方法：
 * try (DebugTimer timer = new DebugTimer("rebuildMesh")) {
 *     chunk.rebuildMesh();
 * }
 */
public class DebugTimer implements AutoCloseable {
    private static final Map<String, Long> timers = new HashMap<>();
    private static final Map<String, Integer> counts = new HashMap<>();
    private static final Map<String, Long> totals = new HashMap<>();

    private final String tag;
    private final long start;

    /**
     * 轻量级调试计时器，使用 try-with-resources 自动计时。
     * 使用方法：
     * try (DebugTimer timer = new DebugTimer("rebuildMesh")) {
     *     chunk.rebuildMesh();
     * }
     */
    public DebugTimer(String tag) {
        this.tag = tag;
        this.start = System.nanoTime();
    }

    @Override
    public void close() {
        long elapsed = System.nanoTime() - start;
        long ms = elapsed / 1_000_000;
        counts.put(tag, counts.getOrDefault(tag, 0) + 1);
        totals.put(tag, totals.getOrDefault(tag, 0L) + ms);

        String summary = String.format(
                "[%s] %d ms (avg: %.1f ms, count: %d)",
                tag, ms,
                totals.get(tag) / (double) counts.get(tag),
                counts.get(tag)
        );
        System.out.println(summary);

//        // 同时写入 DebugWindow
//        DebugRegistry.put("Timer." + tag, summary);
    }

    public static void resetStats() {
        timers.clear();
        counts.clear();
        totals.clear();
    }
}
