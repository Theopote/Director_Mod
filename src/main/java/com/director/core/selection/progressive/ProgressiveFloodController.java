package com.director.core.selection.progressive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.core.selection.tools.magicwandtool.MagicWandTask;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 🎯 渐进式泛洪控制器
 * <p>
 * 管理分帧执行的泛洪填充任务，支持：
 * - 逐帧更新预览
 * - 实时进度反馈
 * - 随时取消
 * - 暂停/恢复
 * 
 * @author Director Team
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
        
        executionFuture = new CompletableFuture<>();
        scheduleNextStep();

        return executionFuture;
    }

    private void scheduleNextStep() {
        if (cancelled.get()) {
            running.set(false);
            executionFuture.cancel(false);
            return;
        }
        CompletableFuture.runAsync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                running.set(false);
                executionFuture.completeExceptionally(new IllegalStateException("MinecraftClient 不可用"));
                return;
            }
            if (!task.isForWorld(client.world)) {
                task.cancel();
                running.set(false);
                executionFuture.cancel(false);
                return;
            }
            client.execute(() -> {
                if (cancelled.get()) {
                    running.set(false);
                    executionFuture.cancel(false);
                    return;
                }
                if (paused.get()) {
                    scheduleNextStep();
                    return;
                }
                try {
                    currentResult = task.run(stepsPerFrame);
                    currentStep.set(currentResult.steps());
                    double progress = Math.min(1.0, (double) currentResult.size() / maxBlocks);
                    if (onProgress != null) {
                        onProgress.accept(new ProgressUpdate(currentResult.size(), currentResult.steps(),
                            maxBlocks * 10, progress, task.isFinished(), currentResult));
                    }
                    if (task.isFinished()) {
                        running.set(false);
                        executionFuture.complete(currentResult);
                    } else {
                        scheduleNextStep();
                    }
                } catch (Exception e) {
                    running.set(false);
                    LOGGER.error("[ProgressiveFlood] 执行失败", e);
                    executionFuture.completeExceptionally(e);
                }
            });
        });
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

