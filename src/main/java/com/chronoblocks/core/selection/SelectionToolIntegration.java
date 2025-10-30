package com.chronoblocks.core.selection;

// import net.minecraft.client.MinecraftClient; // 暂时不使用
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔗 SelectionToolIntegration - 选择工具系统集成
 * 
 * 负责将选择工具系统集成到ChronoBlocks的客户端事件处理中
 * 
 * @author ChronoBlocks Team
 * @version 2.0 (更新为使用 SelectionInputHandler)
 */
public class SelectionToolIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToolIntegration.class);
    
    private static SelectionToolIntegration INSTANCE = null;
    
    private final SelectionInputHandler inputHandler;
    private final SelectionManager selectionManager;
    private boolean isEnabled = false;
    
    private SelectionToolIntegration() {
        this.inputHandler = new SelectionInputHandler();
        this.selectionManager = SelectionManager.get();
        LOGGER.info("[SelectionToolIntegration] 选择工具集成已初始化");
    }
    
    public static SelectionToolIntegration get() {
        if (INSTANCE == null) {
            INSTANCE = new SelectionToolIntegration();
        }
        return INSTANCE;
    }
    
    /**
     * 启用选择工具集成
     */
    public void enable() {
        if (!isEnabled) {
            isEnabled = true;
            LOGGER.info("[SelectionToolIntegration] 选择工具集成已启用");
        }
    }
    
    /**
     * 禁用选择工具集成
     */
    public void disable() {
        if (isEnabled) {
            isEnabled = false;
            LOGGER.info("[SelectionToolIntegration] 选择工具集成已禁用");
        }
    }
    
    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * 处理鼠标点击事件
     */
    public void handleMouseClick(boolean isLeftClick, boolean isShiftDown) {
        if (!isEnabled) return;
        
        try {
            inputHandler.handleMouseClick(isLeftClick, isShiftDown);
        } catch (Exception e) {
            LOGGER.warn("[SelectionToolIntegration] 处理鼠标点击事件失败", e);
        }
    }
    
    /**
     * 处理鼠标拖拽开始
     */
    public void handleMouseDragStart(boolean isLeftClick) {
        if (!isEnabled) return;
        
        try {
            inputHandler.handleMouseDragStart(isLeftClick);
        } catch (Exception e) {
            LOGGER.warn("[SelectionToolIntegration] 处理鼠标拖拽开始事件失败", e);
        }
    }
    
    /**
     * 处理鼠标拖拽更新
     */
    public void handleMouseDragUpdate() {
        if (!isEnabled) return;
        
        try {
            inputHandler.handleMouseDragUpdate();
        } catch (Exception e) {
            LOGGER.warn("[SelectionToolIntegration] 处理鼠标拖拽更新事件失败", e);
        }
    }
    
    /**
     * 处理鼠标拖拽结束
     */
    public void handleMouseDragEnd(boolean isLeftClick) {
        if (!isEnabled) return;
        
        try {
            inputHandler.handleMouseDragEnd(isLeftClick);
        } catch (Exception e) {
            LOGGER.warn("[SelectionToolIntegration] 处理鼠标拖拽结束事件失败", e);
        }
    }
    
    /**
     * 每帧更新
     */
    public void tick() {
        if (!isEnabled) return;
        
        try {
            inputHandler.tick();
        } catch (Exception e) {
            LOGGER.warn("[SelectionToolIntegration] 更新选择工具系统失败", e);
        }
    }
    
    /**
     * 获取选择管理器
     */
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
    
    /**
     * 获取输入处理器
     */
    public SelectionInputHandler getInputHandler() {
        return inputHandler;
    }
}
