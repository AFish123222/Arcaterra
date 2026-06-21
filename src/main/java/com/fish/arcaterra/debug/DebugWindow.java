package com.fish.arcaterra.debug;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.Map;

public class DebugWindow extends JFrame {
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel model;
    private final JTree tree;
    private static DebugWindow instance;
    private boolean disposed = false;

    private DebugWindow() {
        setTitle("调试信息 - Arcaterra");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 400);
        setLocation(100, 100);
        setAlwaysOnTop(true);

        root = new DefaultMutableTreeNode("Debug");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(true);
        tree.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // 滚动面板
        JScrollPane scrollPane = new JScrollPane(tree);

        // --- 按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copyBtn = new JButton("复制为 Markdown");
        copyBtn.addActionListener(e -> copyAsMarkdown());
        buttonPanel.add(copyBtn);

        // 主布局
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

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
        root.removeAllChildren();
        model.reload();
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

            root.removeAllChildren();

            Map<String, Map<String, Object>> data = DebugRegistry.snapshot();

            if (data.isEmpty()) {
                root.add(new DefaultMutableTreeNode("暂无调试数据，请在各模块中注册"));
            } else {
                for (Map.Entry<String, Map<String, Object>> groupEntry : data.entrySet()) {
                    String groupName = groupEntry.getKey();
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupName);
                    for (Map.Entry<String, Object> entry : groupEntry.getValue().entrySet()) {
                        String display = entry.getKey() + " = " + entry.getValue();
                        groupNode.add(new DefaultMutableTreeNode(display));
                    }
                    root.add(groupNode);
                }
            }

            model.reload();
            // 展开所有分组节点
            expandAll(true);
        });
    }

    /**
     * 将当前调试数据复制为 Markdown 无序列表
     */
    private void copyAsMarkdown() {
        try {
            Map<String, Map<String, Object>> data = DebugRegistry.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("# 调试信息\n\n");
            for (Map.Entry<String, Map<String, Object>> groupEntry : data.entrySet()) {
                String group = groupEntry.getKey();
                sb.append("## ").append(group).append("\n\n");
                for (Map.Entry<String, Object> entry : groupEntry.getValue().entrySet()) {
                    sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }
            // 复制到剪贴板
            StringSelection selection = new StringSelection(sb.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            // 给用户一点反馈
            JOptionPane.showMessageDialog(this, "已复制 Markdown 到剪贴板", "复制成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "复制失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void toggleVisibility() {
        if (disposed) {
            instance = new DebugWindow();
        }
        setVisible(!isVisible());
        if (isVisible()) {
            refresh();
        }
    }

    public void setStatus(String status) {
        if (disposed) return;
        DebugRegistry.register("System", "Status", () -> status);
    }

    public static void forceDispose() {
        if (instance != null) {
            instance.disposeInternal();
            instance.dispose();
            instance = null;
        }
    }

    private void expandAll(boolean expand) {
        TreePath rootPath = new TreePath(root);
        if (expand) {
            tree.expandPath(rootPath);
            for (int i = 0; i < root.getChildCount(); i++) {
                tree.expandPath(rootPath.pathByAddingChild(root.getChildAt(i)));
            }
        } else {
            tree.collapsePath(rootPath);
        }
    }
}