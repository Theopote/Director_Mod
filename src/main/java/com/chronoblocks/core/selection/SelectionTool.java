package com.chronoblocks.core.selection;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 🪄 SelectionTool 接口
 * <p>
 * 定义所有选择工具（点击、框选、魔棒等）的通用行为。
 * 每种工具在世界中有独立的交互逻辑与视觉反馈。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface SelectionTool {

    /** 工具唯一 ID（如 "click", "box", "wand"） */
    String getId();

    /** 工具显示名 */
    String getDisplayName();

    /** 工具描述 */
    default String getDescription() {
        return "选择工具";
    }

    /** 工具图标（用于UI显示） */
    default String getIcon() {
        return "🔧";
    }

    /** 当工具激活时调用 */
    default void onActivate(World world) {}

    /** 当工具停用时调用 */
    default void onDeactivate(World world) {}

    /** 鼠标点击选择逻辑 */
    void onClick(World world, BlockPos pos, boolean isShiftDown);
    
    /** 鼠标右键点击逻辑（可选，用于两点式建盒等） */
    default void onRightClick(World world, BlockPos pos) {
        // 默认实现：无操作
    }

    /** 鼠标悬停逻辑（可选，用于预览） */
    default void onHover(World world, BlockPos pos) {}

    /** 拖拽选择开始 */
    default void onDragStart(World world, BlockPos start) {}

    /**
     * 拖拽开始（带射线命中结果，用于贴面采样等）。
     * 默认转调 {@link #onDragStart(World, BlockPos)}；套索等工具可重写以取表面点。
     */
    default void onDragStart(World world, BlockPos start, BlockHitResult hitOrNull) {
        onDragStart(world, start);
    }

    /** 拖拽中更新 */
    default void onDragUpdate(World world, BlockPos current) {}

    /**
     * 拖拽更新（带射线命中结果）。默认转调 {@link #onDragUpdate(World, BlockPos)}。
     */
    default void onDragUpdate(World world, BlockPos current, BlockHitResult hitOrNull) {
        onDragUpdate(world, current);
    }

    /** 拖拽结束 */
    default void onDragEnd(World world, BlockPos end) {}

    /**
     * 拖拽结束（带射线命中结果）。默认转调 {@link #onDragEnd(World, BlockPos)}。
     */
    default void onDragEnd(World world, BlockPos end, BlockHitResult hitOrNull) {
        onDragEnd(world, end);
    }

    /** 工具的每帧更新逻辑（用于动画或可视选区渲染） */
    default void tick(World world) {}

    /** 是否在当前帧显示辅助指示（例如选区框、方块高亮） */
    default boolean shouldRenderOverlay() { 
        return true; 
    }

    /** 获取当前选择状态 */
    default SelectionState getCurrentSelection() {
        return SelectionState.empty();
    }

    /** 清除当前选择 */
    default void clearSelection() {}
}
