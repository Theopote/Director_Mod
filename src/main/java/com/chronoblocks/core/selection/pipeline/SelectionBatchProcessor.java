package com.chronoblocks.core.selection.pipeline;


import java.util.concurrent.*;

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

    public void shutdown() {
        pool.shutdownNow(); 
    }
}
