package com.chronoblocks.ui.commands;

/**
 * 视图命令接口 - 面板与显示控制
 * 
 * <p>职责：管理面板的显示、隐藏、切换等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface ViewCommands {
    
    /**
     * 显示面板
     * @param panelId 面板标识符
     */
    void showPanel(String panelId);
    
    /**
     * 隐藏面板
     * @param panelId 面板标识符
     */
    void hidePanel(String panelId);
    
    /**
     * 切换面板可见性
     * @param panelId 面板标识符
     */
    void togglePanel(String panelId);
    
    /**
     * 显示所有面板
     */
    void showAllPanels();
    
    /**
     * 隐藏所有面板
     */
    void hideAllPanels();
}
