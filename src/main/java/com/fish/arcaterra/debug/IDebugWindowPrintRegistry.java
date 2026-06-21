package com.fish.arcaterra.debug;

/**
 * 调试窗口打印注册接口
 * 实现此接口以注册自定义调试信息
 */
public interface IDebugWindowPrintRegistry {
    /**
     * 注册调试参数
     * 实现此方法，在其中调用 DebugRegistry.register 注册调试信息
     */
    void debugParamRegister();
}
