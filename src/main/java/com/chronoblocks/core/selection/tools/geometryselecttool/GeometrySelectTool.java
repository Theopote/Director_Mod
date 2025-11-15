package com.chronoblocks.core.selection.tools.geometryselecttool;

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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 🧭 GeometrySelectTool v2.0
 *
 * 可参数化的几何选区工具，支持球体、圆柱体、金字塔和立方体等基本形状。
 * 通过 {@link GeometryConfig} 配置参数，并将生成的方块集合交由 SelectionManager 处理。
 */
public final class GeometrySelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeometrySelectTool.class);

    private final GeometryConfig config = new GeometryConfig();
    private GeometryShape shape = GeometryShape.SPHERE;
    private SelectionOperation operation = SelectionOperation.NEW;
    private boolean includeNonSolid = false;

    private World lastWorld;
    private BlockPos lastCenter;

    private final ToolPreviewManager previewManager = ToolPreviewManager.getInstance();

    @Override
    public String getId() {
        return "geometry";
    }

    @Override
    public String getDisplayName() {
        return "几何选择";
    }

    @Override
    public String getDescription() {
        return "生成球体、圆柱体、金字塔或立方体等几何形状的选区。";
    }

    @Override
    public String getIcon() {
        return "\uE942";
    }

    // === SelectionOperationSupport ===

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    @Override
    public void setOperation(SelectionOperation operation) {
        this.operation = Objects.requireNonNullElse(operation, SelectionOperation.NEW);
    }

    // === 几何参数 ===

    public GeometryShape getShape() {
        return shape;
    }

    public void setShape(GeometryShape shape) {
        if (shape == null || shape == this.shape) {
            return;
        }
        this.shape = shape;
        refreshPreview();
    }

    public GeometryConfig getConfig() {
        return config;
    }

    public boolean isIncludeNonSolid() {
        return includeNonSolid;
    }

    public void setIncludeNonSolid(boolean includeNonSolid) {
        if (this.includeNonSolid != includeNonSolid) {
            this.includeNonSolid = includeNonSolid;
            refreshPreview();
        }
    }

    /**
     * 当参数发生变化时，可调用此方法刷新几何体预览。
     */
    public void refreshPreview() {
        if (lastWorld != null && lastCenter != null) {
            Set<BlockPos> previewBlocks = generateSelection(lastWorld, lastCenter);
            previewManager.updateGeometryPreview(previewBlocks, shape, config.copy());
        }
    }

    // === SelectionTool 生命周期 ===

    @Override
    public void onActivate(World world) {
        this.lastWorld = world;
        previewManager.clearGeometryPreview();
        LOGGER.info("[GeometrySelectTool] 激活，形状={}，半径={}，高度={}，空心={}",
            shape, config.getRadius(), config.getHeight(), config.isHollow());
    }

    @Override
    public void onDeactivate(World world) {
        previewManager.clearGeometryPreview();
        lastWorld = null;
        lastCenter = null;
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }

        lastWorld = world;
        lastCenter = pos.toImmutable();

        SelectionOperation effectiveOperation = isShiftDown ? SelectionOperation.ADD : operation;
        Set<BlockPos> blocks = generateSelection(world, pos);

        applySelection(blocks, effectiveOperation);
        previewManager.updateGeometryPreview(blocks, shape, config.copy());

        LOGGER.info("[GeometrySelectTool] 生成 {} 选区，形状={}，块数={}，包含非实体={}",
            effectiveOperation, shape.getDisplayName(), blocks.size(), includeNonSolid);
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        // 预留：未来支持拖拽调节参数
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        // 预留：未来支持拖拽调节参数
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        // 预留
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return manager != null ? SelectionState.of(manager.getSelectionSet().asUnmodifiableSet()) : SelectionState.empty();
    }

    @Override
    public void clearSelection() {
        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.clearSelection();
        }
        previewManager.clearGeometryPreview();
        lastCenter = null;
        LOGGER.debug("[GeometrySelectTool] 清空几何选区");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return true;
    }

    // === 核心逻辑 ===

    private Set<BlockPos> generateSelection(World world, BlockPos center) {
        Set<BlockPos> raw = GeometryShapeGenerator.generate(center, shape, config);
        if (includeNonSolid) {
            return raw;
        }
        Set<BlockPos> filtered = new HashSet<>(raw.size());
        for (BlockPos pos : raw) {
            if (!world.isAir(pos)) {
                filtered.add(pos);
            }
        }
        return filtered;
    }

    private void applySelection(Set<BlockPos> blocks, SelectionOperation op) {
        if (blocks.isEmpty()) {
            LOGGER.debug("[GeometrySelectTool] 生成集合为空，忽略选区操作");
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager == null) {
            LOGGER.warn("[GeometrySelectTool] SelectionManager 不可用，无法应用选区");
            return;
        }

        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.addAllToSelection(blocks);
            }
            case ADD -> manager.addAllToSelection(blocks);
            case SUBTRACT -> blocks.forEach(manager::removeFromSelection);
            case INTERSECT -> manager.getSelectionSet().removeIf(pos -> !blocks.contains(pos));
        }
    }
}
