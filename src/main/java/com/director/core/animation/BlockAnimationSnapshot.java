package com.director.core.animation;

import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

/**
 * BlockAnimationSnapshot
 * ----------------------
 * 单个方块在某一帧的"动画采样结果"，由 BlockAnimationEngine.sample() 生成，
 * 再交给 BlockAnimationRenderer 在渲染管线中实际绘制。
 *
 * 约定：
 * - transform 只包含“局部动画变换”（围绕方块自身中心的位移/旋转/缩放）
 * - 世界坐标（BlockPos → 世界平移）由 Renderer 统一负责：
 *      matrices.translate(pos.getX(), pos.getY(), pos.getZ());
 *      然后再乘上 snapshot.transform
 *
 * 这个类只负责"数据承载"，不参与任何逻辑。
 *
 * @author Director Team
 * @version 2.1
 */
public final class BlockAnimationSnapshot {

    private final BlockPos pos;
    private final BlockState blockState;

    /**
     * 渲染用矩阵（只包含局部平移/旋转/缩放等），不包含世界坐标。
     * Renderer 会：
     *   1) 先平移到方块世界坐标
     *   2) 再将该矩阵乘到当前 MatrixStack 上
     */
    /**
     * 局部动画矩阵。
     *
     * <p>性能约定：transform == null 表示单位矩阵（无局部变换）。
     * 这样可以避免在大量静态方块上分配 MatrixStack / 做矩阵比较。
     */
    private MatrixStack transform;

    /** 当前是否需要渲染（false 直接跳过） */
    private boolean visible;

    /** 当前 alpha（0=完全透明, 1=完全不透明） */
    private float alpha = 1.0f;

    /** 原始进度 t_raw（0..1，未经过 easing） */
    private float rawProgress;

    /** 缓动后的进度 t_eased（0..1，应用了 EasingFunction） */
    private float easedProgress;

    /** 此方块在序列中的归一化时间（0..1），来自 BlockSequenceEntry.normalizedTime */
    private float sequenceNormalizedTime;

    /**
     * 是否需要阻止原版世界网格渲染（与 BlockVisualStateManager / Mixin 配合），
     * 例如：遮挡淡化 / ghost 渲染时，把真实网格隐藏，只画一份透明的。
     */
    private boolean suppressWorldRendering;

    public BlockAnimationSnapshot(BlockPos pos, BlockState blockState) {
        this.pos = pos;
        this.blockState = blockState;
        this.transform = null; // 默认单位矩阵（无局部变换）
        this.visible = false;
    }

    // ========== Getters / Setters ==========

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public MatrixStack getTransform() {
        return transform;
    }

    public void setTransform(MatrixStack transform) {
        // 允许 null：代表单位矩阵
        this.transform = transform;
    }

    public boolean isVisible() {
        return visible && alpha > 0.0f;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public float getRawProgress() {
        return rawProgress;
    }

    public void setRawProgress(float rawProgress) {
        this.rawProgress = rawProgress;
    }

    public float getEasedProgress() {
        return easedProgress;
    }

    public void setEasedProgress(float easedProgress) {
        this.easedProgress = easedProgress;
    }

    public float getSequenceNormalizedTime() {
        return sequenceNormalizedTime;
    }

    public void setSequenceNormalizedTime(float sequenceNormalizedTime) {
        this.sequenceNormalizedTime = sequenceNormalizedTime;
    }

    public boolean isSuppressWorldRendering() {
        return suppressWorldRendering;
    }

    public void setSuppressWorldRendering(boolean suppressWorldRendering) {
        this.suppressWorldRendering = suppressWorldRendering;
    }

    // ====== 便捷 API ======

    /** 标记为"完全出现"状态（1.0） */
    public void markFullyVisible() {
        this.visible = true;
        this.alpha = 1.0f;
        this.rawProgress = 1.0f;
        this.easedProgress = 1.0f;
    }

    /** 标记为"完全隐藏"状态（0.0） */
    public void markHidden() {
        this.visible = false;
        this.alpha = 0.0f;
        this.rawProgress = 0.0f;
        this.easedProgress = 0.0f;
    }
}
