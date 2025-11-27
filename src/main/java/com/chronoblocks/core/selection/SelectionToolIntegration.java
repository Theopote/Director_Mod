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
     * 检查是否启用
     */
    public boolean isEnabled() {
        return isEnabled;
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

}
