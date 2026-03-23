package com.director.core.animation.sequence.evaluator;

/**
 * 起始锚点：决定波浪从哪个位置开始扩散。
 */
public enum OriginType {
    MIN_CORNER("最小角 (Corner)"),
    CENTER("几何中心 (Center)"),
    BOTTOM_CENTER("底部中心 (Bottom)"),
    TOP_CENTER("顶部中心 (Top)"),
    CUSTOM("自定义 (Custom)");

    private final String displayName;

    OriginType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

