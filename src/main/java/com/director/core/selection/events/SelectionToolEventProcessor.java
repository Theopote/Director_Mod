package com.director.core.selection.events;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 选择工具事件处理器
 * <p>
 * 负责处理选择工具的各种事件，包括点击、拖拽、激活等。
 * 作为SelectionManager和事件系统之间的桥梁。
 * 
 * @author Director Team
 * @version 1.0
 */
public class SelectionToolEventProcessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToolEventProcessor.class);
    
    /** 事件处理器 */
    private final SelectionEventHandler eventHandler;
    
    /** 选择管理器 */
    private final SelectionManager selectionManager;
    
    /** 是否启用调试模式 */
    private final boolean debug = Boolean.getBoolean("director.selection.events.debug");
    
    public SelectionToolEventProcessor() {
        this.eventHandler = new SelectionEventHandler();
        this.selectionManager = DirectorServices.get().selection();
        
        LOGGER.info("[SelectionToolEventProcessor] 工具事件处理器初始化完成");
    }
    
    /**
     * 处理工具激活
     */
    public void processToolActivation(String toolId, World world) {
        SelectionTool newTool = selectionManager.getRegistry().getTool(toolId);
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (newTool == null) {
            LOGGER.warn("[SelectionToolEventProcessor] 无法激活未知工具: {}", toolId);
            return;
        }
        
        String previousToolId = currentTool != null ? currentTool.getId() : null;
        
        // 创建工具激活事件
        ToolActivatedEvent event = new ToolActivatedEvent(world, newTool, previousToolId);
        
        // 发布事件
        eventHandler.publishToolActivated(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具激活: {} -> {}", previousToolId, toolId);
        }
    }
    
    /**
     * 处理工具停用
     */
    public void processToolDeactivation(String toolId, World world) {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null || !currentTool.getId().equals(toolId)) {
            LOGGER.warn("[SelectionToolEventProcessor] 无法停用工具: {} (当前工具: {})", 
                toolId, currentTool != null ? currentTool.getId() : "无");
            return;
        }
        
        // 创建工具停用事件
        ToolDeactivatedEvent event = new ToolDeactivatedEvent(world, currentTool, null);
        
        // 发布事件
        eventHandler.publishToolDeactivated(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具停用: {}", toolId);
        }
    }
    
    /**
     * 处理工具点击
     */
    public void processToolClick(World world, BlockPos clickPos, boolean isShiftDown, boolean isRightClick) {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null) {
            LOGGER.warn("[SelectionToolEventProcessor] 没有激活的工具，无法处理点击");
            return;
        }
        
        // 创建工具点击事件
        ToolClickEvent event = new ToolClickEvent(world, currentTool.getId(), clickPos, isShiftDown, isRightClick);
        
        // 发布事件
        eventHandler.publishToolClick(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具点击: {} 位置: {}", 
                currentTool.getId(), clickPos);
        }
    }
    
    /**
     * 处理工具拖拽开始
     */
    public void processToolDragStart(World world, BlockPos startPos) {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null) {
            LOGGER.warn("[SelectionToolEventProcessor] 没有激活的工具，无法处理拖拽开始");
            return;
        }
        
        // 创建工具拖拽事件
        ToolDragEvent event = new ToolDragEvent(world, currentTool.getId(), startPos, startPos, null, ToolDragEvent.DragType.START);
        
        // 发布事件
        eventHandler.publishToolDrag(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具拖拽开始: {} 位置: {}", 
                currentTool.getId(), startPos);
        }
    }
    
    /**
     * 处理工具拖拽更新
     */
    public void processToolDragUpdate(World world, BlockPos startPos, BlockPos currentPos) {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null) {
            LOGGER.warn("[SelectionToolEventProcessor] 没有激活的工具，无法处理拖拽更新");
            return;
        }
        
        // 创建工具拖拽事件
        ToolDragEvent event = new ToolDragEvent(world, currentTool.getId(), startPos, currentPos, null, ToolDragEvent.DragType.UPDATE);
        
        // 发布事件
        eventHandler.publishToolDrag(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具拖拽更新: {} 位置: {}", 
                currentTool.getId(), currentPos);
        }
    }
    
    /**
     * 处理工具拖拽结束
     */
    public void processToolDragEnd(World world, BlockPos startPos, BlockPos endPos) {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null) {
            LOGGER.warn("[SelectionToolEventProcessor] 没有激活的工具，无法处理拖拽结束");
            return;
        }
        
        // 创建工具拖拽事件
        ToolDragEvent event = new ToolDragEvent(world, currentTool.getId(), startPos, endPos, endPos, ToolDragEvent.DragType.END);
        
        // 发布事件
        eventHandler.publishToolDrag(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具拖拽结束: {} 开始: {} 结束: {}", 
                currentTool.getId(), startPos, endPos);
        }
    }
    
    /**
     * 处理选择变化
     */
    public void processSelectionChanged(World world, SelectionChangedEvent event) {
        // 发布选择变化事件
        eventHandler.publishSelectionChanged(event);
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理选择变化: +{} -{} 总计:{}", 
                event.getAddedBlocks().size(), 
                event.getRemovedBlocks().size(), 
                event.getTotalBlockCount());
        }
    }
    
    /**
     * 处理工具切换
     */
    public void processToolSwitch(String fromToolId, String toToolId, World world) {
        // 先停用旧工具
        if (fromToolId != null) {
            processToolDeactivation(fromToolId, world);
        }
        
        // 再激活新工具
        if (toToolId != null) {
            processToolActivation(toToolId, world);
        }
        
        if (debug) {
            LOGGER.debug("[SelectionToolEventProcessor] 处理工具切换: {} -> {}", fromToolId, toToolId);
        }
    }
    
    /**
     * 处理工具错误
     */
    public void processToolError(String toolId, String errorMessage, Throwable throwable) {
        LOGGER.error("[SelectionToolEventProcessor] 工具错误: {} - {}", toolId, errorMessage, throwable);
        
        // 这里可以创建错误事件并发布
        // 例如：ToolErrorEvent event = new ToolErrorEvent(toolId, errorMessage, throwable);
        // eventHandler.publishToolError(event);
    }
    
    /**
     * 获取事件处理器
     */
    public SelectionEventHandler getEventHandler() {
        return eventHandler;
    }
    
    /**
     * 获取事件总线
     */
    public SelectionEventBus getEventBus() {
        return eventHandler.getEventBus();
    }
    
    /**
     * 获取选择管理器
     */
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}
