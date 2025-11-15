package com.chronoblocks.core.selection.block_filters;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 🔗 方块过滤器管道
 * 
 * 将多个 BlockFilter 组成管线，按顺序应用过滤规则。
 * - 支持链式调用
 * - 支持性能分析
 * - 遇到 false 即短路
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class BlockFilterPipeline {
    
    private final List<BlockFilter> filters = new ArrayList<>();
    private boolean profiling = false;
    private long lastNanos = 0;
    private long totalChecked = 0;
    
    /**
     * 添加过滤器到管道
     */
    public BlockFilterPipeline add(BlockFilter filter) {
        if (filter != null) {
            filters.add(filter);
        }
        return this;
    }
    
    /**
     * 批量添加过滤器
     */
    public BlockFilterPipeline addAll(List<BlockFilter> filterList) {
        if (filterList != null) {
            filters.addAll(filterList);
        }
        return this;
    }
    
    /**
     * 清空管道
     */
    public BlockFilterPipeline clear() {
        filters.clear();
        return this;
    }
    
    /**
     * 获取所有过滤器（只读）
     */
    public List<BlockFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }
    
    /**
     * 启用/禁用性能分析
     */
    public BlockFilterPipeline enableProfiling(boolean enable) {
        this.profiling = enable;
        return this;
    }
    
    /**
     * 测试方块是否通过管道中的所有过滤器
     */
    public boolean test(World world, BlockPos pos, BlockState state) {
        if (!profiling) {
            // 快速路径：无性能分析
            for (BlockFilter filter : filters) {
                if (!filter.test(world, pos, state)) {
                    return false;
                }
            }
            return true;
        }
        
        // 慢速路径：带性能分析
        long start = System.nanoTime();
        try {
            totalChecked++;
            for (BlockFilter filter : filters) {
                if (!filter.test(world, pos, state)) {
                    return false;
                }
            }
            return true;
        } finally {
            lastNanos = System.nanoTime() - start;
        }
    }
    
    /**
     * 获取上次测试耗时（纳秒）
     */
    public long getLastNanos() {
        return lastNanos;
    }
    
    /**
     * 获取总测试次数
     */
    public long getTotalChecked() {
        return totalChecked;
    }
    
    /**
     * 重置性能统计
     */
    public void resetStats() {
        lastNanos = 0;
        totalChecked = 0;
    }
    
    /**
     * 获取过滤器数量
     */
    public int size() {
        return filters.size();
    }
    
    /**
     * 判断管道是否为空
     */
    public boolean isEmpty() {
        return filters.isEmpty();
    }
    
    /**
     * 克隆管道（浅拷贝）
     */
    public BlockFilterPipeline clone() {
        BlockFilterPipeline copy = new BlockFilterPipeline();
        copy.addAll(this.filters);
        copy.profiling = this.profiling;
        return copy;
    }
}

