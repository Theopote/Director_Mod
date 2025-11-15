package com.chronoblocks.client.gizmo.logic;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionSet;
import com.chronoblocks.client.gizmo.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧩 TransformApplier - 变换应用器
 * 
 * 将 Gizmo 的操作结果写回目标：
 *  - 选区 (Selection Bounds)
 *  - 动画 Actor Transform (未来扩展)
 * 
 * 支持操作：
 *  - 平移选区
 *  - 调整选区边界大小
 *  - 应用旋转（目前仅记录角度，不直接旋转方块）
 * 
 * 注意：
 * - Minecraft 方块不可旋转（除非方向方块或 NBT 控制）
 * - 旋转操作主要用于动画编辑模式，计算旋转目标而非即时旋转方块
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public class TransformApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformApplier.class);
    
    private final SelectionManager selectionManager;
    
    // 动画管理器（未来扩展）
    // private final AnimationManager animationManager;
    
    public TransformApplier(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }
    
    /**
     * 平移选区
     * 
     * 使用 SelectionManager.offsetSelection() 方法，统一选区操作逻辑
     * 
     * @param delta 平移向量
     */
    public void applyMove(Vec3d delta) {
        SelectionSet set = selectionManager.getSelectionSet();
        
        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] 选区为空，跳过平移");
            return;
        }
        
        // 将 delta 转换为整数 BlockPos 增量
        BlockPos offset = new BlockPos(
            (int) Math.round(delta.x),
            (int) Math.round(delta.y),
            (int) Math.round(delta.z)
        );
        
        if (offset.getX() == 0 && offset.getY() == 0 && offset.getZ() == 0) {
            LOGGER.debug("[TransformApplier] 偏移为零，跳过平移");
            return; // 无变化
        }
        
        LOGGER.debug("[TransformApplier] 平移选区 Δ=({}, {}, {})", offset.getX(), offset.getY(), offset.getZ());
        
        // 使用 SelectionManager 的统一方法
        selectionManager.offsetSelection(offset);
        
        LOGGER.info("[TransformApplier] 选区已平移：{} 个方块", selectionManager.getSelectionSet().size());
    }
    
    /**
     * 拉伸选区（沿一个轴方向扩大或缩小）
     * 
     * 使用 SelectionManager.expandSelection() 方法，统一选区操作逻辑
     * 
     * @param axis 轴
     * @param delta 变化量（正数扩大，负数缩小）
     */
    public void applyResize(Axis axis, double delta) {
        SelectionSet set = selectionManager.getSelectionSet();
        
        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] 选区为空，跳过调整大小");
            return;
        }
        
        int deltaInt = (int) Math.round(delta);
        
        if (deltaInt == 0) {
            LOGGER.debug("[TransformApplier] 增量为零，跳过调整");
            return;
        }
        
        LOGGER.debug("[TransformApplier] 调整选区尺寸 轴={} Δ={}", axis, deltaInt);
        
        // 使用 SelectionManager 的统一方法
        selectionManager.expandSelection(axis, deltaInt);
        
        LOGGER.info("[TransformApplier] 选区尺寸已调整：轴={}, 增量={}, 新选区大小={}", 
                axis, deltaInt, selectionManager.getSelectionSet().size());
    }
    
    /**
     * 从角点调整选区大小（用于 CornerHandle）
     * 
     * @param delta 变化向量
     * @param cornerSign 角点符号（表示哪个角）
     */
    public void applyResizeFromCorner(Vec3d delta, net.minecraft.util.math.Vec3i cornerSign) {
        SelectionSet set = selectionManager.getSelectionSet();
        
        if (set.isEmpty()) {
            return;
        }
        
        LOGGER.debug("[TransformApplier] 从角点调整选区大小 Δ=({}, {}, {})", delta.x, delta.y, delta.z);
        
        // 简化为沿各轴分别调整（实际应该更智能地处理角点）
        // 这里简化实现，实际可以调用 applyResize 三次
        if (Math.abs(delta.x) > 0.1) {
            applyResize(Axis.X, delta.x);
        }
        if (Math.abs(delta.y) > 0.1) {
            applyResize(Axis.Y, delta.y);
        }
        if (Math.abs(delta.z) > 0.1) {
            applyResize(Axis.Z, delta.z);
        }
    }
    
    /**
     * 应用缩放（通用方法，用于 ResizeHandle）
     * 
     * @param axisDelta 沿轴方向的缩放增量
     */
    public void applyResize(Vec3d axisDelta) {
        LOGGER.debug("[TransformApplier] 选区缩放 Δ=({}, {}, {})", axisDelta.x, axisDelta.y, axisDelta.z);
        
        SelectionSet set = selectionManager.getSelectionSet();
        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] 选区为空，跳过缩放");
            return;
        }
        
        // 沿各轴分别应用缩放
        if (Math.abs(axisDelta.x) > 0.01) {
            applyResize(Axis.X, axisDelta.x);
        }
        if (Math.abs(axisDelta.y) > 0.01) {
            applyResize(Axis.Y, axisDelta.y);
        }
        if (Math.abs(axisDelta.z) > 0.01) {
            applyResize(Axis.Z, axisDelta.z);
        }
    }
    
    /**
     * 应用旋转（用于动画 Actor）
     * 
     * 注意：Minecraft 方块不可直接旋转，此方法主要用于：
     * - 动画编辑模式：记录旋转关键帧
     * - 未来扩展：应用到方向方块或 NBT 控制的旋转
     * 
     * @param angle 旋转角度（弧度）
     * @param axis 旋转轴
     */
    public void applyRotation(double angle, Axis axis) {
        double angleDegrees = Math.toDegrees(angle);
        LOGGER.debug("[TransformApplier] 旋转目标 ({}° around {})", angleDegrees, axis);
        
        // TODO: 调用 AnimationManager.applyTransformRotation(angle, axis)
        // 当前仅记录日志，未来扩展时连接到动画系统
    }
    
    /**
     * 检查是否在动画编辑模式
     * 
     * @return true 如果在动画编辑模式
     */
    public boolean isAnimationMode() {
        // TODO: 检查当前是否在动画编辑模式
        // 可以通过 ChronoBlocksScreen 或 AnimationManager 查询
        return false;
    }
}

