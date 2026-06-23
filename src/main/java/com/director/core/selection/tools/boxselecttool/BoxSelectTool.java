package com.director.core.selection.tools.boxselecttool;

import com.director.core.DirectorServices;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoType;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.ToolPreviewManager;
import com.director.core.selection.*;
import com.director.core.selection.algorithms.RegionSelectors;
import com.director.core.selection.events.SelectionEvents;
import com.director.core.selection.block_filters.BlockFilters;
import com.director.core.selection.history.SelectionHistoryManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.core.selection.tools.SelectionPhase;
import com.director.ui.theme.Icons;
import com.director.core.util.SnapUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 📦 BoxSelectTool v2.0
 * <p>
 * —— Director 框选工具增强版
 * <p>
 * 功能特性：
 * ✅ 两点式建盒（左键第一次设置起点，第二次设置终点）
 * ✅ 面中心双向箭头调整选区大小（FaceHandleManager）
 * ✅ 一方块步进（网格对齐）
 * ✅ 支持新建选区 / 加选 / 减选 / 交集 模式
 * ✅ 实时渲染预览（ToolPreviewManager）
 * ✅ 自动同步 SelectionEvents（用于 Renderer / Gizmo / Timeline）
 * ✅ 模块化面手柄交互
 * ✅ 操作性能优化（高效交集算法）
 * ✅ 历史记录支持（撤销/重做）
 *
 * @author Director
 * @version 2.0
 */
public class BoxSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoxSelectTool.class);

    // ====================== 状态机 ======================
    private SelectionPhase phase = SelectionPhase.IDLE;
    
    // ====================== 两点式建盒状态 ======================
    private BlockPos firstCorner = null;  // 第一角点（左键点击）
    private BlockPos previewCorner = null; // 预览中的第二角点（鼠标移动实时更新）
    
    // ====================== Gizmo 编辑状态 ======================
    @SuppressWarnings("unused")
    // ====================== 面中心小方块管理器 ======================
    private final FaceHandleManager faceHandleManager = FaceHandleManager.SHARED;

    // ====================== 操作模式 ======================
    private SelectionOperation operation = SelectionOperation.NEW;

    @Override
    public String getId() {
        return "box";
    }

    @Override
    public String getIcon() {
        return Icons.BOX;
    }

    // ====================== 事件处理 ======================

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) return;

        // 如果正在拖拽面中心小方块，不处理点击
        if (faceHandleManager.isDragging()) {
            LOGGER.debug("[BoxSelectTool] 正在拖拽面中心小方块，跳过点击");
            return;
        }

        // 两点式：第一次左键设置起点，第二次左键直接确认终点并应用
        if (firstCorner == null) {
            firstCorner = pos.toImmutable();
            previewCorner = firstCorner;
            phase = SelectionPhase.FIRST_POINT_SET;

            // 兜底：确保工具预览管理器处于启用状态，避免状态残留导致预览不可见。
            ToolPreviewManager.getInstance().setEnabled(true);
            clearPreview();

            // 显示第一角点预览（单个方块）
            Box previewBox = makeInclusiveBlockBox(firstCorner, firstCorner).expand(0.01);
            ToolPreviewManager.getInstance().setPreviewBox(previewBox);

            LOGGER.info("[BoxSelectTool] ✅ 第一角点已设置: {} (左键点击第二角点完成选择)", firstCorner);
            return;
        }

        Box box = makeInclusiveBlockBox(firstCorner, pos);
        applyBoxSelection(world, box);
        clearPreview();
        LOGGER.info("[BoxSelectTool] ✅ 两点式建盒完成: {} -> {}", firstCorner, pos);
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
    }
    
    @Override
    public void onRightClick(World world, BlockPos pos) {
        if (world == null) return;

        // 右键用于取消当前两点式框选（不参与确认第二角点）
        if (firstCorner != null) {
            clearPreview();
            LOGGER.info("[BoxSelectTool] 取消两点式框选，已清除第一角点: {}", firstCorner);
            firstCorner = null;
            previewCorner = null;
            phase = SelectionPhase.IDLE;
        }
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        // 两点式框选由 onClick 完成；面手柄拖拽由 SelectionInputHandler 统一驱动。
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        // 面手柄拖拽由 SelectionInputHandler.tick 驱动。
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        // 面手柄拖拽结束由 SelectionInputHandler.tick 轮询鼠标释放完成。
    }

    @Override
    public void tick(World world) {
        // 两点式建盒：第一角点已设置时，用当前鼠标指向方块更新预览框
        if (phase == SelectionPhase.FIRST_POINT_SET && firstCorner != null && !faceHandleManager.isDragging()) {
            BlockPos hovered = getHoveredBlockPos();
            if (hovered != null) {
                previewCorner = hovered.toImmutable();
            }
            if (previewCorner != null) {
                Box preview = makeDragBoxWithMinHeight(firstCorner, previewCorner).expand(0.01);
                ToolPreviewManager.getInstance().setPreviewBox(preview);
            }
        }
    }

    private BlockPos getHoveredBlockPos() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null || client.options == null) {
                return null;
            }

            // 优先复用输入系统当前射线命中，保证与点击/掠过逻辑一致。
            BlockHitResult current = DirectorInputSystem.getCurrentRay();
            if (current != null && current.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                return current.getBlockPos();
            }

            Vec3d[] ray = DirectorInputSystem.getRayOriginAndDirection();
            if (ray == null || ray.length != 2) {
                return null;
            }

            double maxDist = client.options.getViewDistance().getValue() * 16.0;
            Vec3d endPos = ray[0].add(ray[1].multiply(maxDist));
            BlockHitResult hit = client.world.raycast(new net.minecraft.world.RaycastContext(
                    ray[0],
                    endPos,
                    net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    client.player
            ));

            if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                return hit.getBlockPos();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void clearSelection() {
        DirectorServices.get().selection().clearSelection();
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
        LOGGER.debug("[BoxSelectTool] 清除选区");
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = DirectorServices.get().selection();
        return phase != SelectionPhase.IDLE
            || !manager.getSelectionSet().isEmpty()
            || manager.getCurrentBounds() != null;
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = DirectorServices.get().selection();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet(), getId());
    }

    @Override
    public void onActivate(World world) {
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        LOGGER.debug("[BoxSelectTool] 工具激活 (模式: {})", operation.name());
    }

    @Override
    public void onDeactivate(World world) {
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        LOGGER.debug("[BoxSelectTool] 工具停用");
    }

    // ====================== 辅助逻辑 ======================

    /**
     * 两点（BlockPos）创建"含体积"的方块盒（max 边 +1，以覆盖整块）
     */
    private static Box makeInclusiveBlockBox(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 拖拽式：最小高度=1 方块（若 Y 相同或相邻，强制厚度=1）
     */
    private static Box makeDragBoxWithMinHeight(BlockPos a, BlockPos b) {
        Box raw = makeInclusiveBlockBox(a, b);
        return SnapUtil.ensureMinThicknessOne(raw);
    }
    
    /**
     * 应用盒子选择：根据 operation 合并到 SelectionSet，触发事件，激活 Gizmo（Bounds）
     */
    private void applyBoxSelection(World world, Box box) {
        if (world == null || box == null) return;

        SelectionManager manager = DirectorServices.get().selection();
        SelectionSet set = manager.getSelectionSet();

        if (operation == SelectionOperation.NEW) {
            manager.setSelectionBoundsOverride(box);
        } else {
            manager.clearSelectionBoundsOverride();
        }
        
        // 📊 记录操作前状态（用于历史记录）
        SelectionState beforeState = SelectionState.of(set.asUnmodifiableSet(), getId());

        // 框选工具：选中框内所有非空气方块，这样用户可以看到被选中的方块样式。
        // 框选工具允许自由调整选框大小（不受方块分布影响），但最终选区只包含实际存在的方块。
        Set<BlockPos> found = RegionSelectors.selectInBox(world, box, BlockFilters.nonAir());

        int beforeSize = set.size();
        
        // 快速模式运算
        switch (operation) {
            case NEW -> {
                // 新建选区：清除现有选区，然后添加新选区
                set.clear();
                set.addAll(found);
            }
            case ADD -> set.addAll(found);
            case SUBTRACT -> {
                // SelectionSet 没有 removeAll，使用 removeIf
                Set<BlockPos> toRemove = new HashSet<>(found);
                set.removeIf(toRemove::contains);
            }
            case INTERSECT -> {
                // ⚡ 高效交集：只保留 found 中存在的方块
                Set<BlockPos> toKeep = new HashSet<>(found);
                // 移除不在交集中的方块
                set.removeIf(pos -> !toKeep.contains(pos));
            }
        }

        int afterSize = set.size();
        int delta = switch (operation) {
            case NEW, ADD -> Math.max(0, afterSize - beforeSize);
            case SUBTRACT, INTERSECT -> Math.max(0, beforeSize - afterSize);
        };
        LOGGER.info("[BoxSelectTool] 框选完成 ({} 模式) → 影响 {} 个方块", operation.name(), delta);

        // 📊 记录操作到历史管理器
        try {
            SelectionState afterState = SelectionState.of(set.asUnmodifiableSet(), getId());
            SelectionHistoryManager historyManager = SelectionHistoryManager.getInstance();
            historyManager.recordOperation(beforeState, afterState, operation.name(), getId());
        } catch (Exception e) {
            LOGGER.warn("[BoxSelectTool] 记录历史失败", e);
        }

        // 🔔 触发事件同步
        SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);
    }

    // ====================== 清理方法 ======================
    
    /**
     * 清理拖拽态与预览
     */
    private void clearDrag() {
        // 保留方法用于兼容既有调用；当前框选工具已不使用普通框选拖拽状态。
    }
    
    /**
     * 清理预览
     */
    private void clearPreview() {
        previewCorner = null;
        ToolPreviewManager.getInstance().clearPreview();
    }
    
    /**
     * 清理选区 BOUNDS Gizmo（若仍存在则停用，不影响演员 TRANSFORM Gizmo）
     */
    private void clearGizmo() {
        try {
            GizmoManager gm = GizmoManager.get();
            if (gm.isActive() && gm.getContext() != null && gm.getContext().getType() == GizmoType.BOUNDS) {
                gm.deactivate();
            }
        } catch (Exception e) {
            LOGGER.debug("[BoxSelectTool] 停用 BOUNDS Gizmo 失败", e);
        }
    }

    // ====================== Getters & Setters ======================

    public boolean isDragging() {
        return phase == SelectionPhase.DRAGGING;
    }

    @Override
    public void setOperation(SelectionOperation op) {
        this.operation = op;
        LOGGER.debug("[BoxSelectTool] 操作模式切换为: {}", op.name());
    }

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    /**
     * 检查是否正在拖拽面中心小方块
     * 用于 SelectionInputHandler 判断是否需要阻止框选拖拽
     */
    public boolean isDraggingFaceHandle() {
        return faceHandleManager.isDragging();
    }

}
