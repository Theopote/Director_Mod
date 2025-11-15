package com.chronoblocks.core.selection.tools.lassoselecttool;

import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 🖊️ 世界空间套索工具
 *
 * 拖拽时直接采样 BlockPos 顶点，形成 XZ 平面上的多边形，
 * 交由 {@link LassoSelectTask} 异步计算选区。
 */
public final class LassoSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LassoSelectTool.class);

    private static final int MAX_PATH_POINTS = 8_192;
    private static final double MIN_SAMPLE_DISTANCE_SQ = 0.25; // 0.5 格

    private final List<BlockPos> currentPath = new ArrayList<>();
    private final ToolPreviewManager previewManager = ToolPreviewManager.getInstance();

    private SelectionOperation operation = SelectionOperation.NEW;
    private boolean includeNonSolid = false;
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
        return "\uE91D";
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

    public boolean isIncludeNonSolid() {
        return includeNonSolid;
    }

    public void setIncludeNonSolid(boolean includeNonSolid) {
        this.includeNonSolid = includeNonSolid;
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
    public void onDragStart(World world, BlockPos start) {
        if (world == null || start == null) {
            return;
        }

        cancelActiveTask();
        drawing = true;
        currentPath.clear();
        currentPath.add(start.toImmutable());

        previewManager.updateLassoPath(currentPath, false);
        previewManager.updateLassoFill(Collections.emptyList());

        LOGGER.debug("[LassoSelectTool] 拖拽开始，起点={}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (!drawing || world == null || current == null) {
            return;
        }
        if (currentPath.size() >= MAX_PATH_POINTS) {
            return;
        }

        BlockPos last = currentPath.get(currentPath.size() - 1);
        if (last.getSquaredDistance(current) < MIN_SAMPLE_DISTANCE_SQ) {
            return;
        }

        currentPath.add(current.toImmutable());
        previewManager.updateLassoPath(currentPath, false);
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (!drawing) {
            return;
        }
        drawing = false;

        if (world == null || end == null) {
            clearPreview();
            return;
        }

        if (currentPath.isEmpty() ||
            currentPath.get(currentPath.size() - 1).getSquaredDistance(end) >= MIN_SAMPLE_DISTANCE_SQ) {
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
        LassoSelectTask task = new LassoSelectTask(
            world,
            pathSnapshot,
            operation,
            includeNonSolid
        );
        activeTask = task;
        task.start();

        LOGGER.info("[LassoSelectTool] 已提交套索任务，顶点数={}，操作={}，includeNonSolid={}",
            pathSnapshot.size(), operation, includeNonSolid);

        currentPath.clear();
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
    }

    private void clearPreview() {
        currentPath.clear();
        previewManager.clearLassoPreview();
        previewManager.updateLassoFill(Collections.emptyList());
    }
}

