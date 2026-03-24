package com.director.core.selection.tools.lassoselecttool;

import com.director.client.renderer.ToolPreviewManager;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
import com.director.core.selection.block_filters.BlockFilter;
import com.director.core.selection.block_filters.BlockFilters;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 🖊️ 世界空间套索工具
 * <p>
 * 拖拽时直接采样 BlockPos 顶点，形成 XZ 平面上的多边形，
 * 交由 {@link LassoSelectTask} 异步计算选区。
 */
public final class LassoSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LassoSelectTool.class);

    private static final int MAX_PATH_POINTS = 8_192;
    private static final double MIN_SAMPLE_DISTANCE_SQ = 0.25; // 0.5 格，用于 currentPath 采样
    /** 笔迹稀疏阈值（0.1 格 squared），仅当与上一点距离超过此值才加点，减轻 z-fighting 与开销 */
    private static final double MIN_STROKE_DISTANCE_SQ = 0.01;

    private final List<BlockPos> currentPath = new ArrayList<>();
    /** 套索笔迹：贴块表面的世界坐标，仅供 ToolPreviewManager 绘制，与 currentPath 分离 */
    private final List<Vec3d> strokePoints = new ArrayList<>();
    private final ToolPreviewManager previewManager = ToolPreviewManager.getInstance();

    private SelectionOperation operation = SelectionOperation.NEW;
    private boolean drawing = false;

    private LassoSelectTask activeTask;

    @Override
    public String getId() {
        return "lasso";
    }

    @Override
    public String getDisplayName() {
        return "套索选择工具";
    }

    @Override
    public String getDescription() {
        return "自由绘制闭合路径，选中路径内部的方块区域。";
    }

    @Override
    public String getIcon() {
        return Icons.LASSO;
    }

    // ========= 配置 =========

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    @Override
    public void setOperation(SelectionOperation operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public boolean isDrawing() {
        return drawing;
    }

    public int getPathSampleCount() {
        return currentPath.size();
    }

    // ========= 交互 =========

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager == null) {
            LOGGER.warn("[LassoSelectTool] SelectionManager 不可用，忽略点击");
            return;
        }

        SelectionOperation op = isShiftDown ? SelectionOperation.ADD : operation;
        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.getSelectionSet().add(pos);
            }
            case ADD -> manager.addToSelection(pos);
            case SUBTRACT -> manager.removeFromSelection(pos);
            case INTERSECT -> {
                if (manager.getSelectionSet().contains(pos)) {
                    manager.clearSelection();
                    manager.getSelectionSet().add(pos);
                } else {
                    manager.clearSelection();
                }
            }
        }

        LOGGER.info("[LassoSelectTool] 单点套索，操作={}，方块={}", op, pos);
    }

    @Override
    public void onDragStart(World world, BlockPos start, BlockHitResult hitOrNull) {
        if (world == null || start == null) {
            return;
        }

        cancelActiveTask();
        drawing = true;
        currentPath.clear();
        currentPath.add(start.toImmutable());
        strokePoints.clear();

        previewManager.updateLassoPath(currentPath, false);
        previewManager.updateLassoFill(Collections.emptyList());

        if (hitOrNull != null) {
            Vec3d n = new Vec3d(
                hitOrNull.getSide().getOffsetX(),
                hitOrNull.getSide().getOffsetY(),
                hitOrNull.getSide().getOffsetZ()
            );
            Vec3d p = hitOrNull.getPos().add(n.multiply(0.01));
            strokePoints.add(p);
            previewManager.updateLassoStroke(strokePoints);
        }

        LOGGER.debug("[LassoSelectTool] 拖拽开始，起点={}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current, BlockHitResult hitOrNull) {
        if (!drawing || world == null || current == null) {
            return;
        }
        if (currentPath.size() >= MAX_PATH_POINTS) {
            return;
        }

        BlockPos last = currentPath.getLast();
        if (last.getSquaredDistance(current) < MIN_SAMPLE_DISTANCE_SQ) {
            return;
        }

        currentPath.add(current.toImmutable());
        previewManager.updateLassoPath(currentPath, false);

        if (hitOrNull != null) {
            Vec3d n = new Vec3d(
                hitOrNull.getSide().getOffsetX(),
                hitOrNull.getSide().getOffsetY(),
                hitOrNull.getSide().getOffsetZ()
            );
            Vec3d p = hitOrNull.getPos().add(n.multiply(0.01));
            if (strokePoints.isEmpty() || strokePoints.getLast().squaredDistanceTo(p) > MIN_STROKE_DISTANCE_SQ) {
                strokePoints.add(p);
                previewManager.updateLassoStroke(strokePoints);
            }
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end, BlockHitResult hitOrNull) {
        if (!drawing) {
            return;
        }
        drawing = false;

        previewManager.updateLassoStroke(Collections.emptyList());

        if (world == null || end == null) {
            clearPreview();
            return;
        }

        if (currentPath.isEmpty() ||
            currentPath.getLast().getSquaredDistance(end) >= MIN_SAMPLE_DISTANCE_SQ) {
            if (currentPath.size() < MAX_PATH_POINTS) {
                currentPath.add(end.toImmutable());
            }
        }

        if (currentPath.size() < 3) {
            LOGGER.debug("[LassoSelectTool] 顶点不足，忽略此次套索");
            clearPreview();
            return;
        }

        previewManager.updateLassoPath(currentPath, true);
        startComputation(world);
    }

    @Override
    public void onDeactivate(World world) {
        cancelActiveTask();
        LassoSelectTask.cancelAll();
        clearPreview();
        LOGGER.info("[LassoSelectTool] 工具停用，清理路径与任务");
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return manager != null ? manager.getCurrentSelection() : SelectionState.empty();
    }

    @Override
    public void clearSelection() {
        cancelActiveTask();
        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.clearSelection();
        }
        clearPreview();
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = SelectionManager.get();
        return drawing || (manager != null && manager.getSelectionSet() != null
            && !manager.getSelectionSet().isEmpty());
    }

    // ========= 私有工具方法 =========

    private void startComputation(World world) {
        cancelActiveTask();

        List<BlockPos> pathSnapshot = new ArrayList<>(currentPath);
        BlockFilter filter = getEffectiveBlockFilter();
        LassoSelectTask task = new LassoSelectTask(world, pathSnapshot, operation, filter);
        activeTask = task;
        task.start();

        LOGGER.info("[LassoSelectTool] 已提交套索任务，顶点数={}，操作={}", pathSnapshot.size(), operation);

        currentPath.clear();
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
    }

    /** 与框选、魔棒、刷选、面选一致：使用 SelectionManager.getEffectiveBlockFilter()，空管道时 nonAir。 */
    private BlockFilter getEffectiveBlockFilter() {
        SelectionManager m = SelectionManager.get();
        return m != null ? m.getEffectiveBlockFilter() : BlockFilters.nonAir();
    }

    private void clearPreview() {
        currentPath.clear();
        strokePoints.clear();
        previewManager.clearLassoPreview();
        previewManager.updateLassoFill(Collections.emptyList());
    }
}

