package com.fish.arcaterra.tree;


/// 局部路径编码，从根到叶子的方向序列。
/// 每个方向用 3 位表示（0-7），代表八叉树的 8 个子节点。
public class TreePath {
    private final long code;   // 64位编码，每3位一个方向
    private final int depth;   // 路径长度

    public TreePath(long code, int depth) {
        this.code = code;
        this.depth = depth;
    }

    // 从父路径添加一个子节点方向
    public TreePath append(int childIndex) {
        if (childIndex < 0 || childIndex > 7) throw new IllegalArgumentException("childIndex must be 0-7");
        long newCode = (code << 3) | childIndex;
        return new TreePath(newCode, depth + 1);
    }

    // 获取路径中的第 i 个方向（从根开始）
    public int getDirectionAt(int i) {
        if (i < 0 || i >= depth) throw new IllegalArgumentException("index out of range");
        return (int) ((code >> (3 * (depth - 1 - i))) & 0x7);
    }

    // 获取父路径
    public TreePath parent() {
        if (depth == 0) return null;
        return new TreePath(code >> 3, depth - 1);
    }

    public int getDepth() { return depth; }
    public long getCode() { return code; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        long temp = code;
        for (int i = 0; i < depth; i++) {
            sb.insert(0, "." + (temp & 0x7));
            temp >>= 3;
        }
        return !sb.isEmpty() ? sb.substring(1) : "root";
    }
}
