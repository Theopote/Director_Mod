package com.chronoblocks.ui.commands;

/**
 * 工具命令接口 - 工具切换与管理
 * 
 * <p>职责：管理工具的激活、切换等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface ToolCommands {
    
    /**
     * 激活指定工具
     * @param toolId 工具标识符
     */
    void activateTool(String toolId);
    
    // ==================== 状态查询方法（只读接口） ====================
    
    /**
     * 获取当前激活的工具 ID（query only）
     * @return 当前激活的工具 ID，如果没有则返回 null
     */
    String getActiveToolId();
    
    /**
     * 检查指定工具是否激活（query only）
     * @param toolId 工具标识符
     * @return true 如果工具已激活，false 如果未激活
     */
    boolean isToolActive(String toolId);
}
