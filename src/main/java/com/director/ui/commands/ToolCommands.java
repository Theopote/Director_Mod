package com.director.ui.commands;

import com.director.core.selection.SelectionOperation;

/**
 * 工具命令接口 - 工具切换与管理
 * 
 * <p>职责：管理工具的激活、切换等
 * 
 * @author Director Team
 * @version 2.0 - 添加选择操作相关方法
 */
public interface ToolCommands {
    
    /**
     * 激活指定工具
     * @param toolId 工具标识符
     */
    void activateTool(String toolId);
    
    /**
     * 切换到下一个工具（循环）
     */
    void switchToNextTool();
    
    /**
     * 切换到上一个工具（循环）
     */
    void switchToPreviousTool();
    
    /**
     * 设置选择操作模式（加选/减选/交集）
     * @param operation 操作模式
     */
    void setSelectionMode(SelectionOperation operation);
    
    /**
     * 清除当前选择
     */
    void clearSelection();
    
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
