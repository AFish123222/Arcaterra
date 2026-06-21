package com.fish.arcaterra.debug;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;

public class DebugWindow extends JFrame {
    private final JTextArea textArea = new JTextArea();
    private static DebugWindow instance;
    private boolean disposed = false;

    private DebugWindow() {
        setTitle("调试信息 - Arcaterra");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 350);
        setLocation(100, 100);
        setAlwaysOnTop(true);

        // 设置 JTextArea：不可编辑但可选择文本
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setBackground(new Color(240, 240, 240));
        textArea.setForeground(Color.BLACK);
        // 支持复制
        textArea.setFocusable(true);
        textArea.setSelectionColor(new Color(150, 200, 255));

        // 添加到滚动面板
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane);

        setVisible(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                disposeInternal();
            }
        });
    }

    private void disposeInternal() {
        disposed = true;
        textArea.setText(""); // 清空内容
        instance = null;
    }

    public static DebugWindow getInstance() {
        if (instance == null || instance.disposed) {
            instance = new DebugWindow();
        }
        return instance;
    }

    public void refresh() {
        if (disposed) return;
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;

            Map<String, Object> data = DebugRegistry.snapshot();

            StringBuilder sb = new StringBuilder();
            if (data.isEmpty()) {
                sb.append("暂无调试数据，请在各模块中注册");
            } else {
                // 计算最大键长度，用于对齐（可选）
                int maxKeyLen = 0;
                for (String key : data.keySet()) {
                    maxKeyLen = Math.max(maxKeyLen, key.length());
                }
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String key = entry.getKey();
                    String value = String.valueOf(entry.getValue());
                    // 左对齐键，加冒号，对齐显示
                    sb.append(String.format("%-" + (maxKeyLen + 2) + "s = %s%n", key + ":", value));
                }
            }
            textArea.setText(sb.toString());
            // 滚动到顶部
            textArea.setCaretPosition(0);
        });
    }

    public void toggleVisibility() {
        if (disposed) {
            instance = new DebugWindow();
        }
        setVisible(!isVisible());
        if (isVisible()) {
            refresh();
            // 获取焦点，以便直接 Ctrl+C
            textArea.requestFocusInWindow();
        }
    }

    public void setStatus(String status) {
        if (disposed) return;
        DebugRegistry.register("System.Status", () -> status);
    }

    public static void forceDispose() {
        if (instance != null) {
            instance.disposeInternal();
            instance.dispose();
            instance = null;
        }
    }
}