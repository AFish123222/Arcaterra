package com.fish.arcaterra.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 调试信息注册表。
 * - 对于成员变量：使用 register(key, supplier) 注册，延迟读取。
 * - 对于局部变量：使用 put(key, value) 主动推送。
 * DebugWindow 定时调用 snapshot() 获取所有值。
 */
public class DebugRegistry {
    private static final Map<String, Supplier<Object>> suppliers = new LinkedHashMap<>();
    private static final Map<String, Object> snapshots = new LinkedHashMap<>();

    /**
     * 注册一个延迟读取的调试信息（适用于成员变量）
     * @param key 显示键名
     * @param supplier 提供该值的函数（捕获变量引用）
     */
    public static void register(String key, Supplier<Object> supplier) {
        suppliers.put(key, supplier);
    }

    /**
     * 主动推送一个调试值（适用于局部变量或一次性数据）
     * 会覆盖同名的延迟读取项
     * @param key 显示键名
     * @param value 当前值
     */
    public static void put(String key, Object value) {
        snapshots.put(key, value);
    }

    /**
     * 获取所有注册项的快照：先加入快照（put），再加入 Supplier（如果 key 不重复）
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        // 先放快照（优先级高）
        result.putAll(snapshots);
        // 再放 Supplier
        for (Map.Entry<String, Supplier<Object>> entry : suppliers.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                try {
                    result.put(entry.getKey(), entry.getValue().get());
                } catch (Exception e) {
                    result.put(entry.getKey(), "ERROR: " + e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * 清空注册表（用于重新加载或重置）
     */
    public static void clear() {
        suppliers.clear();
        snapshots.clear();
    }
}