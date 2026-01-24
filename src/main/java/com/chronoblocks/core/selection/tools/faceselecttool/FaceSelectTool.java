package com.chronoblocks.core.selection.tools.faceselecttool;

import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.block_filters.BlockFilter;
import com.chronoblocks.core.selection.block_filters.BlockFilters;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🔲 FaceSelectTool v2.0
 *
 * - 基于命中面的二维 Flood Fill
 * - 异步执行 + 分帧更新，避免卡顿
 * - 实时预览同步 ToolPreviewManager
 * - 支持 Shift 加选
 */
public class FaceSelectTool implements SelectionTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceSelectTool.class);

    private final SelectionState currentSelection = new SelectionState("face");
    private final FaceSelectConfig config = new FaceSelectConfig();

    private FaceSelectTask runningTask;

    /** Hover 预览任务（鼠标悬停） */
    private FaceSelectHoverTask currentHoverTask = null;
    private final Object hoverLock = new Object();

    @Override
    public String getId() {
        return "face";
    }

    @Override
    public String getDisplayName() {
        return "面选择工具";
    }

    @Override
    public String getDescription() {
        return "选择方块的特定面，并延伸至相连的同类方块。支持异步处理与实时预览。";
    }

    @Override
    public String getIcon() {
        return "\uE91F";
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            clearHoverPreview();
            LOGGER.debug("[FaceSelectTool] 触发点击时 world/pos 为空");
            return;
        }

        BlockState targetState = world.getBlockState(pos);
        if (targetState == null || targetState.isAir()) {
            clearHoverPreview();
            LOGGER.debug("[FaceSelectTool] 点击位置无有效方块");
            return;
        }

        Direction face = resolvePlaneFace(getClickedFace());
        if (face == null) {
            clearHoverPreview();
            LOGGER.warn("[FaceSelectTool] 未能确定点击面");
            ToolPreviewManager.getInstance().clearFacePreview();
            return;
        }

        clearHoverPreview();

        cancelRunningTask();
        MinecraftClient client = MinecraftClient.getInstance();
        ToolPreviewManager previewManager = ToolPreviewManager.getInstance();

        LOGGER.info("[FaceSelectTool] 开始面选择: pos={}, face={}, block={}", pos, face, targetState.getBlock());

        BlockFilter filter = getEffectiveBlockFilter();
        runningTask = new FaceSelectTask(
            world,
            pos,
            face,
            targetState,
            config,
            filter,
            progress -> {
                if (client != null) {
                    client.execute(() -> handleProgress(progress, isShiftDown));
                } else {
                    handleProgress(progress, isShiftDown);
                }
            },
            result -> {
                if (client != null) {
                    client.execute(() -> handleCompletion(result, isShiftDown));
                } else {
                    handleCompletion(result, isShiftDown);
                }
            }
        );

        previewManager.updateFacePreview(Set.of(pos), tintFor(isShiftDown, false));
        runningTask.start();
    }

    @Override
    public void onHover(World world, BlockPos pos) {
        if (world == null || pos == null) {
            clearHoverPreview();
            return;
        }

        Direction face = resolvePlaneFace(getClickedFace());
        if (face == null) {
            clearHoverPreview();
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            clearHoverPreview();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ToolPreviewManager preview = ToolPreviewManager.get();

        synchronized (hoverLock) {
            if (currentHoverTask != null) {
                currentHoverTask.cancel();
                currentHoverTask = null;
            }

            FaceSelectHoverTask task = new FaceSelectHoverTask(world, pos, face, state, getEffectiveBlockFilter());
            currentHoverTask = task;

            task.start(new FaceSelectHoverTask.Callback() {
                private void runOnClient(Runnable runnable) {
                    if (client != null) {
                        client.execute(runnable);
                    } else {
                        runnable.run();
                    }
                }

                @Override
                public void onUpdate(FaceSelectHoverTask.Result result) {
                    runOnClient(() -> preview.updateFaceHoverPreview(
                        result.blocks(),
                        result.depthMap(),
                        result.origin(),
                        result.face()
                    ));
                }

                @Override
                public void onCompleted(FaceSelectHoverTask.Result result) {
                    runOnClient(() -> preview.updateFaceHoverPreview(
                        result.blocks(),
                        result.depthMap(),
                        result.origin(),
                        result.face()
                    ));
                }

                @Override
                public void onCancelled() {
                    runOnClient(() -> {
                        synchronized (hoverLock) {
                            if (currentHoverTask == task) {
                                preview.clearFaceHoverPreview();
                                currentHoverTask = null;
                            }
                        }
                    });
                }
            });
        }
    }

    private void handleProgress(FaceSelectTask.TaskProgress progress, boolean appendMode) {
        if (progress == null || progress.blocks() == null) {
            return;
        }
        ToolPreviewManager.getInstance().updateFacePreview(progress.blocks(), tintFor(appendMode, progress.reachedLimit()));
    }

    private void handleCompletion(FaceSelectTask.TaskResult result, boolean appendMode) {
        runningTask = null;
        if (result == null || result.blocks() == null) {
            return;
        }

        if (result.cancelled()) {
            LOGGER.debug("[FaceSelectTool] 任务已取消");
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (!appendMode) {
            currentSelection.clear();
            manager.clearSelection();
        }
        currentSelection.addBlocks(result.blocks());
        manager.addAllToSelection(result.blocks());

        ToolPreviewManager.getInstance().updateFacePreview(result.blocks(), tintFor(appendMode, result.reachedLimit()));

        if (result.reachedLimit()) {
            LOGGER.warn("[FaceSelectTool] 达到最大限制 ({} 个方块)", config.getMaxSelectionSize());
        } else {
            LOGGER.info("[FaceSelectTool] 面选择完成，选择 {} 个方块", result.blocks().size());
        }
    }

    private void cancelRunningTask() {
        if (runningTask != null) {
            runningTask.cancel();
            runningTask = null;
        }
    }

    private Direction getClickedFace() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.crosshairTarget instanceof BlockHitResult hit) {
            return hit.getSide();
        }
        return null;
    }

    /**
     * 根据配置将“面选择”约束到指定平面。
     * AUTO：保持现有逻辑，使用鼠标命中面
     * XY/XZ/YZ：忽略命中面，固定为指定平面（通过选择一个对应轴的 Direction 实现）
     */
    private Direction resolvePlaneFace(Direction clickedFace) {
        FaceSelectConfig.PlaneMode mode = config.getPlaneMode();
        if (mode == null || mode == FaceSelectConfig.PlaneMode.AUTO) {
            return clickedFace;
        }
        return switch (mode) {
            case XY -> Direction.NORTH; // Axis.Z
            case XZ -> Direction.UP;    // Axis.Y
            case YZ -> Direction.EAST;  // Axis.X
            case AUTO -> clickedFace;
        };
    }

    private void clearHoverPreview() {
        synchronized (hoverLock) {
            if (currentHoverTask != null) {
                currentHoverTask.cancel();
                currentHoverTask = null;
            }
        }
        ToolPreviewManager.get().clearFaceHoverPreviewImmediately();
    }

    /** 与框选、魔棒、刷选一致：使用 SelectionManager.getEffectiveBlockFilter()，空管道时 nonAir。 */
    private BlockFilter getEffectiveBlockFilter() {
        SelectionManager m = SelectionManager.get();
        return m != null ? m.getEffectiveBlockFilter() : BlockFilters.nonAir();
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        cancelRunningTask();
        currentSelection.clear();
        SelectionManager.get().clearSelection();
        ToolPreviewManager.get().clearFacePreview();
        clearHoverPreview();
        LOGGER.debug("[FaceSelectTool] 清除选择");
    }

    // ====================== FaceSelectConfig 代理（用于 ToolPropertiesPanel） ======================
    // 说明：本类底部已存在 max/step/规则相关的 getter/setter，这里只补充 planeMode 接口，避免重复定义。

    public FaceSelectConfig.PlaneMode getPlaneMode() {
        return config.getPlaneMode();
    }

    public void setPlaneMode(FaceSelectConfig.PlaneMode mode) {
        config.setPlaneMode(mode);
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty();
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[FaceSelectTool] 工具激活");
    }

    @Override
    public void onDeactivate(World world) {
        cancelRunningTask();
        ToolPreviewManager.get().clearFacePreview();
        clearHoverPreview();
        LOGGER.debug("[FaceSelectTool] 工具停用");
    }

    public FaceSelectConfig getConfig() {
        return config;
    }

    public int getMaxSelectionSize() {
        return config.getMaxSelectionSize();
    }

    public void setMaxSelectionSize(int value) {
        config.setMaxSelectionSize(value);
    }

    public boolean isIgnoreTransparent() {
        return config.isIgnoreTransparent();
    }

    public void setIgnoreTransparent(boolean ignoreTransparent) {
        config.setIgnoreTransparent(ignoreTransparent);
    }

    public boolean isPropagateByMaterial() {
        return config.isPropagateByMaterial();
    }

    public void setPropagateByMaterial(boolean propagateByMaterial) {
        config.setPropagateByMaterial(propagateByMaterial);
    }

    public boolean isStrictBlockState() {
        return config.isStrictBlockState();
    }

    public void setStrictBlockState(boolean strictBlockState) {
        config.setStrictBlockState(strictBlockState);
    }

    public int getStepSize() {
        return config.getStepSize();
    }

    public void setStepSize(int stepSize) {
        config.setStepSize(stepSize);
    }

    private ToolPreviewManager.FacePreviewTint tintFor(boolean appendMode, boolean reachedLimit) {
        if (reachedLimit) {
            return ToolPreviewManager.FacePreviewTint.BOUNDARY;
        }
        return appendMode ? ToolPreviewManager.FacePreviewTint.ADDITIVE : ToolPreviewManager.FacePreviewTint.BASE;
    }
}
