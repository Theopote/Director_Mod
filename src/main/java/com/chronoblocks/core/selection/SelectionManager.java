package com.chronoblocks.core.selection;

import com.chronoblocks.client.renderer.SelectionRenderer;
import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.core.selection.events.*;
import com.chronoblocks.core.selection.block_filters.BlockFilter;
import com.chronoblocks.core.selection.block_filters.BlockFilterPipeline;
import com.chronoblocks.core.selection.block_filters.BlockFilters;
import com.chronoblocks.core.selection.block_filters.BlockFilterRegistry;
import com.chronoblocks.core.selection.block_filters.FilterIds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.chronoblocks.client.gizmo.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * 🎯 SelectionManager - 选择工具管理器
 * <p>
 * 负责管理当前选中工具、选区状态与世界交互事件。
 * - 处理工具切换（来自 Toolbar）
 * - 转发鼠标事件至当前工具
 * - 发出选区变化事件给属性面板 / 时间线
 * - 管理全局过滤管道（由 SelectionFilterPanel 配置）
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    private static SelectionManager INSTANCE;

    private SelectionTool currentTool;
    private final SelectionRegistry registry = new SelectionRegistry();
    private final List<Consumer<SelectionState>> selectionChangeListeners = new ArrayList<>();
    
    /** 选区集合 */
    private final SelectionSet selectionSet = new SelectionSet();

    // ===== 面拖拽实时预览状态 (v4.9) =====
    private Box activeResizeBaseBounds = null;
    private Box activeResizePreviewBounds = null;
    private Integer activeResizeFaceIndex = null;
    private double activeResizeOffset = 0.0;

    // ===== BOUNDS Gizmo（移动包围盒）实时预览状态 =====
    private Box activeBoundsGizmoMovePreviewBounds = null;
    
    /** 事件处理器 */
    private SelectionToolEventProcessor eventProcessor;
    
    // ================================
    // 🧩 实时预览同步配置 (v3.1)
    // ================================
    
    /** 是否自动显示预览框 */
    private boolean autoPreviewEnabled = true;
    
    /** 全局过滤管道（可由 SelectionFilterPanel 配置）；默认显式加入 exclude_air，不再用「空管道=忽略空气」隐式规则。 */
    private final BlockFilterPipeline globalPipeline = createInitialPipeline();

    private static BlockFilterPipeline createInitialPipeline() {
        BlockFilter f = BlockFilterRegistry.getFilter(FilterIds.EXCLUDE_AIR);
        return new BlockFilterPipeline().add(f != null ? f : BlockFilters.nonAir());
    }

    private SelectionManager() {
        registerDefaultTools();
        // 延迟初始化事件处理器，避免循环依赖
        this.eventProcessor = null;
    }

    public static SelectionManager get() {
        if (INSTANCE == null) {
            INSTANCE = new SelectionManager();
        }
        return INSTANCE;
    }
    
    /**
     * 延迟初始化事件处理器
     */
    private SelectionToolEventProcessor getEventProcessorInternal() {
        if (eventProcessor == null) {
            eventProcessor = new SelectionToolEventProcessor();
        }
        return eventProcessor;
    }

    /**
     * 注册默认工具
     */
    private void registerDefaultTools() {
        // 基础工具
        registry.register(new com.chronoblocks.core.selection.tools.ClickSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTool());
        
        // 高级工具
        registry.register(new com.chronoblocks.core.selection.tools.faceselecttool.FaceSelectTool());
        // 几何选择工具已合并到刷选工具中，不再单独注册
        // registry.register(new com.chronoblocks.core.selection.tools.geometry.GeometrySelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.brushselecttool.BrushSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.lassoselecttool.LassoSelectTool());
        
        // 多模式选择工具
        registry.register(new com.chronoblocks.core.selection.tools.boxselecttool.BoxSelectTool());
        
        LOGGER.info("[SelectionManager] 已注册 {} 个选择工具", registry.size());
    }

    /**
     * 设置当前工具
     */
    public void setCurrentTool(String toolId, World world) {
        SelectionTool tool = registry.getTool(toolId);
        if (tool == null) {
            LOGGER.warn("[SelectionManager] 无法切换到未知工具: {}", toolId);
            return;
        }

        // String previousToolId = currentTool != null ? currentTool.getId() : null; // 暂时不使用
        
        // 停用当前工具
        if (currentTool != null) {
            currentTool.onDeactivate(world);
            // 发布工具停用事件
            getEventProcessorInternal().processToolDeactivation(currentTool.getId(), world);
        }
        
        // 激活新工具
        currentTool = tool;
        currentTool.onActivate(world);
        
        // 发布工具激活事件
        getEventProcessorInternal().processToolActivation(toolId, world);

        LOGGER.info("[SelectionManager] 当前工具切换为: {}", tool.getDisplayName());
    }

    /**
     * 切换到下一个工具
     */
    public void switchToNextTool(World world) {
        SelectionTool nextTool = registry.getNextTool(currentTool);
        if (nextTool != null) {
            setCurrentTool(nextTool.getId(), world);
        }
    }

    /**
     * 切换到上一个工具
     */
    public void switchToPreviousTool(World world) {
        SelectionTool prevTool = registry.getPreviousTool(currentTool);
        if (prevTool != null) {
            setCurrentTool(prevTool.getId(), world);
        }
    }

    /**
     * 获取选区集合
     */
    public SelectionSet getSelectionSet() { 
        return selectionSet; 
    }

    /**
     * 清除选区
     */
    public void clearSelection() { 
        selectionSet.clear();
        
        // 🚀 v2.0: 使网格缓存失效，强制下次渲染时重新计算
        invalidateRendererCache();
        
        // 自动清除预览框
        if (autoPreviewEnabled) {
            try {
                ToolPreviewManager.getInstance().clearPreview();
            } catch (Exception e) {
                LOGGER.debug("[SelectionManager] 清除预览框失败（可能不在客户端）: {}", e.getMessage());
            }
        }
    }

    /**
     * 添加到选区
     */
    public void addToSelection(BlockPos pos) { 
        if (pos != null) {
            selectionSet.add(pos);
            // 🚀 v2.0: 使网格缓存失效
            invalidateRendererCache();
        }
    }

    /**
     * 批量添加到选区
     */
    public void addAllToSelection(Collection<BlockPos> positions) {
        selectionSet.addAll(positions);
        // 🚀 v2.0: 使网格缓存失效
        invalidateRendererCache();
    }

    /**
     * 从选区移除
     */
    public void removeFromSelection(BlockPos pos) { 
        if (pos != null) {
            selectionSet.remove(pos);
            // 🚀 v2.0: 使网格缓存失效
            invalidateRendererCache();
        }
    }
    
    /**
     * 🚀 v2.0: 使渲染器缓存失效的辅助方法
     */
    private void invalidateRendererCache() {
        try {
            SelectionRenderer.getInstance().invalidateCache();
        } catch (Exception e) {
            // 如果 SelectionRenderer 不可用（服务器端），忽略错误
            LOGGER.debug("[SelectionManager] 使网格缓存失效失败（可能不在客户端）: {}", e.getMessage());
        }
    }

    /**
     * 处理点击事件
     */
    public void handleClick(World world, BlockPos pos, boolean shift) {
        LOGGER.info("[SelectionManager] 处理点击事件: pos={}, shift={}, currentTool={}", 
            pos, shift, currentTool != null ? currentTool.getId() : "null");
        
        if (currentTool != null) {
            // 发布工具点击事件
            LOGGER.info("[SelectionManager] 发布工具点击事件");
            getEventProcessorInternal().processToolClick(world, pos, shift, false);
            
            // 执行工具点击逻辑
            LOGGER.info("[SelectionManager] 执行工具点击逻辑");
            currentTool.onClick(world, pos, shift);
            
            // 通知选择变化
            LOGGER.info("[SelectionManager] 通知选择变化");
            notifySelectionChanged();
        } else {
            LOGGER.warn("[SelectionManager] 没有当前工具，无法处理点击事件");
        }
    }
    
    /**
     * 处理右键点击事件
     */
    public void handleRightClick(World world, BlockPos pos) {
        LOGGER.debug("[SelectionManager] 处理右键点击事件: pos={}, currentTool={}", 
            pos, currentTool != null ? currentTool.getId() : "null");
        
        if (currentTool != null) {
            // 发布工具右键点击事件
            getEventProcessorInternal().processToolClick(world, pos, false, true);
            
            // 执行工具右键点击逻辑
            currentTool.onRightClick(world, pos);
            
            // 通知选择变化
            notifySelectionChanged();
        } else {
            LOGGER.debug("[SelectionManager] 没有当前工具，无法处理右键点击事件");
        }
    }

    /**
     * 处理拖拽开始
     */
    public void handleDragStart(World world, net.minecraft.util.math.BlockPos start) {
        if (currentTool != null) {
            // 发布工具拖拽开始事件
            getEventProcessorInternal().processToolDragStart(world, start);
            
            // 执行工具拖拽开始逻辑
            currentTool.onDragStart(world, start);

            // 开始工具预览（框选工具自己负责 setPreviewBox 实时预览，避免双重预览与 O(n^3) 预览计算）
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().startDragPreview(start);
            }
        }
    }

    /**
     * 处理拖拽更新
     */
    public void handleDragUpdate(World world, net.minecraft.util.math.BlockPos current) {
        if (currentTool != null) {
            // 发布工具拖拽更新事件
            getEventProcessorInternal().processToolDragUpdate(world, current, current);
            
            // 执行工具拖拽更新逻辑
            currentTool.onDragUpdate(world, current);

            // 更新工具预览（框选工具自己负责 setPreviewBox 实时预览）
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().updateDragPreview(current);
            }
        }
    }

    /**
     * 处理拖拽结束
     */
    public void handleDragEnd(World world, net.minecraft.util.math.BlockPos end) {
        if (currentTool != null) {
            // 发布工具拖拽结束事件
            getEventProcessorInternal().processToolDragEnd(world, end, end);
            
            // 执行工具拖拽结束逻辑
            currentTool.onDragEnd(world, end);

            // 结束工具预览（框选工具自己负责 clearPreview）
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().endDragPreview();
            }
            
            // 🚀 v2.0: 使网格缓存失效（拖拽结束通常意味着选区改变）
            invalidateRendererCache();
            
            // 通知选择变化
            notifySelectionChanged();
        }
    }

    /**
     * 每帧更新
     */
    public void tick(World world) {
        if (currentTool != null) {
            currentTool.tick(world);
        }
    }

    /**
     * 添加选择变化监听器
     */
    public void addSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.add(listener);
    }

    /**
     * 移除选择变化监听器
     * 
     * @deprecated 当前未使用，保留用于未来扩展
     */
    @Deprecated
    @SuppressWarnings("unused")
    public void removeSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.remove(listener);
    }

    /**
     * 通知选择状态变化
     */
    private void notifySelectionChanged() {
        if (currentTool != null) {
            SelectionState state = currentTool.getCurrentSelection();
            
            // 创建选择变化事件
            Set<BlockPos> addedBlocks = new HashSet<>(); // 简化实现，实际应该跟踪变化
            Set<BlockPos> removedBlocks = new HashSet<>(); // 简化实现，实际应该跟踪变化
            SelectionChangedEvent event = new SelectionChangedEvent(
                null, // 简化实现，暂时使用null
                state, addedBlocks, removedBlocks);
            
            // 发布选择变化事件
            getEventProcessorInternal().processSelectionChanged(null, event); // 简化实现，暂时使用null
            
            // 通知传统监听器
            for (Consumer<SelectionState> listener : selectionChangeListeners) {
                try {
                    listener.accept(state);
                } catch (Exception e) {
                    LOGGER.warn("[SelectionManager] 选择变化监听器执行失败", e);
                }
            }
        }
    }

    // ========== Getters ==========

    public SelectionTool getCurrentTool() {
        return currentTool;
    }

    public SelectionRegistry getRegistry() {
        return registry;
    }

    public SelectionState getCurrentSelection() {
        return SelectionState.of(selectionSet.asUnmodifiableSet());
    }
    

    public boolean hasCurrentTool() {
        return currentTool != null;
    }

    public String getCurrentToolId() {
        return currentTool != null ? currentTool.getId() : null;
    }

    public String getCurrentToolDisplayName() {
        return currentTool != null ? currentTool.getDisplayName() : "无工具";
    }

    /**
     * 获取全局过滤管道
     * 
     * @return 全局过滤管道实例，可用于区域选择工具
     */
    public BlockFilterPipeline getPipeline() {
        return globalPipeline;
    }

    /**
     * 获取用于方块选择的「有效过滤器」。空管道 = 全部通过，由 pipeline.test 语义保证。
     */
    public BlockFilter getEffectiveBlockFilter() {
        return globalPipeline::test;
    }

    /**
     * 获取用于需要 BlockFilterPipeline 的「有效管道」的副本。空管道 = 全部通过。
     */
    public BlockFilterPipeline getEffectiveFilterPipeline() {
        return globalPipeline.clone();
    }

    // =====================================================
    // 🔄 面拖拽实时预览 (v4.9)
    // =====================================================

    public void beginFaceResize(Box baseBounds, int faceIndex) {
        if (baseBounds == null) {
            cancelFaceResize();
            return;
        }

        activeResizeBaseBounds = copyBox(baseBounds);
        activeResizePreviewBounds = activeResizeBaseBounds;
        activeResizeFaceIndex = faceIndex;
        activeResizeOffset = 0.0;
    }

    public void previewFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        if (activeResizeFaceIndex == null) {
            return;
        }

        if (!activeResizeFaceIndex.equals(faceIndex)) {
            beginFaceResize(activeResizeBaseBounds != null ? activeResizeBaseBounds : getCurrentBounds(), faceIndex);
        }

        activeResizeOffset = offset;
        activeResizePreviewBounds = adjustBounds(activeResizeBaseBounds, activeResizeFaceIndex, activeResizeOffset);
    }

    public void applyFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        previewFaceResize(faceIndex, offset);

        if (activeResizePreviewBounds == null) {
            cancelFaceResize();
            return;
        }

        rebuildSelectionFromBounds(activeResizePreviewBounds);
        notifySelectionChanged();
        
        // 更新 Gizmo 位置以跟随新的选区中心
        updateGizmoPosition();
        
        cancelFaceResize();
    }

    public void cancelFaceResize() {
        activeResizeBaseBounds = null;
        activeResizePreviewBounds = null;
        activeResizeFaceIndex = null;
        activeResizeOffset = 0.0;
    }

    public boolean isFaceResizeActive() {
        return activeResizeBaseBounds != null;
    }

    public Box getResizePreviewBounds() {
        return activeResizePreviewBounds;
    }

    public boolean isBoundsGizmoMoveActive() {
        return activeBoundsGizmoMovePreviewBounds != null;
    }

    public Box getBoundsGizmoMovePreviewBounds() {
        return activeBoundsGizmoMovePreviewBounds;
    }

    public void setBoundsGizmoMovePreviewBounds(Box box) {
        activeBoundsGizmoMovePreviewBounds = box;
    }

    public void clearBoundsGizmoMovePreviewBounds() {
        activeBoundsGizmoMovePreviewBounds = null;
    }

    public Box getResizeBaseBounds() {
        return activeResizeBaseBounds;
    }

    private void rebuildSelectionFromBounds(Box bounds) {
        if (bounds == null) {
            return;
        }

        selectionSet.clear();

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }

        // ✅ 使用全局过滤管道（默认包含 nonAir）来重建选区，避免“包围盒里全是空气但选区为空/混乱”的体验
        World world = null;
        try {
            world = net.minecraft.client.MinecraftClient.getInstance().world;
        } catch (Throwable ignored) {
        }

        if (world == null) {
            // 兜底：无 world 时按坐标填充（至少不丢选区）
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        selectionSet.add(new BlockPos(x, y, z));
                    }
                }
            }
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var state = world.getBlockState(p);
                    if (globalPipeline.test(world, p, state)) selectionSet.add(p);
                }
            }
        }
    }

    /**
     * ✅ 供 BOUNDS Gizmo 使用：把一个“体积包围盒”转为选区（默认过滤掉空气）。
     *
     * <p>注意：这里<strong>不</strong>调用 {@link #updateGizmoPosition()}，否则拖拽过程中会反复重建 Gizmo，
     * 导致每次最多移动一格/拖拽中断。</p>
     */
    public void rebuildSelectionFromBoundsForBoundsGizmo(Box bounds) {
        if (bounds == null) {
            return;
        }

        World world;
        try {
            world = net.minecraft.client.MinecraftClient.getInstance().world;
        } catch (Throwable t) {
            world = null;
        }

        if (world == null) {
            // ✅ 没有 world 时不做“坐标填充”，避免把空气也选中。
            return;
        }

        selectionSet.clear();

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var state = world.getBlockState(p);
                    if (globalPipeline.test(world, p, state)) selectionSet.add(p);
                }
            }
        }

        // ✅ 选区重建后需要让渲染缓存失效，否则可能出现“选区没更新/不稳定”的视觉表现
        invalidateRendererCache();

        if (selectionSet.isEmpty()) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
        } else {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);
        }

        // 让面板/监听器也能收到变化（不触发 updateGizmoPosition，避免拖拽中断）
        notifySelectionChanged();
    }

    private Box adjustBounds(Box base, int faceIndex, double offset) {
        if (base == null) {
            return null;
        }

        double minX = base.minX;
        double minY = base.minY;
        double minZ = base.minZ;
        double maxX = base.maxX;
        double maxY = base.maxY;
        double maxZ = base.maxZ;

        double minThickness = 0.01;

        switch (faceIndex) {
            case 0 -> maxX = Math.max(minX + minThickness, maxX + offset);
            case 1 -> minX = Math.min(maxX - minThickness, minX - offset);
            case 2 -> maxY = Math.max(minY + minThickness, maxY + offset);
            case 3 -> minY = Math.min(maxY - minThickness, minY - offset);
            case 4 -> maxZ = Math.max(minZ + minThickness, maxZ + offset);
            case 5 -> minZ = Math.min(maxZ - minThickness, minZ - offset);
            default -> {
            }
        }

        if (maxX - minX < minThickness) {
            double mid = (minX + maxX) * 0.5;
            minX = mid - minThickness * 0.5;
            maxX = mid + minThickness * 0.5;
        }
        if (maxY - minY < minThickness) {
            double mid = (minY + maxY) * 0.5;
            minY = mid - minThickness * 0.5;
            maxY = mid + minThickness * 0.5;
        }
        if (maxZ - minZ < minThickness) {
            double mid = (minZ + maxZ) * 0.5;
            minZ = mid - minThickness * 0.5;
            maxZ = mid + minThickness * 0.5;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Box copyBox(Box source) {
        return source == null ? null : new Box(source.minX, source.minY, source.minZ,
                source.maxX, source.maxY, source.maxZ);
    }
    
    /**
     * 更新 Gizmo 位置以跟随选区中心
     */
    private void updateGizmoPosition() {
        // Gizmo 用于“整体移动包围盒/选区”，只有在显示包围盒时才启用，避免与拖拽建盒产生概念/交互冲突
        if (!com.chronoblocks.client.settings.RenderSettings.get().selection_showBoundingBox) {
            try {
                com.chronoblocks.client.gizmo.GizmoManager.get().deactivate();
            } catch (Exception ignored) {
            }
            return;
        }

        Box bounds = getCurrentBounds();
        if (bounds == null || selectionSet.isEmpty()) {
            return;
        }
        
        // 计算新的几何中心
        Vec3d newPivot = new Vec3d(
            (bounds.minX + bounds.maxX) / 2.0,
            (bounds.minY + bounds.maxY) / 2.0,
            (bounds.minZ + bounds.maxZ) / 2.0
        );
        
        try {
            com.chronoblocks.client.gizmo.GizmoManager gizmoManager = com.chronoblocks.client.gizmo.GizmoManager.get();
            if (gizmoManager != null && gizmoManager.isActive()) {
                // ✅ 不要在选区变化时反复 activate()（会重建 controller/handles，导致“拖拽只能动一格/不稳定”）
                gizmoManager.setPivot(newPivot);
                gizmoManager.setTargetBox(bounds);
                LOGGER.debug("[SelectionManager] 更新 Gizmo 位置(无重建): pivot={}", newPivot);
            }
        } catch (Exception e) {
            LOGGER.warn("[SelectionManager] 更新 Gizmo 位置失败", e);
        }
    }

    // =====================================================
    // 🧩 expandSelection — 选区缩放核心逻辑 (v3.0)
    // =====================================================
    
    /**
     * 沿指定轴扩展或收缩当前选区。
     * <p>
     * 支持功能：
     * ✅ 正/负方向扩展选区
     * ✅ 自动重新计算 Bounding Box
     * ✅ 兼容 BoxSelectTool_v3 与 GizmoRenderer
     * ✅ 实时预览刷新（预留）
     * 
     * @param axis  方向轴（X/Y/Z）
     * @param delta 增量（>0 扩展，<0 收缩）
     */
    public void expandSelection(Axis axis, int delta) {
        if (selectionSet.isEmpty()) {
            LOGGER.warn("[SelectionManager] 当前无选区，无法扩展");
            return;
        }
        
        // 计算当前选区边界（使用 getCurrentBounds()）
        Box bounds = getCurrentBounds();
        if (bounds == null) {
            LOGGER.warn("[SelectionManager] 无法计算选区边界");
            return;
        }
        
        int minX = (int) bounds.minX;
        int minY = (int) bounds.minY;
        int minZ = (int) bounds.minZ;
        int maxX = (int) bounds.maxX - 1; // Box 的 max 是 exclusive，需要减 1
        int maxY = (int) bounds.maxY - 1;
        int maxZ = (int) bounds.maxZ - 1;
        
        // 基于方向扩展边界
        switch (axis) {
            case X -> {
                if (delta > 0) {
                    // 正向扩展：在 maxX 外侧添加方块
                    for (int x = maxX + 1; x <= maxX + delta; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 负向收缩：移除 maxX 侧的外层方块
                    for (int x = maxX; x > maxX + delta; x--) {
                        final int removeX = x;
                        selectionSet.removeIf(p -> p.getX() == removeX);
                    }
                }
            }
            case Y -> {
                if (delta > 0) {
                    // 正向扩展：在 maxY 外侧添加方块
                    for (int y = maxY + 1; y <= maxY + delta; y++) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 负向收缩：移除 maxY 侧的外层方块
                    for (int y = maxY; y > maxY + delta; y--) {
                        final int removeY = y;
                        selectionSet.removeIf(p -> p.getY() == removeY);
                    }
                }
            }
            case Z -> {
                if (delta > 0) {
                    // 正向扩展：在 maxZ 外侧添加方块
                    for (int z = maxZ + 1; z <= maxZ + delta; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 负向收缩：移除 maxZ 侧的外层方块
                    for (int z = maxZ; z > maxZ + delta; z--) {
                        final int removeZ = z;
                        selectionSet.removeIf(p -> p.getZ() == removeZ);
                    }
                }
            }
        }
        
        LOGGER.info("[SelectionManager] 沿 {} 轴调整选区 Δ={}，当前方块数={}",
                axis.name(), delta, selectionSet.size());
        
        // 通知选择变化（触发渲染更新）
        notifySelectionChanged();
        
        // 🧩 实时同步预览框（如果启用）
        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] 设置预览框失败（可能不在客户端）: {}", e.getMessage());
                }
            }
        }
    }
    
    // =====================================================
    // 📦 offsetSelection — 平移选区 (v3.0)
    // =====================================================
    
    /**
     * 平移选区
     * 
     * @param offset 偏移向量
     */
    public void offsetSelection(BlockPos offset) {
        if (selectionSet.isEmpty()) {
            LOGGER.debug("[SelectionManager] 选区为空，跳过平移");
            return;
        }
        
        Set<BlockPos> moved = new HashSet<>();
        
        // 计算新的位置
        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            moved.add(pos.add(offset));
        }
        
        // 清空旧选区，添加新位置
        selectionSet.clear();
        selectionSet.addAll(moved);
        
        LOGGER.debug("[SelectionManager] 平移选区: offset={}, 方块数={}", offset, selectionSet.size());
        
        // 通知选择变化（触发渲染更新）
        notifySelectionChanged();
        
        // ✅ 更新 Gizmo 位置，跟随选区移动
        updateGizmoPosition();
        
        // 🧩 实时同步预览框（如果启用）
        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] 设置预览框失败（可能不在客户端）: {}", e.getMessage());
                }
            }
        }
    }
    
    // =====================================================
    // 📦 getCurrentBounds — 当前选区包围盒 (v3.1)
    // =====================================================
    
    /**
     * 获取当前选区的包围盒（Box）
     * <p>
     * 用于：
     * - Gizmo 系统获取选区边界
     * - SelectionRenderer 预览框同步
     * - 其他需要选区边界的功能
     * 
     * @return 当前选区的包围盒，如果选区为空则返回 null
     */
    public Box getCurrentBounds() {
        if (selectionSet.isEmpty()) {
            return null;
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        // 返回 Box（包含 min 到 max+1 的范围，因为 BlockPos 是整数坐标）
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
