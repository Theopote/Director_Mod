package com.chronoblocks.core.selection.progressive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 🎯 渐进式泛洪控制器
 * 
 * 管理分帧执行的泛洪填充任务，支持：
 * - 逐帧更新预览
 * - 实时进度反馈
 * - 随时取消
 * - 暂停/恢复
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ProgressiveFloodController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressiveFloodController.class);
    
    private final MagicWandTask task;
    private final int stepsPerFrame;
    private final int maxBlocks;
    private final Consumer<ProgressUpdate> onProgress;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger currentStep = new AtomicInteger(0);
    
    private MagicWandTask.MagicWandTaskResult currentResult;
    private CompletableFuture<MagicWandTask.MagicWandTaskResult> executionFuture;
    
    /**
     * 进度更新数据
     */
    public record ProgressUpdate(
        int currentBlocks,
        int currentSteps,
        int totalSteps,
        double progress,
        boolean completed,
        MagicWandTask.MagicWandTaskResult partialResult
    ) {
        public String getProgressString() {
            return String.format("%.1f%% (%d blocks, %d/%d steps)", 
                progress * 100, currentBlocks, currentSteps, totalSteps);
        }
    }
    
    public ProgressiveFloodController(
        MagicWandTask task,
        int stepsPerFrame,
        int maxBlocks,
        Consumer<ProgressUpdate> onProgress
    ) {
        this.task = task;
        this.stepsPerFrame = stepsPerFrame;
        this.maxBlocks = maxBlocks;
        this.onProgress = onProgress;
    }
    
    /**
     * 开始执行
     */
    public CompletableFuture<MagicWandTask.MagicWandTaskResult> start() {
        if (running.get()) {
            LOGGER.warn("[ProgressiveFlood] 任务已在运行");
            return executionFuture;
        }
        
        running.set(true);
        cancelled.set(false);
        currentStep.set(0);
        
        LOGGER.info("[ProgressiveFlood] 开始渐进式泛洪，步长: {}", stepsPerFrame);
        
        executionFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return executeProgressive();
            } catch (Exception e) {
                LOGGER.error("[ProgressiveFlood] 执行失败", e);
                return null;
            } finally {
                running.set(false);
            }
        });
        
        return executionFuture;
    }
    
    /**
     * 渐进式执行主循环
     */
    private MagicWandTask.MagicWandTaskResult executeProgressive() {
        int maxSteps = maxBlocks * 10; // 预估最大步数
        int executedSteps = 0;
        
        while (!cancelled.get() && executedSteps < maxSteps) {
            // 检查暂停
            while (paused.get() && !cancelled.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (cancelled.get()) {
                break;
            }
            
            // 执行一帧
            currentResult = task.run(stepsPerFrame);
            executedSteps = currentResult.steps();
            currentStep.set(executedSteps);
            
            // 计算进度
            double progress = Math.min(1.0, (double) currentResult.size() / maxBlocks);
            
            // 回调通知
            if (onProgress != null) {
                ProgressUpdate update = new ProgressUpdate(
                    currentResult.size(),
                    executedSteps,
                    maxSteps,
                    progress,
                    currentResult.reachedLimit() || currentResult.cancelled(),
                    currentResult
                );
                
                onProgress.accept(update);
            }
            
            // 检查是否完成
            if (currentResult.reachedLimit() || currentResult.cancelled() || currentResult.isEmpty()) {
                LOGGER.info("[ProgressiveFlood] 完成：{} 个方块，{} 步", 
                    currentResult.size(), executedSteps);
                break;
            }
            
            // 帧延迟（避免占用过多CPU）
            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return currentResult;
    }
    
    /**
     * 取消执行
     */
    public void cancel() {
        if (!running.get()) {
            return;
        }
        
        LOGGER.info("[ProgressiveFlood] 取消任务");
        cancelled.set(true);
        task.cancel();
        
        if (executionFuture != null) {
            executionFuture.cancel(true);
        }
    }
    
    /**
     * 暂停执行
     */
    public void pause() {
        if (!running.get()) {
            return;
        }
        
        LOGGER.debug("[ProgressiveFlood] 暂停任务");
        paused.set(true);
    }
    
    /**
     * 恢复执行
     */
    public void resume() {
        if (!running.get()) {
            return;
        }
        
        LOGGER.debug("[ProgressiveFlood] 恢复任务");
        paused.set(false);
    }
    
    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 是否已暂停
     */
    public boolean isPaused() {
        return paused.get();
    }
    
    /**
     * 是否已取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    /**
     * 获取当前进度
     */
    public int getCurrentStep() {
        return currentStep.get();
    }
    
    /**
     * 获取当前结果
     */
    public MagicWandTask.MagicWandTaskResult getCurrentResult() {
        return currentResult;
    }
}

