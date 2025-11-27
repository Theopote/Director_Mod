package com.chronoblocks.core.animation.sequence.evaluator;

/**
 * 宏观流向：决定方块出现的整体波浪走势。
 */
public enum FlowType {
    NONE("不切分 (整体)"),
    PLANAR("平面切分 (Planar)"),
    SPHERICAL("球形扩散 (Spherical)"),
    CYLINDRICAL("柱状扩散 (Cylindrical)"),
    RANDOM("完全随机 (Random)");

    private final String displayName;

    FlowType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

