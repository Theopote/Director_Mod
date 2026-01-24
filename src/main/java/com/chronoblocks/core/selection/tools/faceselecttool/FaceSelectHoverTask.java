package com.chronoblocks.core.selection.tools.faceselecttool;

import com.chronoblocks.core.selection.block_filters.BlockFilter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 💠 FaceSelectHoverTask
 * <p>
 * 面选择工具的悬停预览任务。
 * - 在指定平面内进行 Flood Fill
 * - 分批回调，逐步刷新预览，避免一次性卡顿
 * - 不修改 SelectionState，仅通过回调提供预览数据
 */
public class FaceSelectHoverTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceSelectHoverTask.class);

    private static final int DEFAULT_MAX_BLOCKS = 8_000;
    private static final int DEFAULT_STEP_SIZE = 1_024;

    public record Result(
        Set<BlockPos> blocks,
        Map<BlockPos, Integer> depthMap,
        BlockPos origin,
        Direction face,
        boolean reachedLimit
    ) {}

    public interface Callback {
        void onUpdate(Result result);
        void onCompleted(Result result);
        void onCancelled();
    }

    private final World world;
    private final BlockPos startPos;
    private final Direction face;
    private final BlockState targetState;
    private final BlockFilter blockFilter;
    private final int maxBlocks;
    private final int stepSize;

    private volatile boolean cancelled = false;
    private volatile boolean finished = false;

    public FaceSelectHoverTask(World world,
                               BlockPos startPos,
                               Direction face,
                               BlockState targetState,
                               BlockFilter blockFilter) {
        this(world, startPos, face, targetState, blockFilter, DEFAULT_MAX_BLOCKS, DEFAULT_STEP_SIZE);
    }

    public FaceSelectHoverTask(World world,
                               BlockPos startPos,
                               Direction face,
                               BlockState targetState,
                               BlockFilter blockFilter,
                               int maxBlocks,
                               int stepSize) {
        this.world = world;
        this.startPos = startPos != null ? startPos.toImmutable() : null;
        this.face = face;
        this.targetState = targetState;
        this.blockFilter = Objects.requireNonNull(blockFilter, "blockFilter");
        this.maxBlocks = Math.max(1, maxBlocks);
        this.stepSize = Math.max(32, stepSize);
    }

    public void start(Callback callback) {
        ForkJoinPool.commonPool().submit(() -> runFlood(callback));
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isFinished() {
        return finished;
    }

    private void runFlood(Callback callback) {
        long startNs = System.nanoTime();
        if (world == null || startPos == null || face == null || targetState == null) {
            finished = true;
            if (callback != null) {
                try {
                    callback.onCancelled();
                } catch (Exception ignored) {}
            }
            return;
        }

        if (!blockFilter.test(world, startPos, targetState)) {
            finished = true;
            Result empty = new Result(Collections.emptySet(), new HashMap<>(), startPos, face, false);
            if (callback != null) {
                try {
                    callback.onCompleted(empty);
                } catch (Exception ignored) {}
            }
            return;
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, Integer> depthMap = new HashMap<>();

        queue.add(startPos);
        visited.add(startPos);
        depthMap.put(startPos, 0);

        List<Direction> planeDirs = getFaceDirections(face);
        int processedInStep = 0;
        boolean reachedLimit = false;

        while (!queue.isEmpty() && !cancelled) {
            BlockPos current = queue.poll();
            int currentDepth = depthMap.getOrDefault(current, 0);

            for (Direction dir : planeDirs) {
                BlockPos neighbor = current.offset(dir);
                if (visited.contains(neighbor)) {
                    continue;
                }

                BlockState neighborState = world.getBlockState(neighbor);
                if (!isSameBlockType(targetState, neighborState)) {
                    continue;
                }
                if (!blockFilter.test(world, neighbor, neighborState)) {
                    continue;
                }

                visited.add(neighbor);
                depthMap.put(neighbor, currentDepth + 1);
                queue.add(neighbor);

                if (visited.size() >= maxBlocks) {
                    reachedLimit = true;
                    break;
                }
            }

            if (reachedLimit || cancelled) {
                break;
            }

            processedInStep++;
            if (processedInStep >= stepSize) {
                processedInStep = 0;
                if (callback != null) {
                    try {
                        callback.onUpdate(snapshotResult(visited, depthMap, false));
                    } catch (Exception e) {
                        LOGGER.warn("[FaceSelectHoverTask] onUpdate 回调失败", e);
                    }
                }
            }
        }

        finished = true;

        if (cancelled) {
            if (callback != null) {
                try {
                    callback.onCancelled();
                } catch (Exception e) {
                    LOGGER.warn("[FaceSelectHoverTask] onCancelled 回调失败", e);
                }
            }
            LOGGER.debug("[FaceSelectHoverTask] 任务被取消");
            return;
        }

        Result finalResult = snapshotResult(visited, depthMap, reachedLimit);

        if (callback != null) {
            try {
                callback.onUpdate(finalResult);
                callback.onCompleted(finalResult);
            } catch (Exception e) {
                LOGGER.warn("[FaceSelectHoverTask] 完成回调失败", e);
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        LOGGER.info("[FaceSelectHoverTask] Hover flood 完成: {} 个方块, limit={}, 耗时 {} ms",
            visited.size(), reachedLimit, durationMs);
    }

    private Result snapshotResult(Set<BlockPos> visited,
                                  Map<BlockPos, Integer> depthMap,
                                  boolean reachedLimit) {
        return new Result(
            new HashSet<>(visited),
            new HashMap<>(depthMap),
            startPos,
            face,
            reachedLimit
        );
    }

    private static List<Direction> getFaceDirections(Direction face) {
        return switch (face) {
            case NORTH, SOUTH -> Arrays.asList(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);
            case EAST, WEST -> Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN);
            case UP, DOWN -> Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        };
    }

    private static boolean isSameBlockType(BlockState a, BlockState b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getBlock().equals(b.getBlock());
    }
}

