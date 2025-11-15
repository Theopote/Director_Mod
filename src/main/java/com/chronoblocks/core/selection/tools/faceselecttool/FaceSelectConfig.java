package com.chronoblocks.core.selection.tools.faceselecttool;

/**
 * ⚙️ FaceSelectConfig - 面选择工具配置
 *
 * 管理面选择工具的运行参数，确保在性能与精确度之间取得平衡。
 * 支持 UI 动态调节，便于根据场景调整传播限制、透明方块处理等策略。
 */
public final class FaceSelectConfig {

    /** 默认最大选择数量（防止无限扩散导致卡顿） */
    private static final int DEFAULT_MAX_SELECTION = 50_000;

    /** 默认每帧处理的节点数量（步数预算） */
    private static final int DEFAULT_STEP_SIZE = 2_048;

    /** 默认每帧时间预算（纳秒，约 8ms） */
    private static final long DEFAULT_FRAME_BUDGET_NANOS = 8_000_000L;

    /** 选择数量上限 */
    private int maxSelectionSize = DEFAULT_MAX_SELECTION;

    /** 是否忽略透明方块（如玻璃、空气） */
    private boolean ignoreTransparent = true;

    /** 是否按材质传播（使用 MapColor 判定） */
    private boolean propagateByMaterial = false;

    /** 是否要求方块状态完全一致 */
    private boolean strictBlockState = false;

    /** 每帧最大处理步数 */
    private int stepSize = DEFAULT_STEP_SIZE;

    /** 每帧最大耗时（纳秒） */
    private long frameBudgetNanos = DEFAULT_FRAME_BUDGET_NANOS;

    public int getMaxSelectionSize() {
        return maxSelectionSize;
    }

    public void setMaxSelectionSize(int maxSelectionSize) {
        this.maxSelectionSize = Math.max(1, Math.min(maxSelectionSize, 500_000));
    }

    public boolean isIgnoreTransparent() {
        return ignoreTransparent;
    }

    public void setIgnoreTransparent(boolean ignoreTransparent) {
        this.ignoreTransparent = ignoreTransparent;
    }

    public boolean isPropagateByMaterial() {
        return propagateByMaterial;
    }

    public void setPropagateByMaterial(boolean propagateByMaterial) {
        this.propagateByMaterial = propagateByMaterial;
    }

    public boolean isStrictBlockState() {
        return strictBlockState;
    }

    public void setStrictBlockState(boolean strictBlockState) {
        this.strictBlockState = strictBlockState;
    }

    public int getStepSize() {
        return stepSize;
    }

    public void setStepSize(int stepSize) {
        this.stepSize = Math.max(64, Math.min(stepSize, 32_768));
    }

    public long getFrameBudgetNanos() {
        return frameBudgetNanos;
    }

    public void setFrameBudgetNanos(long frameBudgetNanos) {
        this.frameBudgetNanos = Math.max(1_000_000L, Math.min(frameBudgetNanos, 33_000_000L));
    }
}

