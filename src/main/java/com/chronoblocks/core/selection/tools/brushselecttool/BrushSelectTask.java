package com.chronoblocks.core.selection.tools.brushselecttool;

import com.chronoblocks.core.selection.block_filters.BlockFilterPipeline;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🧵 BrushSelectTask
 *
 * 基于球形区域的异步分帧刷选任务。
 * 按批次遍历球体内的方块并支持取消、错误回调。
 */
public final class BrushSelectTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrushSelectTask.class);

    private static final int DEFAULT_STEP_SIZE = 2048;

    private final World world;
    private final BlockFilterPipeline pipeline;
    private final int stepSize;
    private final List<BlockPos> candidates;
    private final Set<BlockPos> result = new LinkedHashSet<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Callback callback;

    private int index = 0;

    public interface Callback {
        default void onCompleted(BrushSelectTask task, Set<BlockPos> result) {}

        default void onCancelled(BrushSelectTask task) {}

        default void onError(BrushSelectTask task, Throwable error) {}
    }

    private BrushSelectTask(World world,
                            BlockPos center,
                            double radius,
                            BlockFilterPipeline pipeline,
                            int stepSize,
                            Callback callback) {
        this.world = world;
        this.pipeline = pipeline != null ? pipeline.clone() : null;
        this.stepSize = stepSize > 0 ? stepSize : DEFAULT_STEP_SIZE;
        this.callback = callback != null ? callback : new Callback() {};
        BlockPos immutableCenter = center.toImmutable();
        this.candidates = generateCandidates(immutableCenter, radius);

        LOGGER.info("[BrushSelectTask] 初始化：中心={}, 半径={}, 候选方块数={}",
            immutableCenter, String.format("%.2f", radius), candidates.size());
    }

    public static BrushSelectTask start(World world,
                                        BlockPos center,
                                        double radius,
                                        BlockFilterPipeline pipeline,
                                        Callback callback) {
        if (world == null || center == null || radius <= 0.0) {
            return null;
        }
        BrushSelectTask task = new BrushSelectTask(world, center, radius, pipeline, DEFAULT_STEP_SIZE, callback);
        ForkJoinPool.commonPool().execute(task);
        return task;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            LOGGER.debug("[BrushSelectTask] 任务已取消");
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public int getTotalCandidateCount() {
        return candidates.size();
    }

    public int getProcessedCount() {
        return index;
    }

    @Override
    public void run() {
        if (cancelled.get()) {
            notifyCancelled();
            return;
        }

        try {
            int processedThisRun = 0;
            int total = candidates.size();

            while (index < total && processedThisRun < stepSize && !cancelled.get()) {
                BlockPos pos = candidates.get(index++);
                processedThisRun++;

                if (pipeline == null) {
                    result.add(pos);
                    continue;
                }

                BlockState state = world.getBlockState(pos);
                if (pipeline.test(world, pos, state)) {
                    result.add(pos);
                }
            }

            if (cancelled.get()) {
                notifyCancelled();
                return;
            }

            if (index >= total) {
                notifyCompleted(Collections.unmodifiableSet(result));
            } else {
                ForkJoinPool.commonPool().execute(this);
            }
        } catch (Throwable t) {
            LOGGER.error("[BrushSelectTask] 任务执行出错", t);
            notifyError(t);
        }
    }

    private List<BlockPos> generateCandidates(BlockPos center, double radius) {
        List<BlockPos> list = new ArrayList<>();
        double clamped = Math.max(0.5, Math.min(radius, 64.0));
        int intRadius = (int) Math.ceil(clamped);
        double radiusSq = clamped * clamped;

        for (int dx = -intRadius; dx <= intRadius; dx++) {
            for (int dy = -intRadius; dy <= intRadius; dy++) {
                for (int dz = -intRadius; dz <= intRadius; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq <= radiusSq + 1e-4) {
                        list.add(center.add(dx, dy, dz));
                    }
                }
            }
        }
        return list;
    }

    private void notifyCompleted(Set<BlockPos> finalResult) {
        runOnClientThread(() -> {
            try {
                LOGGER.info("[BrushSelectTask] 完成：共选择 {} 个方块", finalResult.size());
                callback.onCompleted(this, finalResult);
            } catch (Throwable t) {
                LOGGER.error("[BrushSelectTask] onCompleted 回调出错", t);
            }
        });
    }

    private void notifyCancelled() {
        runOnClientThread(() -> {
            try {
                callback.onCancelled(this);
            } catch (Throwable t) {
                LOGGER.error("[BrushSelectTask] onCancelled 回调出错", t);
            }
        });
    }

    private void notifyError(Throwable error) {
        runOnClientThread(() -> {
            try {
                callback.onError(this, error);
            } catch (Throwable t) {
                LOGGER.error("[BrushSelectTask] onError 回调出错", t);
            }
        });
    }

    private void runOnClientThread(Runnable runnable) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(runnable);
        } else {
            runnable.run();
        }
    }
}


