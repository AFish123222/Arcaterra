package com.fish.arcaterra.ui.hud;

public interface IHudElement {
        void render();               // 绘制内容
        boolean isVisible();         // 是否显示（可选）
}

