package com.fish.arcaterra.debug;

import javax.swing.*;
import java.awt.*;

/**
 * 外置调试窗口，显示游戏运行时信息。
 * 使用 Swing 实现，独立于 GLFW 窗口。
 */
public class DebugWindow extends JFrame {
    private JLabel lblPlayerPos;
    private JLabel lblFps;
    private JLabel lblChunkCount;
    private JLabel lblHeight;
    private JLabel lblMemory;
    private JLabel lblStatus;

    private static DebugWindow instance;

    private DebugWindow() {
        setTitle("调试信息 - Arcaterra");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(320, 240);
        setLocation(100, 100);
        setAlwaysOnTop(true);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        lblPlayerPos = new JLabel("玩家坐标: (0, 0, 0)");
        lblFps = new JLabel("FPS: 0");
        lblChunkCount = new JLabel("区块总数: 0");
        lblHeight = new JLabel("地面高度: 0.0");
        lblMemory = new JLabel("内存: 0 MB");
        lblStatus = new JLabel("状态: 运行中");

        panel.add(lblPlayerPos);
        panel.add(lblFps);
        panel.add(lblChunkCount);
        panel.add(lblHeight);
        panel.add(lblMemory);
        panel.add(lblStatus);

        add(panel);
        setVisible(false); // 默认隐藏
    }

    public static DebugWindow getInstance() {
        if (instance == null) {
            instance = new DebugWindow();
        }
        return instance;
    }

    // 更新方法（线程安全）
    public void updateInfo(float x, float y, float z, int fps, int chunkCount, float height, long usedMemory) {
        SwingUtilities.invokeLater(() -> {
            lblPlayerPos.setText(String.format("玩家坐标: (%.2f, %.2f, %.2f)", x, y, z));
            lblFps.setText("FPS: " + fps);
            lblChunkCount.setText("区块总数: " + chunkCount);
            lblHeight.setText(String.format("地面高度: %.2f", height));
            lblMemory.setText(String.format("内存: %.1f MB", usedMemory / (1024.0 * 1024.0)));
        });
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> lblStatus.setText("状态: " + status));
    }

    // 切换显示/隐藏
    public void toggleVisibility() {
        setVisible(!isVisible());
    }
}