package com.chronoblocks.core.selection.tools.faceselecttool;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 🌀 FaceSelectTask v2.0
 *
 * 支持异步、分帧执行的平面 Flood Fill 任务。
 * - 固定在命中面的平面上扩散
 * - 每帧控制步数与时间预算，防止掉帧
 * - 支持取消、进度回调与结果回调
 */
public class FaceSelectTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceSelectTask.class);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FaceSelectTask");
        t.setDaemon(true);
        return t;
    });

    /** 任务输入 */
    private final World world;
    private final BlockPos startPos;
    private final Direction face;
    private final BlockState targetState;
    private final FaceSelectConfig config;

    /** 回调 */
    private final Consumer<TaskProgress> onProgress;
    private final Consumer<TaskResult> onComplete;

    /** BFS 状态 */
    private final Queue<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private final Set<BlockPos> accepted = new HashSet<>();

    /** 目标状态缓存 */
    private final BlockState normalizedTargetState;
    private final MapColor targetMapColor;

    /** 控制状态 */
    private volatile boolean cancelled = false;
    private volatile Future<?> future;

    public FaceSelectTask(
        World world,
        BlockPos startPos,
        Direction face,
        BlockState targetState,
        FaceSelectConfig config,
        Consumer<TaskProgress> onProgress,
        Consumer<TaskResult> onComplete
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.startPos = Objects.requireNonNull(startPos, "startPos");
        this.face = Objects.requireNonNull(face, "face");
        this.targetState = Objects.requireNonNull(targetState, "targetState");
        this.config = Objects.requireNonNull(config, "config");
        this.onProgress = onProgress;
        this.onComplete = onComplete;

        this.queue.offer(startPos);
        this.visited.add(startPos);

        this.normalizedTargetState = targetState;
        this.targetMapColor = targetState.getMapColor(world, startPos);
    }

    /** 启动任务 */
    public synchronized void start() {
        if (future != null && !future.isDone()) {
            throw new IllegalStateException("FaceSelectTask 已在运行");
        }
        future = EXECUTOR.submit(this);
    }

    /** 取消任务 */
    public void cancel() {
        this.cancelled = true;
        Future<?> snapshot = this.future;
        if (snapshot != null) {
            snapshot.cancel(true);
        }
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        long frameStart = System.nanoTime();
        int processed = 0;
        int maxSize = config.getMaxSelectionSize();
        int stepBudget = config.getStepSize();
        long timeBudget = config.getFrameBudgetNanos();
        Set<BlockPos> frameDelta = new HashSet<>();

        while (!queue.isEmpty() && accepted.size() < maxSize && !cancelled) {
            BlockPos current = queue.poll();
            if (current == null) {
                break;
            }

            accepted.add(current);
            frameDelta.add(current);

            // 遍历同一平面的邻居
            for (Direction dir : getFaceDirections(face)) {
                BlockPos neighbor = current.offset(dir);
                if (!visited.add(neighbor)) {
                    continue;
                }

                if (!isOnSamePlane(startPos, neighbor, face)) {
                    continue;
                }

                BlockState neighborState = world.getBlockState(neighbor);
                if (!matches(neighbor, neighborState)) {
                    continue;
                }

                queue.offer(neighbor);
            }

            processed++;

            if (processed >= stepBudget) {
                break;
            }

            if (System.nanoTime() - frameStart >= timeBudget) {
                break;
            }
        }

        boolean reachedLimit = accepted.size() >= maxSize;

        if (!frameDelta.isEmpty() && onProgress != null) {
            onProgress.accept(new TaskProgress(snapshot(), frameDelta.size(), reachedLimit));
        }

        if (queue.isEmpty() || reachedLimit || cancelled) {
            Set<BlockPos> snapshot = snapshot();
            if (onComplete != null && !cancelled) {
                LOGGER.debug("[FaceSelectTask] 完成：blocks={}, reachedLimit={}", snapshot.size(), reachedLimit);
                onComplete.accept(new TaskResult(snapshot, reachedLimit, false));
            } else if (onComplete != null && cancelled) {
                LOGGER.debug("[FaceSelectTask] 取消：blocks={}, reachedLimit={}", snapshot.size(), reachedLimit);
                onComplete.accept(new TaskResult(snapshot, reachedLimit, true));
            }
        } else {
            scheduleNextFrame();
        }
    }

    private void scheduleNextFrame() {
        if (cancelled) {
            return;
        }
        future = EXECUTOR.submit(this);
    }

    private boolean matches(BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }

        if (config.isIgnoreTransparent() && state.isAir()) {
            return false;
        }

        if (config.isIgnoreTransparent() && !state.isOpaque()) {
            return false;
        }

        if (config.isStrictBlockState()) {
            return state.equals(normalizedTargetState);
        }

        if (config.isPropagateByMaterial()) {
            return state.getMapColor(world, pos) == targetMapColor;
        }

        return state.getBlock().equals(targetState.getBlock());
    }

    private static boolean isOnSamePlane(BlockPos origin, BlockPos pos, Direction face) {
        return switch (face.getAxis()) {
            case X -> pos.getX() == origin.getX();
            case Y -> pos.getY() == origin.getY();
            case Z -> pos.getZ() == origin.getZ();
        };
    }

    private static List<Direction> getFaceDirections(Direction face) {
        return switch (face) {
            case NORTH, SOUTH -> FACE_NS;
            case EAST, WEST -> FACE_EW;
            case UP, DOWN -> FACE_UD;
        };
    }

    private Set<BlockPos> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(accepted));
    }

    /** 任务进度 */
    public record TaskProgress(Set<BlockPos> blocks, int deltaCount, boolean reachedLimit) {}

    /** 任务结果 */
    public record TaskResult(Set<BlockPos> blocks, boolean reachedLimit, boolean cancelled) {}

    private static final List<Direction> FACE_NS = List.of(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);
    private static final List<Direction> FACE_EW = List.of(Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN);
    private static final List<Direction> FACE_UD = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
}

