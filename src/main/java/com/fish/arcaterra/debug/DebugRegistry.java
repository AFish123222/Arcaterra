package com.fish.arcaterra.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// ## debug窗口的调试注册表
public class DebugRegistry {
    private static final Map<String, Map<String, Supplier<Object>>> groups = new LinkedHashMap<>();

    /**
     * 注册一个调试信息（指定分组）
     * @param group 分组名（如 "Player"）
     * @param key 显示键名
     * @param supplier 值提供者
     * eg:  DebugRegistry.register("Group", "Key", () -> Var);<br>
     *                  //哪怕是实例变量，也是指针传递，自动刷新
     */
    public static void register(String group, String key, Supplier<Object> supplier) {
        groups.computeIfAbsent(group, k -> new LinkedHashMap<>()).put(key, supplier);
    }

    /**
     * 注册一个调试信息（默认分组 "Default"）
     */
    public static void register(String key, Supplier<Object> supplier) {
        register("Default", key, supplier);
    }

    /**
     * 主动推送一个值（分组为 "Push"）
     */
    public static void put(String key, Object value) {
        register("Push", key, () -> value);
    }

    /**
     * 获取快照：分组 -> (键 -> 值)
     */
    public static Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Supplier<Object>>> groupEntry : groups.entrySet()) {
            String group = groupEntry.getKey();
            Map<String, Object> groupMap = new LinkedHashMap<>();
            for (Map.Entry<String, Supplier<Object>> entry : groupEntry.getValue().entrySet()) {
                try {
                    groupMap.put(entry.getKey(), entry.getValue().get());
                } catch (Exception e) {
                    groupMap.put(entry.getKey(), "ERROR: " + e.getMessage());
                }
            }
            result.put(group, groupMap);
        }
        return result;
    }

    public static void clear() {
        groups.clear();
    }
}