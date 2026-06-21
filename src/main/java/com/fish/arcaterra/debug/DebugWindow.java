package com.fish.arcaterra.debug;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * 调试窗口，独立于游戏窗口。
 * 定时从 DebugRegistry 拉取数据并刷新显示。
 */
public class DebugWindow extends JFrame {
    private final JPanel contentPanel = new JPanel();
    private static DebugWindow instance;

    private DebugWindow() {
        setTitle("调试信息 - Arcaterra");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(400, 350);
        setLocation(100, 100);
        setAlwaysOnTop(true);

        contentPanel.setLayout(new GridLayout(0, 1));
        add(new JScrollPane(contentPanel));
        setVisible(false);
    }

    public static DebugWindow getInstance() {
        if (instance == null) {
            instance = new DebugWindow();
        }
        return instance;
    }

    /**
     * 刷新窗口：从注册表拉取所有数据并重新绘制
     */
    public void refresh() {
        SwingUtilities.invokeLater(() -> {
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
        setVisible(!isVisible());
        if (isVisible()) {
            refresh(); // 显示时立即刷新
        }
    }

    public void setStatus(String status) {
        DebugRegistry.register("System.Status", () -> status);
    }
}