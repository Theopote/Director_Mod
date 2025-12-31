package com.chronoblocks.core.animation.sequence.evaluator;

/**
 * 微观填充：决定在同一个切面内方块的填充细节。
 */
public enum FillType {
    LINEAR_SCAN("线性扫描 (Linear)"),
    RASTER("打印机扫描 (Raster)"),
    RADIAL("径向扩散 (Radial)"),
    SPIRAL("螺旋 (Spiral)"),
    NOISE("平滑噪声 (Perlin)"),
    FLOOD("泛洪蔓延 (Flood)");

    private final String displayName;

    FillType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

