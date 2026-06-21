package com.fish.arcaterra.debug;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;

public class DebugWindow extends JFrame {
    private final JPanel contentPanel = new JPanel();
    private static DebugWindow instance;
    private boolean disposed = false; // 标记窗口是否已释放

    private DebugWindow() {
        setTitle("调试信息 - Arcaterra");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // 点击关闭时释放资源
        setSize(400, 350);
        setLocation(100, 100);
        setAlwaysOnTop(true);

        contentPanel.setLayout(new GridLayout(0, 1));
        add(new JScrollPane(contentPanel));
        setVisible(false);

        // 监听窗口关闭事件，自动清理单例
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cleanup(); // 窗口关闭时清理单例
            }
        });
    }

    public static DebugWindow getInstance() {
        // 如果实例不存在，或已被释放（disposed），重新创建
        if (instance == null || instance.disposed) {
            instance = new DebugWindow();
        }
        return instance;
    }

    public void refresh() {
        if (disposed) return; // 已释放则忽略
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            contentPanel.removeAll();

            Map<String, Object> data = DebugRegistry.snapshot();

            if (data.isEmpty()) {
                contentPanel.add(new JLabel("暂无调试数据，请在各模块中注册"));
            } else {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String display = entry.getKey() + " = " + entry.getValue();
                    contentPanel.add(new JLabel(display));
                }
            }

            contentPanel.revalidate();
            contentPanel.repaint();
        });
    }

    public void toggleVisibility() {
        if (disposed) {
            // 如果已释放，重新创建（getInstance 会自动创建）
            instance = new DebugWindow();
        }
        setVisible(!isVisible());
        if (isVisible()) {
            refresh();
        }
    }

    public void setStatus(String status) {
        if (disposed) return;
        DebugRegistry.register("System.Status", () -> status);
    }

    /**
     * 释放窗口资源，并清除单例
     */
    public static void cleanup() {
        if (instance != null) {
            instance.disposed = true;
            instance.setVisible(false);
            instance.dispose();
            instance = null;
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }
}