package com.chronoblocks.core.selection.events;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 选择事件处理器
 * 
 * 负责处理选择相关事件，协调各个组件之间的交互。
 * 包括工具状态更新、选择变化通知、UI更新等。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionEventHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionEventHandler.class);
    
    /** 事件总线 */
    private final SelectionEventBus eventBus;
    
    /** 选择管理器 */
    @SuppressWarnings("unused")
    private final SelectionManager selectionManager;
    
    /** 是否启用调试模式 */
    private final boolean debug = Boolean.getBoolean("chronoblocks.selection.events.debug");
    
    public SelectionEventHandler() {
        this.eventBus = SelectionEventBus.getInstance();
        this.selectionManager = SelectionManager.get();
        
        // 注册事件监听器
        registerEventListeners();
        
        LOGGER.info("[SelectionEventHandler] 事件处理器初始化完成");
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 工具激活事件
        eventBus.subscribe(ToolActivatedEvent.class, this::handleToolActivated, 10);
        
        // 工具停用事件
        eventBus.subscribe(ToolDeactivatedEvent.class, this::handleToolDeactivated, 10);
        
        // 选择变化事件
        eventBus.subscribe(SelectionChangedEvent.class, this::handleSelectionChanged, 20);
        
        // 工具点击事件
        eventBus.subscribe(ToolClickEvent.class, this::handleToolClick, 30);
        
        // 工具拖拽事件
        eventBus.subscribe(ToolDragEvent.class, this::handleToolDrag, 30);
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 事件监听器注册完成");
        }
    }
    
    /**
     * 处理工具激活事件
     */
    private void handleToolActivated(ToolActivatedEvent event) {
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 处理工具激活事件: {}", event);
        }
        
        // 更新UI状态
        updateUIForToolActivation(event);
        
        // 通知其他组件
        notifyToolActivation(event);
    }
    
    /**
     * 处理工具停用事件
     */
    private void handleToolDeactivated(ToolDeactivatedEvent event) {
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 处理工具停用事件: {}", event);
        }
        
        // 清理工具状态
        cleanupToolState(event);
        
        // 通知其他组件
        notifyToolDeactivation(event);
    }
    
    /**
     * 处理选择变化事件
     */
    private void handleSelectionChanged(SelectionChangedEvent event) {
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 处理选择变化事件: {}", event);
        }
        
        // 更新选择状态
        updateSelectionState(event);
        
        // 通知UI更新
        notifySelectionChange(event);
        
        // 触发相关操作
        triggerSelectionActions(event);
    }
    
    /**
     * 处理工具点击事件
     */
    private void handleToolClick(ToolClickEvent event) {
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 处理工具点击事件: {}", event);
        }
        
        // 执行工具点击逻辑
        executeToolClick(event);
        
        // 更新选择状态
        updateSelectionAfterClick(event);
    }
    
    /**
     * 处理工具拖拽事件
     */
    private void handleToolDrag(ToolDragEvent event) {
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 处理工具拖拽事件: {}", event);
        }
        
        // 执行工具拖拽逻辑
        executeToolDrag(event);
        
        // 更新选择状态
        updateSelectionAfterDrag(event);
    }
    
    /**
     * 更新UI以响应工具激活
     */
    private void updateUIForToolActivation(ToolActivatedEvent event) {
        // 这里可以通知UI组件更新工具状态
        // 例如：更新工具栏高亮、更新工具提示等
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 更新UI工具激活状态: {}", event.getToolId());
        }
    }
    
    /**
     * 通知工具激活
     */
    private void notifyToolActivation(ToolActivatedEvent event) {
        // 这里可以通知其他系统工具已激活
        // 例如：通知属性面板、时间线面板等
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 通知工具激活: {}", event.getToolId());
        }
    }
    
    /**
     * 清理工具状态
     */
    private void cleanupToolState(ToolDeactivatedEvent event) {
        // 清理工具相关状态
        // 例如：清理临时选择、重置工具状态等
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 清理工具状态: {}", event.getToolId());
        }
    }
    
    /**
     * 通知工具停用
     */
    private void notifyToolDeactivation(ToolDeactivatedEvent event) {
        // 通知其他系统工具已停用
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 通知工具停用: {}", event.getToolId());
        }
    }
    
    /**
     * 更新选择状态
     */
    private void updateSelectionState(SelectionChangedEvent event) {
        // 更新选择状态
        SelectionState selectionState = event.getSelectionState();
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 更新选择状态: {} 个方块", 
                selectionState.getSelectedBlocks().size());
        }
    }
    
    /**
     * 通知选择变化
     */
    private void notifySelectionChange(SelectionChangedEvent event) {
        // 通知UI组件选择已变化
        // 例如：更新属性面板、更新选择显示等
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 通知选择变化: +{} -{}", 
                event.getAddedBlocks().size(), 
                event.getRemovedBlocks().size());
        }
    }
    
    /**
     * 触发选择相关操作
     */
    private void triggerSelectionActions(SelectionChangedEvent event) {
        // 触发选择相关的操作
        // 例如：自动保存选择、更新统计信息等
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 触发选择操作: {}", event.getToolId());
        }
    }
    
    /**
     * 执行工具点击逻辑
     */
    private void executeToolClick(ToolClickEvent event) {
        // 执行工具点击的具体逻辑
        // 这里可以添加通用的点击处理逻辑
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 执行工具点击: {} 位置: {}", 
                event.getToolId(), event.getClickPos());
        }
    }
    
    /**
     * 更新点击后的选择状态
     */
    private void updateSelectionAfterClick(ToolClickEvent event) {
        // 更新点击后的选择状态
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 更新点击后选择状态: {}", event.getToolId());
        }
    }
    
    /**
     * 执行工具拖拽逻辑
     */
    private void executeToolDrag(ToolDragEvent event) {
        // 执行工具拖拽的具体逻辑
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 执行工具拖拽: {} 类型: {}", 
                event.getToolId(), event.getDragType());
        }
    }
    
    /**
     * 更新拖拽后的选择状态
     */
    private void updateSelectionAfterDrag(ToolDragEvent event) {
        // 更新拖拽后的选择状态
        
        if (debug) {
            LOGGER.debug("[SelectionEventHandler] 更新拖拽后选择状态: {}", event.getToolId());
        }
    }
    
    /**
     * 发布工具激活事件
     */
    public void publishToolActivated(ToolActivatedEvent event) {
        eventBus.publish(event);
    }
    
    /**
     * 发布工具停用事件
     */
    public void publishToolDeactivated(ToolDeactivatedEvent event) {
        eventBus.publish(event);
    }
    
    /**
     * 发布选择变化事件
     */
    public void publishSelectionChanged(SelectionChangedEvent event) {
        eventBus.publish(event);
    }
    
    /**
     * 发布工具点击事件
     */
    public void publishToolClick(ToolClickEvent event) {
        eventBus.publish(event);
    }
    
    /**
     * 发布工具拖拽事件
     */
    public void publishToolDrag(ToolDragEvent event) {
        eventBus.publish(event);
    }
    
    /**
     * 获取事件总线
     */
    public SelectionEventBus getEventBus() {
        return eventBus;
    }
}
