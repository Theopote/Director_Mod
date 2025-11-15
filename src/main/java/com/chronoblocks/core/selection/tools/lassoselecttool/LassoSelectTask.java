package com.chronoblocks.core.selection.tools.lassoselecttool;

import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🌀 世界空间版套索任务
 * <p>
 * 将拖拽路径（BlockPos 顶点）视为 XZ 平面上的多边形，
 * 对多边形进行扫描线填充，获得内部的方块集合，并应用指定的 SelectionOperation。
 * <p>
 * 不再触碰 RenderSystem / 投影矩阵，完全在世界坐标系运算。
 */
public final class LassoSelectTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LassoSelectTask.class);

    /** 异步线程池（供多个套索任务复用） */
    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        runnable -> {
            Thread thread = new Thread(runnable, "LassoSelectTask-Worker");
            thread.setDaemon(true);
            return thread;
        }
    );

    /** 当前正在执行的任务集合，用于 cancelAll() */
    private static final Set<LassoSelectTask> RUNNING_TASKS = ConcurrentHashMap.newKeySet();

    /** 防御性限制，避免一次性选区过大耗尽内存 */
    private static final int MAX_BLOCKS = 500_000;

    /** 如果路径不闭合时自动闭合，允许的起终点误差 */
    private static final double CLOSE_EPSILON = 1.5;

    private final World world;
    private final List<BlockPos> path;
    private final SelectionOperation operation;
    private final boolean includeNonSolid;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private int minY;
    private int maxY;

    public LassoSelectTask(World world,
                           List<BlockPos> path,
                           SelectionOperation operation,
                           boolean includeNonSolid) {
        this.world = Objects.requireNonNull(world, "world");
        if (path == null || path.size() < 3) {
            throw new IllegalArgumentException("Lasso path requires at least 3 points");
        }
        this.path = new ArrayList<>(path.size());
        for (BlockPos pos : path) {
            this.path.add(pos.toImmutable());
        }
        closePathIfNeeded(this.path);

        this.operation = operation != null ? operation : SelectionOperation.NEW;
        this.includeNonSolid = includeNonSolid;

        computeVerticalBounds();
    }

    /** 启动任务 */
    public void start() {
        RUNNING_TASKS.add(this);
        EXECUTOR.submit(this);
    }

    /** 请求取消 */
    public void cancel() {
        cancelled.set(true);
    }

    /** 取消所有正在运行的任务 */
    public static void cancelAll() {
        for (LassoSelectTask task : RUNNING_TASKS) {
            task.cancel();
        }
        RUNNING_TASKS.clear();
        LOGGER.warn("[LassoSelectTask] 已请求取消所有套索任务");
    }

    @Override
    public void run() {
        try {
            if (cancelled.get()) {
                return;
            }

            List<Vector2f> polygon = buildPolygon2D(path);
            if (polygon.size() < 3) {
                LOGGER.warn("[LassoSelectTask] 2D 多边形顶点不足，放弃");
                return;
            }

            BoundsXZ bounds = computeBounds(polygon);
            if (bounds.isEmpty()) {
                LOGGER.warn("[LassoSelectTask] 计算得到的包围盒为空，放弃");
                return;
            }

            Set<BlockPos> result = fillPolygon(bounds, polygon);
            if (result.isEmpty()) {
                LOGGER.info("[LassoSelectTask] 填充结果为空");
                return;
            }

            applySelectionOnClient(result);
        } catch (Exception e) {
            LOGGER.error("[LassoSelectTask] 执行异常", e);
        } finally {
            RUNNING_TASKS.remove(this);
        }
    }

    private void applySelectionOnClient(Set<BlockPos> blocks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.warn("[LassoSelectTask] MinecraftClient 不可用，无法应用选区");
            return;
        }

        client.execute(() -> {
            if (cancelled.get()) {
                return;
            }

            SelectionManager manager = SelectionManager.get();
            if (manager == null) {
                LOGGER.warn("[LassoSelectTask] SelectionManager 为空，跳过应用结果");
                return;
            }

            var selectionSet = manager.getSelectionSet();

            switch (operation) {
                case NEW -> {
                    manager.clearSelection();
                    selectionSet.addAll(blocks);
                }
                case ADD -> selectionSet.addAll(blocks);
                case SUBTRACT -> selectionSet.removeIf(blocks::contains);
                case INTERSECT -> {
                    Set<BlockPos> intersection = new HashSet<>();
                    for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
                        if (blocks.contains(pos)) {
                            intersection.add(pos);
                        }
                    }
                    selectionSet.clear();
                    selectionSet.addAll(intersection);
                }
            }

            ToolPreviewManager.getInstance().updateLassoFill(blocks);
            LOGGER.info("[LassoSelectTask] ✅ 应用 {} 操作，当前选区方块数={}",
                operation, manager.getSelectionSet().size());
        });
    }

    private Set<BlockPos> fillPolygon(BoundsXZ bounds, List<Vector2f> polygon) {
        Set<BlockPos> result = new HashSet<>();

        for (int x = bounds.minX; x <= bounds.maxX && !cancelled.get(); x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ && !cancelled.get(); z++) {
                float cx = x + 0.5f;
                float cz = z + 0.5f;
                if (!pointInPolygon(polygon, cx, cz)) {
                    continue;
                }

                for (int y = minY; y <= maxY && !cancelled.get(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!includeNonSolid && world.isAir(pos)) {
                        continue;
                    }
                    if (!includeNonSolid && world.getBlockState(pos).isAir()) {
                        continue;
                    }

                    result.add(pos);
                    if (result.size() >= MAX_BLOCKS) {
                        LOGGER.warn("[LassoSelectTask] 选区达到上限 {}，提前停止", MAX_BLOCKS);
                        cancelled.set(true);
                        break;
                    }
                }
            }
        }

        LOGGER.info("[LassoSelectTask] 填充完成，共选中 {} 个方块", result.size());
        return result;
    }

    private void computeVerticalBounds() {
        var dimension = world.getDimension();
        int worldBottom = dimension.minY();
        int worldTop = dimension.minY() + dimension.height() - 1;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : path) {
            min = Math.min(min, pos.getY());
            max = Math.max(max, pos.getY());
        }

        minY = Math.max(worldBottom, min - 2);
        maxY = Math.min(worldTop, max + 2);
    }

    private static void closePathIfNeeded(List<BlockPos> path) {
        if (path.isEmpty()) {
            return;
        }
        BlockPos first = path.getFirst();
        BlockPos last = path.getLast();
        if (first.getSquaredDistance(last) > CLOSE_EPSILON * CLOSE_EPSILON) {
            path.add(first.toImmutable());
        }
    }

    private static List<Vector2f> buildPolygon2D(List<BlockPos> path) {
        List<Vector2f> polygon = new ArrayList<>(path.size());
        BlockPos prev = null;
        for (BlockPos pos : path) {
            if (prev != null && prev.getX() == pos.getX() && prev.getZ() == pos.getZ()) {
                continue;
            }
            polygon.add(new Vector2f(pos.getX() + 0.5f, pos.getZ() + 0.5f));
            prev = pos;
        }
        return polygon;
    }

    private static BoundsXZ computeBounds(List<Vector2f> polygon) {
        if (polygon.isEmpty()) {
            return BoundsXZ.empty();
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Vector2f point : polygon) {
            minX = Math.min(minX, (int) Math.floor(point.x));
            maxX = Math.max(maxX, (int) Math.floor(point.x));
            minZ = Math.min(minZ, (int) Math.floor(point.y));
            maxZ = Math.max(maxZ, (int) Math.floor(point.y));
        }

        return new BoundsXZ(minX, maxX, minZ, maxZ);
    }

    private static boolean pointInPolygon(List<Vector2f> polygon, float x, float z) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vector2f pi = polygon.get(i);
            Vector2f pj = polygon.get(j);

            boolean intersect = ((pi.y > z) != (pj.y > z)) &&
                (x < (pj.x - pi.x) * (z - pi.y) / (pj.y - pi.y + 1.0e-6f) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static final class BoundsXZ {
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        BoundsXZ(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        boolean isEmpty() {
            return minX > maxX || minZ > maxZ;
        }

        static BoundsXZ empty() {
            return new BoundsXZ(0, -1, 0, -1);
        }
    }
}

