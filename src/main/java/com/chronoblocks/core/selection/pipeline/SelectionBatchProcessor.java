package com.chronoblocks.core.selection.pipeline;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionSet;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * SelectionBatchProcessor
 * - 在 worker 线程执行重计算
 * - 结果回到主线程（通过 MinecraftClient.execute）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class SelectionBatchProcessor {
    private static final SelectionBatchProcessor INSTANCE = new SelectionBatchProcessor();
    
    public static SelectionBatchProcessor get(){ 
        return INSTANCE; 
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()/2));

    private SelectionBatchProcessor(){}

    public Future<?> submit(String name, Supplier<Set<BlockPos>> workerJob) {
        return pool.submit(() -> {
            Set<BlockPos> result = workerJob.get();
            // 回到主线程合并
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                SelectionSet set = SelectionManager.get().getSelectionSet();
                set.clear();
                set.addAll(result);
            });
        });
    }

    public void shutdown() { 
        pool.shutdownNow(); 
    }
}
