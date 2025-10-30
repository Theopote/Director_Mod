package com.chronoblocks.core.selection;

import com.chronoblocks.core.selection.events.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
 * 
 * 负责管理当前选中工具、选区状态与世界交互事件。
 * - 处理工具切换（来自 Toolbar）
 * - 转发鼠标事件至当前工具
 * - 发出选区变化事件给属性面板 / 时间线
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    private static SelectionManager INSTANCE;

    private SelectionTool currentTool;
    private final SelectionRegistry registry = new SelectionRegistry();
    private final List<Consumer<SelectionState>> selectionChangeListeners = new ArrayList<>();
    
    /** 选区集合 */
    private final SelectionSet selectionSet = new SelectionSet();
    
    /** 事件处理器 */
    private SelectionToolEventProcessor eventProcessor;

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
        registry.register(new com.chronoblocks.core.selection.tools.BoxSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.MagicWandTool());
        
        // 高级工具
        registry.register(new com.chronoblocks.core.selection.tools.FaceSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.SphereSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.CylinderSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.PyramidSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.ConnectedSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.InverseSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.TypeFilterTool());
        registry.register(new com.chronoblocks.core.selection.tools.BrushSelectTool());
        registry.register(new com.chronoblocks.core.selection.tools.LassoSelectTool());
        
        // 多模式选择工具
        registry.register(new com.chronoblocks.core.selection.tools.BoxSelectTool_v2());
        
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
    }

    /**
     * 添加到选区
     */
    public void addToSelection(BlockPos pos) { 
        if (pos != null) selectionSet.add(pos); 
    }

    /**
     * 批量添加到选区
     */
    public void addAllToSelection(Collection<BlockPos> positions) {
        selectionSet.addAll(positions);
    }

    /**
     * 从选区移除
     */
    public void removeFromSelection(BlockPos pos) { 
        if (pos != null) selectionSet.remove(pos); 
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
     * 处理拖拽开始
     */
    public void handleDragStart(World world, net.minecraft.util.math.BlockPos start) {
        if (currentTool != null) {
            // 发布工具拖拽开始事件
            getEventProcessorInternal().processToolDragStart(world, start);
            
            // 执行工具拖拽开始逻辑
            currentTool.onDragStart(world, start);
            
            // 开始工具预览
            com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().startDragPreview(start);
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
            
            // 更新工具预览
            com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().updateDragPreview(current);
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
            
            // 结束工具预览
            com.chronoblocks.client.renderer.ToolPreviewManager.getInstance().endDragPreview();
            
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
     */
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

    /**
     * 清除当前选择
     */
    public void clearCurrentSelection() {
        if (currentTool != null) {
            currentTool.clearSelection();
            notifySelectionChanged();
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
     * 获取事件处理器
     */
    public SelectionToolEventProcessor getEventProcessor() {
        return getEventProcessorInternal();
    }
    
    /**
     * 获取事件总线
     */
    public SelectionEventBus getEventBus() {
        return getEventProcessorInternal().getEventBus();
    }
}
