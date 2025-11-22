package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 螺旋式执行策略
 * 
 * <p>从中心向外螺旋式执行方块动画。
 * 方块按照到中心点的距离和角度排序，形成螺旋效果。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与 EXPAND 组合：从中心螺旋式扩散</li>
 *   <li>创造螺旋、旋转等视觉效果</li>
 * </ul>
 * 
 * <p>参数：
 * <ul>
 *   <li>spiralStart：螺旋起始方向（"X", "Y", "Z"，默认"Y"）</li>
 *   <li>spiralDirection：螺旋方向（"clockwise"顺时针或"counterclockwise"逆时针）</li>
 * </ul>
 */
public class SpiralStrategy implements SequenceStrategy {
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (container == null) {
            return new ArrayList<>(blocks);
        }
        
        Container.BoundingBox bbox = container.getBoundingBox();
        if (bbox == null) {
            return new ArrayList<>(blocks);
        }
        
        // 获取中心点
        BlockPos center = bbox.getCenter();
        
        // 获取参数
        String startDir = params != null ? params.getSpiralStart() : "Y";
        String direction = params != null ? params.getSpiralDirection() : "clockwise";
        boolean clockwise = "clockwise".equalsIgnoreCase(direction);
        
        // 计算每个方块到中心的距离和角度
        List<BlockPosWithSpiral> blocksWithSpiral = new ArrayList<>();
        for (BlockPos pos : blocks) {
            double distance = calculateDistance(pos, center, startDir);
            double angle = calculateAngle(pos, center, startDir, clockwise);
            blocksWithSpiral.add(new BlockPosWithSpiral(pos, distance, angle));
        }
        
        // 排序：先按距离（从近到远），距离相同时按角度（形成螺旋）
        blocksWithSpiral.sort(Comparator
            .comparingDouble((BlockPosWithSpiral b) -> b.distance)
            .thenComparingDouble(b -> b.angle)
        );
        
        // 提取排序后的方块列表
        List<BlockPos> result = new ArrayList<>();
        for (BlockPosWithSpiral bs : blocksWithSpiral) {
            result.add(bs.pos);
        }
        
        return result;
    }
    
    /**
     * 计算方块到中心的距离（在垂直于起始方向的平面上）
     */
    private double calculateDistance(BlockPos pos, BlockPos center, String startDir) {
        char dir = startDir.length() > 0 ? startDir.charAt(0) : 'Y';
        
        switch (dir) {
            case 'X':
            case 'x':
                // 在 YZ 平面上计算距离
                int dy = pos.getY() - center.getY();
                int dz = pos.getZ() - center.getZ();
                return Math.sqrt(dy * dy + dz * dz);
            case 'Z':
            case 'z':
                // 在 XY 平面上计算距离
                int dx = pos.getX() - center.getX();
                int dy2 = pos.getY() - center.getY();
                return Math.sqrt(dx * dx + dy2 * dy2);
            case 'Y':
            case 'y':
            default:
                // 在 XZ 平面上计算距离（默认）
                int dx2 = pos.getX() - center.getX();
                int dz2 = pos.getZ() - center.getZ();
                return Math.sqrt(dx2 * dx2 + dz2 * dz2);
        }
    }
    
    /**
     * 计算方块相对于中心的角度（用于螺旋排序）
     */
    private double calculateAngle(BlockPos pos, BlockPos center, String startDir, boolean clockwise) {
        char dir = startDir.length() > 0 ? startDir.charAt(0) : 'Y';
        
        double angle;
        switch (dir) {
            case 'X':
            case 'x':
                // 在 YZ 平面上计算角度
                int dy = pos.getY() - center.getY();
                int dz = pos.getZ() - center.getZ();
                angle = Math.atan2(dz, dy);
                break;
            case 'Z':
            case 'z':
                // 在 XY 平面上计算角度
                int dx = pos.getX() - center.getX();
                int dy2 = pos.getY() - center.getY();
                angle = Math.atan2(dy2, dx);
                break;
            case 'Y':
            case 'y':
            default:
                // 在 XZ 平面上计算角度（默认）
                int dx2 = pos.getX() - center.getX();
                int dz2 = pos.getZ() - center.getZ();
                angle = Math.atan2(dz2, dx2);
                break;
        }
        
        // 转换为 0-2π 范围
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        
        // 如果是逆时针，反转角度
        if (!clockwise) {
            angle = 2 * Math.PI - angle;
        }
        
        return angle;
    }
    
    /**
     * 方块与螺旋信息的包装类
     */
    private static class BlockPosWithSpiral {
        final BlockPos pos;
        final double distance;
        final double angle;
        
        BlockPosWithSpiral(BlockPos pos, double distance, double angle) {
            this.pos = pos;
            this.distance = distance;
            this.angle = angle;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Spiral";
    }
    
    @Override
    public String getDisplayName() {
        return "螺旋式";
    }
    
    @Override
    public String getDescription() {
        return "从中心向外螺旋式执行方块动画，创造螺旋、旋转等视觉效果";
    }
}

