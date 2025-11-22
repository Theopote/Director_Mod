package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 波浪式执行策略
 * 
 * <p>按行列或距离波浪式执行方块动画。
 * 方块按照指定方向（X、Y、Z）的坐标值分组，形成波浪效果。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与 GROW_UP 组合：从下到上，波浪式出现</li>
 *   <li>与 EXPAND 组合：从中心扩散，波浪式执行</li>
 *   <li>创造波浪、涟漪等视觉效果</li>
 * </ul>
 * 
 * <p>参数：
 * <ul>
 *   <li>waveDirection：波浪方向（"X", "Y", "Z" 或组合，如 "XY"）</li>
 *   <li>waveInterval：波幅间隔（单位：方块，默认1.0）</li>
 *   <li>waveStart：起始位置（"min", "max", "center"）</li>
 * </ul>
 */
public class WaveStrategy implements SequenceStrategy {
    
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
        
        // 获取参数
        String direction = params != null ? params.getWaveDirection() : "Y";
        float interval = params != null ? params.getWaveInterval() : 1.0f;
        String start = params != null ? params.getWaveStart() : "min";
        
        // 确定主要方向（取第一个字符）
        char primaryDir = direction.length() > 0 ? direction.charAt(0) : 'Y';
        
        // 计算每个方块的"波前"值（根据主要方向）
        List<BlockPosWithWave> blocksWithWave = new ArrayList<>();
        for (BlockPos pos : blocks) {
            float waveValue = getWaveValue(pos, bbox, primaryDir, start);
            blocksWithWave.add(new BlockPosWithWave(pos, waveValue));
        }
        
        // 按波前值排序（从小到大，形成从起始点向外扩散的波浪）
        blocksWithWave.sort(Comparator.comparingDouble(b -> b.waveValue));
        
        // 如果间隔大于1，可以进一步分组
        if (interval > 1.0f) {
            return groupByInterval(blocksWithWave, interval);
        }
        
        // 提取排序后的方块列表
        List<BlockPos> result = new ArrayList<>();
        for (BlockPosWithWave bw : blocksWithWave) {
            result.add(bw.pos);
        }
        
        return result;
    }
    
    /**
     * 计算方块的波前值
     */
    private float getWaveValue(BlockPos pos, Container.BoundingBox bbox, char direction, String start) {
        BlockPos min = bbox.getMin();
        BlockPos max = bbox.getMax();
        
        int coord;
        int minCoord;
        int maxCoord;
        
        switch (direction) {
            case 'X':
            case 'x':
                coord = pos.getX();
                minCoord = min.getX();
                maxCoord = max.getX();
                break;
            case 'Z':
            case 'z':
                coord = pos.getZ();
                minCoord = min.getZ();
                maxCoord = max.getZ();
                break;
            case 'Y':
            case 'y':
            default:
                coord = pos.getY();
                minCoord = min.getY();
                maxCoord = max.getY();
                break;
        }
        
        // 根据起始位置计算波前值
        if ("max".equals(start)) {
            // 从最大值开始，向最小值扩散
            return maxCoord - coord;
        } else if ("center".equals(start)) {
            // 从中心开始，向两边扩散
            int center = (minCoord + maxCoord) / 2;
            return Math.abs(coord - center);
        } else {
            // 默认从最小值开始，向最大值扩散
            return coord - minCoord;
        }
    }
    
    /**
     * 按间隔分组方块（用于创建更明显的波浪效果）
     */
    private List<BlockPos> groupByInterval(List<BlockPosWithWave> blocksWithWave, float interval) {
        List<BlockPos> result = new ArrayList<>();
        
        if (blocksWithWave.isEmpty()) {
            return result;
        }
        
        // 按间隔分组
        float currentGroup = blocksWithWave.get(0).waveValue;
        List<BlockPos> currentGroupBlocks = new ArrayList<>();
        
        for (BlockPosWithWave bw : blocksWithWave) {
            if (bw.waveValue >= currentGroup + interval) {
                // 开始新的一组
                result.addAll(currentGroupBlocks);
                currentGroupBlocks.clear();
                currentGroup = bw.waveValue;
            }
            currentGroupBlocks.add(bw.pos);
        }
        
        // 添加最后一组
        result.addAll(currentGroupBlocks);
        
        return result;
    }
    
    /**
     * 方块与波前值的包装类
     */
    private static class BlockPosWithWave {
        final BlockPos pos;
        final float waveValue;
        
        BlockPosWithWave(BlockPos pos, float waveValue) {
            this.pos = pos;
            this.waveValue = waveValue;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Wave";
    }
    
    @Override
    public String getDisplayName() {
        return "波浪式";
    }
    
    @Override
    public String getDescription() {
        return "按行列或距离波浪式执行方块动画，创造波浪、涟漪等视觉效果";
    }
}

