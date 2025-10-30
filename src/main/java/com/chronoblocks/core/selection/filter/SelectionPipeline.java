package com.chronoblocks.core.selection.filter;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将多个 SelectionFilter 组成"管线"。
 * - 按顺序应用，遇到 false 即短路
 * - 支持性能统计（可选）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionPipeline {

    private final List<SelectionFilter> filters = new ArrayList<>();
    private boolean profiling = false;
    private long lastNanos = 0;
    private long totalChecked = 0;

    public SelectionPipeline add(SelectionFilter f) {
        if (f != null) filters.add(f);
        return this;
    }

    public SelectionPipeline addAll(List<SelectionFilter> list) {
        if (list != null) filters.addAll(list);
        return this;
    }

    public SelectionPipeline clear() { 
        filters.clear(); 
        return this;
    }

    public List<SelectionFilter> getFilters() { 
        return Collections.unmodifiableList(filters); 
    }

    public SelectionPipeline enableProfiling(boolean enable) { 
        this.profiling = enable; 
        return this;
    }

    public boolean test(World w, BlockPos p, BlockState s) {
        if (!profiling) {
            for (SelectionFilter f: filters) if (!f.test(w,p,s)) return false;
            return true;
        }
        long start = System.nanoTime();
        try {
            totalChecked++;
            for (SelectionFilter f: filters) if (!f.test(w,p,s)) return false;
            return true;
        } finally {
            lastNanos = System.nanoTime() - start;
        }
    }

    public long getLastNanos() { 
        return lastNanos; 
    }
    
    public long getTotalChecked() { 
        return totalChecked; 
    }
}
