package com.director.client.gizmo.logic;

import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionSet;
import com.director.client.gizmo.Axis;
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
 * @author Director
 * @version 1.0
 */
public class TransformApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformApplier.class);
    
    private final SelectionManager selectionManager;
    
    // 拖拽过程中累积的小数部分，避免每帧舍入导致 0 位移
    private double moveCarryX = 0.0;
    private double moveCarryY = 0.0;
    private double moveCarryZ = 0.0;
    private double resizeCarryX = 0.0;
    private double resizeCarryY = 0.0;
    private double resizeCarryZ = 0.0;
    
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
        moveCarryX += delta.x;
        moveCarryY += delta.y;
        moveCarryZ += delta.z;
        
        int stepX = extractStep(moveCarryX);
        int stepY = extractStep(moveCarryY);
        int stepZ = extractStep(moveCarryZ);
        
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            LOGGER.debug("[TransformApplier] 偏移不足 1 方块，累积中...");
            return;
        }
        
        moveCarryX -= stepX;
        moveCarryY -= stepY;
        moveCarryZ -= stepZ;
        
        LOGGER.debug("[TransformApplier] 平移选区 Δ=({}, {}, {})", stepX, stepY, stepZ);
        
        selectionManager.offsetSelection(new BlockPos(stepX, stepY, stepZ));
        
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
        
        switch (axis) {
            case X -> resizeCarryX += delta;
            case Y -> resizeCarryY += delta;
            case Z -> resizeCarryZ += delta;
        }
        
        int deltaInt = switch (axis) {
            case X -> extractStep(resizeCarryX);
            case Y -> extractStep(resizeCarryY);
            case Z -> extractStep(resizeCarryZ);
        };
        
        if (deltaInt == 0) {
            LOGGER.debug("[TransformApplier] 轴 {} 增量不足 1 方块，累积中...", axis);
            return;
        }
        
        switch (axis) {
            case X -> resizeCarryX -= deltaInt;
            case Y -> resizeCarryY -= deltaInt;
            case Z -> resizeCarryZ -= deltaInt;
        }
        
        LOGGER.debug("[TransformApplier] 调整选区尺寸 轴={} Δ={}", axis, deltaInt);
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
     * 重置所有累积的拖拽位移（在一次拖拽开始/结束时调用）。
     */
    public void resetAccumulators() {
        moveCarryX = moveCarryY = moveCarryZ = 0.0;
        resizeCarryX = resizeCarryY = resizeCarryZ = 0.0;
    }

    private int extractStep(double value) {
        if (value >= 1.0) {
            return (int) Math.floor(value);
        }
        if (value <= -1.0) {
            return (int) Math.ceil(value);
        }
        return 0;
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
        // 可以通过 DirectorScreen 或 AnimationManager 查询
        return false;
    }
}

