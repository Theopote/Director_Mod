package com.chronoblocks.core.animation.sequence.evaluator;

/**
 * 微观填充：决定在同一个切面内方块的填充细节。
 */
public enum FillType {
    LINEAR_SCAN("线性扫描 (Linear)"),
    RASTER("打印机扫描 (Raster)"),
    RADIAL("径向扩散 (Radial)"),
    SPIRAL("螺旋 (Spiral)"),
    NOISE("伪随机散布 (Noise)");

    // 注意：FLOOD（泛洪蔓延）已移除 —— 原 BFS 逻辑未实现，避免用户误选。
    // 如需恢复，需在 SequenceEvaluator.computeFillScore 中实现真正的 BFS 泛洪算法。

    private final String displayName;

    FillType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

