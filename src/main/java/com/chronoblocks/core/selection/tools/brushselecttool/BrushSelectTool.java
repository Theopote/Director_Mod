package com.chronoblocks.core.selection.tools.brushselecttool;

import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionSet;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.block_filters.BlockFilterPipeline;
import com.chronoblocks.core.selection.block_filters.BlockFilters;
import com.chronoblocks.core.selection.tools.geometry.GeometryShape;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🖌️ 刷选工具 (Brush) v3.1
 * <p>
 * - 形状 = 纯空间范围（Set<BlockPos>），仅用于：
 *   1）预览：ToolPreviewManager 渲染体积轮廓 / 填充
 *   2）异步刷选：BrushSelectTask 计算真正选区结果
 * - 所有形状（球体、圆柱体、立方体、金字塔等）统一通过 BrushSelectTask.generateGeometryCandidates(...)
 * - 预览和实选使用同一套几何逻辑，保证"所见即所得"
 */
public class BrushSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrushSelectTool.class);

    private static final long TASK_COOLDOWN_MS = 80L;

    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 64.0;
    private static final double MIN_HEIGHT = 1.0;
    private static final double MAX_HEIGHT = 128.0;

    private double radius = 3.0;
    private double height = 8.0;
    private boolean hollow = false;
    private GeometryShape shape = GeometryShape.SPHERE;
    private SelectionOperation operation = SelectionOperation.NEW;
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
        return "几何形状笔刷选择工具，支持球体、圆柱体、金字塔、立方体等形状，支持加选/减选/交集操作。";
    }

    @Override
    public String getIcon() {
        return "\uE911"; // Icons.BRUSH
    }

    public void setRadius(double r) {
        double clamped = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
        if (this.radius == clamped) return;
        this.radius = clamped;
        LOGGER.debug("[BrushSelectTool] 设置半径: {}", radius);
        refreshPreview();
    }

    public double getRadius() {
        return radius;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double h) {
        double clamped = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
        if (this.height == clamped) return;
        this.height = clamped;
        LOGGER.debug("[BrushSelectTool] 设置高度: {}", height);
        refreshPreview();
    }

    public boolean isHollow() {
        return hollow;
    }

    public void setHollow(boolean h) {
        if (this.hollow != h) {
            this.hollow = h;
            LOGGER.debug("[BrushSelectTool] 设置空心: {}", hollow);
            refreshPreview();
        }
    }

    public GeometryShape getShape() {
        return shape;
    }

    public void setShape(GeometryShape s) {
        if (s == null || s == this.shape) {
            return;
        }
        this.shape = s;
        LOGGER.debug("[BrushSelectTool] 设置形状: {}", shape);
        refreshPreview();
    }

    /**
     * 几何参数发生变化时，尝试刷新当前预览。
     * - 如果正在拖拽，则用当前拖拽位置作为中心
     * - 否则交给 onHover 的下一次调用来刷新
     */
    private void refreshPreview() {
        SelectionManager manager = SelectionManager.get();
        if (!isPainting || manager == null) {
            return;
        }
        BlockPos current = ToolPreviewManager.get().getDragCurrentPos();
        if (current != null) {
            updateBrushPreview(current);
        }
    }

    /**
     * 根据中心方块生成形状体积，并推送给 ToolPreviewManager。
     * 这里的集合仅用于"可视化预览"，不直接修改选区。
     */
    private void updateBrushPreview(BlockPos center) {
        if (center == null) {
            ToolPreviewManager.get().clearBrushVolumePreview();
            return;
        }

        Set<BlockPos> previewBlocks = BrushSelectTask.generateGeometryCandidates(
            center, shape, radius, height, hollow
        );

        // 只作为"空间范围"的可视化，不影响 SelectionSet
        ToolPreviewManager.get().updateBrushVolumePreview(previewBlocks);
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

    /**
     * 只读：返回全局管道（供 ToolPropertiesPanel 等展示）。刷选逻辑统一经 SelectionManager.getEffectiveFilterPipeline()。
     */
    public BlockFilterPipeline getPipeline() {
        SelectionManager m = SelectionManager.get();
        return m != null ? m.getPipeline() : new BlockFilterPipeline();
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
        if (world == null || pos == null) return;

        cancelCurrentTask();

        BlockFilterPipeline effective = (SelectionManager.get() != null)
            ? SelectionManager.get().getEffectiveFilterPipeline()
            : new BlockFilterPipeline().add(BlockFilters.nonAir());

        BrushSelectTask task = BrushSelectTask.start(
            world, pos, shape, radius, height, hollow, effective,
            new BrushSelectTask.Callback() {
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

        LOGGER.debug("[BrushSelectTool] 刷选点击: {}，半径: {}, 高度: {}, 形状: {}",
            pos, radius, height, shape);

        final SelectionOperation effectiveOperation =
            (shift && this.operation == SelectionOperation.NEW)
                ? SelectionOperation.ADD
                : this.operation;

        // 点击时先更新预览形状，让玩家看到最终体积
        updateBrushPreview(pos);
        // 再启动异步刷选任务，真正计算并应用选区
        startBrushTask(world, pos, effectiveOperation);
    }

    private void applySelectionResult(Set<BlockPos> blocks, SelectionOperation op) {
        if (blocks == null || blocks.isEmpty()) {
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
            updateBrushPreview(start);
        }
        lastTaskStartedAt = 0L;
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (current == null) {
            return;
        }

        // 拖拽过程中一直更新空间范围预览
        updateBrushPreview(current);

        if (isPainting && canStartNewTask()) {
            startBrushTask(world, current, this.operation);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        isPainting = false;
        LOGGER.debug("[BrushSelectTool] 刷选拖拽结束");
        if (end != null) {
            updateBrushPreview(end);
        }
        // 不立即清除预览，让玩家还能看见最后一次刷选范围
    }

    @Override
    public void onHover(World world, BlockPos pos) {
        if (world == null || pos == null) {
            ToolPreviewManager.get().clearBrushVolumePreview();
            return;
        }

        // 普通 Hover 也使用同一套空间范围预览
        updateBrushPreview(pos);
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
        if (manager != null) {
            manager.clearSelection();
        }
        ToolPreviewManager.get().clearBrushVolumePreview();
        LOGGER.debug("[BrushSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = SelectionManager.get();
        return (manager != null && !manager.getSelectionSet().isEmpty()) || isPainting;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具激活，形状: {}，半径: {}，高度: {}，操作模式: {}",
            shape, radius, height, operation.getDisplayName());
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具停用");
        isPainting = false;
        cancelCurrentTask();
        ToolPreviewManager.get().clearBrushVolumePreview();
        lastTaskStartedAt = 0L;
    }
}

