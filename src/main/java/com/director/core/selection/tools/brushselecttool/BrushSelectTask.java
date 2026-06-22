package com.director.core.selection.tools.brushselecttool;

import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.tools.geometry.GeometryShape;
import com.director.core.selection.tools.geometry.GeometryShapeGenerator;
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
 * <p>
 * 基于几何形状的异步分帧刷选任务。
 * 支持球体、圆柱体、金字塔、立方体等多种形状。
 * 按批次遍历形状内的方块并支持取消、错误回调。
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
                            GeometryShape shape,
                            double radius,
                            double height,
                            boolean hollow,
                            BlockFilterPipeline pipeline,
                            int stepSize,
                            Callback callback) {
        this.world = world;
        this.pipeline = pipeline != null ? pipeline.clone() : null;
        this.stepSize = stepSize > 0 ? stepSize : DEFAULT_STEP_SIZE;
        this.callback = callback != null ? callback : new Callback() {};
        BlockPos immutableCenter = center.toImmutable();
        this.candidates = new ArrayList<>(generateGeometryCandidates(immutableCenter, shape, radius, height, hollow));

        LOGGER.info("[BrushSelectTask] 初始化：中心={}, 形状={}, 半径={}, 高度={}, 空心={}, 候选方块数={}",
            immutableCenter, shape, String.format("%.2f", radius), String.format("%.2f", height), hollow, candidates.size());
    }

    public static BrushSelectTask start(World world,
                                        BlockPos center,
                                        GeometryShape shape,
                                        double radius,
                                        double height,
                                        boolean hollow,
                                        BlockFilterPipeline pipeline,
                                        Callback callback) {
        if (world == null || center == null || radius <= 0.0) {
            return null;
        }
        BrushSelectTask task = new BrushSelectTask(world, center, shape, radius, height, hollow, pipeline, DEFAULT_STEP_SIZE, callback);
        ForkJoinPool.commonPool().execute(task);
        return task;
    }

    /**
     * 生成几何形状候选方块（供外部调用，用于预览等）
     */
    public static Set<BlockPos> generateGeometryCandidates(BlockPos center, GeometryShape shape, double radius, double height, boolean hollow) {
        com.director.core.selection.tools.geometry.GeometryConfig config = 
            new com.director.core.selection.tools.geometry.GeometryConfig();
        config.setRadius(radius);
        config.setHeight(height);
        config.setHollow(hollow);
        return GeometryShapeGenerator.generate(center, shape, config);
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            LOGGER.debug("[BrushSelectTask] 任务已取消");
        }
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

                BlockState state = world.getBlockState(pos);
                
                // 默认过滤空气方块：所有选择工具都只能选中非空气方块
                if (state.isAir()) {
                    continue;
                }

                if (pipeline == null) {
                    // 如果没有 pipeline，只检查非空气方块即可
                    result.add(pos);
                    continue;
                }

                // 如果有 pipeline，使用过滤器进一步筛选
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
        } catch (RuntimeException t) {
            LOGGER.error("[BrushSelectTask] 任务执行出错", t);
            notifyError(t);
        }
    }

    private void notifyCompleted(Set<BlockPos> finalResult) {
        runOnClientThread(() -> {
            try {
                LOGGER.info("[BrushSelectTask] 完成：共选择 {} 个方块", finalResult.size());
                callback.onCompleted(this, finalResult);
            } catch (RuntimeException t) {
                LOGGER.error("[BrushSelectTask] onCompleted 回调出错", t);
            }
        });
    }

    private void notifyCancelled() {
        runOnClientThread(() -> {
            try {
                callback.onCancelled(this);
            } catch (RuntimeException t) {
                LOGGER.error("[BrushSelectTask] onCancelled 回调出错", t);
            }
        });
    }

    private void notifyError(Throwable error) {
        runOnClientThread(() -> {
            try {
                callback.onError(this, error);
            } catch (RuntimeException t) {
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


