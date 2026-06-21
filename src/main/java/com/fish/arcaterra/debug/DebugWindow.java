package com.fish.arcaterra.debug;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

        // 双击复制值（可选）
        // tree.addMouseListener(...);

        JScrollPane scrollPane = new JScrollPane(tree);
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
            // 默认展开所有分组节点（可改为展开根，折叠分组）
            expandAll(true);
        });
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

    // 展开所有节点
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