package com.chronoblocks.core.selection.tools.brushselecttool;

import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionSet;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.block_filters.BlockFilterPipeline;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🖌️ 刷选工具 (Brush) v2.0
 *
 * - 支持实时 hover 预览（ToolPreviewManager）
 * - 支持异步分帧刷选任务（BrushSelectTask）
 */
public class BrushSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrushSelectTool.class);

    private static final long TASK_COOLDOWN_MS = 80L;

    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 20.0;

    private double radius = 3.0;
    private SelectionOperation operation = SelectionOperation.NEW;
    private final BlockFilterPipeline pipeline = new BlockFilterPipeline();
    private boolean isPainting = false;

    private volatile BrushSelectTask currentTask;
    private final Object taskLock = new Object();
    private long lastTaskStartedAt = 0L;

    @Override
    public String getId() {
        return "brush";
    }

    @Override
    public String getDisplayName() {
        return "刷选工具";
    }

    @Override
    public String getDescription() {
        return "圆形区域笔刷选择工具，支持加选/减选/交集操作。";
    }

    @Override
    public String getIcon() {
        return "\uE911"; // Icons.BRUSH
    }

    public void setRadius(double r) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
        LOGGER.debug("[BrushSelectTool] 设置半径: {}", radius);
        ToolPreviewManager.get().setBrushPreviewRadius((float) radius);
        if (isPainting && SelectionManager.get() != null) {
            // 拖拽中半径变化时刷新预览
            BlockPos current = ToolPreviewManager.get().getDragCurrentPos();
            if (current != null) {
                ToolPreviewManager.get().updateBrushPreview(current, radius);
            }
        }
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public void setOperation(SelectionOperation op) {
        if (op != null) {
            this.operation = op;
            LOGGER.debug("[BrushSelectTool] 设置操作模式: {}", op.getDisplayName());
        }
    }

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    public BlockFilterPipeline getPipeline() {
        return pipeline;
    }

    private void cancelCurrentTask() {
        synchronized (taskLock) {
            if (currentTask != null) {
                currentTask.cancel();
                currentTask = null;
                LOGGER.debug("[BrushSelectTool] 已取消当前刷选任务");
            }
        }
    }

    private void setCurrentTask(BrushSelectTask task) {
        synchronized (taskLock) {
            currentTask = task;
        }
    }

    private boolean clearCurrentTask(BrushSelectTask task) {
        synchronized (taskLock) {
            if (currentTask == task) {
                currentTask = null;
                return true;
            }
        }
        return false;
    }

    private boolean canStartNewTask() {
        long now = System.currentTimeMillis();
        return now - lastTaskStartedAt >= TASK_COOLDOWN_MS;
    }

    private void startBrushTask(World world, BlockPos pos, SelectionOperation effectiveOperation) {
        cancelCurrentTask();

        BrushSelectTask task = BrushSelectTask.start(world, pos, radius, pipeline, new BrushSelectTask.Callback() {
            @Override
            public void onCompleted(BrushSelectTask taskRef, Set<BlockPos> result) {
                handleTaskCompleted(taskRef, effectiveOperation, result);
            }

            @Override
            public void onCancelled(BrushSelectTask taskRef) {
                handleTaskCancelled(taskRef);
            }

            @Override
            public void onError(BrushSelectTask taskRef, Throwable error) {
                handleTaskError(taskRef, error);
            }
        });

        if (task != null) {
            setCurrentTask(task);
            lastTaskStartedAt = System.currentTimeMillis();
        }
    }

    private void handleTaskCompleted(BrushSelectTask task, SelectionOperation op, Set<BlockPos> blocks) {
        if (!clearCurrentTask(task)) {
            LOGGER.trace("[BrushSelectTool] 忽略过期刷选任务结果");
            return;
        }
        applySelectionResult(blocks, op);
    }

    private void handleTaskCancelled(BrushSelectTask task) {
        if (clearCurrentTask(task)) {
            LOGGER.trace("[BrushSelectTool] 异步刷选任务被取消");
        }
    }

    private void handleTaskError(BrushSelectTask task, Throwable error) {
        if (clearCurrentTask(task)) {
            LOGGER.error("[BrushSelectTool] 异步刷选任务出错", error);
        } else {
            LOGGER.debug("[BrushSelectTool] 忽略过期刷选任务错误: {}", error.getMessage());
        }
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean shift) {
        if (world == null || pos == null) {
            return;
        }

        LOGGER.debug("[BrushSelectTool] 刷选点击: {}，半径: {}", pos, radius);

        final SelectionOperation effectiveOperation =
            (shift && this.operation == SelectionOperation.NEW)
                ? SelectionOperation.ADD
                : this.operation;

        ToolPreviewManager.get().updateBrushPreview(pos, radius);
        startBrushTask(world, pos, effectiveOperation);
    }

    private void applySelectionResult(Set<BlockPos> blocks, SelectionOperation op) {
        if (blocks == null) {
            LOGGER.warn("[BrushSelectTool] 刷选结果为空");
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager == null) {
            LOGGER.warn("[BrushSelectTool] SelectionManager 不存在，无法应用刷选结果");
            return;
        }

        SelectionSet set = manager.getSelectionSet();
        int beforeSize = set.size();

        switch (op) {
            case NEW -> {
                set.clear();
                set.addAll(blocks);
                LOGGER.info("[BrushSelectTool] 新建选区操作，添加 {} 个方块", blocks.size());
            }
            case ADD -> {
                set.addAll(blocks);
                LOGGER.info("[BrushSelectTool] 加选操作，添加 {} 个方块", set.size() - beforeSize);
            }
            case SUBTRACT -> {
                int removed = 0;
                for (BlockPos blockPos : blocks) {
                    if (set.remove(blockPos)) {
                        removed++;
                    }
                }
                LOGGER.info("[BrushSelectTool] 减选操作，移除 {} 个方块", removed);
            }
            case INTERSECT -> {
                var current = set.asUnmodifiableSet();
                set.clear();
                for (BlockPos blockPos : blocks) {
                    if (current.contains(blockPos)) {
                        set.add(blockPos);
                    }
                }
                LOGGER.info("[BrushSelectTool] 交集操作，保留 {} 个方块", set.size());
            }
        }
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        isPainting = true;
        LOGGER.debug("[BrushSelectTool] 开始刷选拖拽");
        if (start != null) {
            ToolPreviewManager.get().updateBrushPreview(start, radius);
        }
        lastTaskStartedAt = 0L;
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (current == null) {
            return;
        }

        ToolPreviewManager.get().updateBrushPreview(current, radius);

        if (isPainting && canStartNewTask()) {
            startBrushTask(world, current, this.operation);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        isPainting = false;
        LOGGER.debug("[BrushSelectTool] 刷选拖拽结束");
        if (end != null) {
            ToolPreviewManager.get().updateBrushPreview(end, radius);
        }
    }

    @Override
    public void onHover(World world, BlockPos pos) {
        if (world == null || pos == null) {
            ToolPreviewManager.get().clearBrushPreview();
            return;
        }

        ToolPreviewManager.get().updateBrushPreview(pos, radius);
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
    }

    @Override
    public void clearSelection() {
        cancelCurrentTask();
        SelectionManager manager = SelectionManager.get();
        manager.clearSelection();
        ToolPreviewManager.get().clearBrushPreview();
        LOGGER.debug("[BrushSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !SelectionManager.get().getSelectionSet().isEmpty() || isPainting;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具激活，半径: {}，操作模式: {}", radius, operation.getDisplayName());
        ToolPreviewManager.get().setBrushPreviewRadius((float) radius);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具停用");
        isPainting = false;
        cancelCurrentTask();
        ToolPreviewManager.get().clearBrushPreview();
        lastTaskStartedAt = 0L;
    }
}

