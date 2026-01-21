package com.chronoblocks.ui.commands;

import com.chronoblocks.ui.theme.UITheme;

/**
 * 布局命令接口 - 布局与主题管理
 * 
 * <p>职责：管理布局保存/恢复、UI缩放、主题切换等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface LayoutCommands {
    
    /**
     * 保存当前布局
     */
    void saveCurrentLayout();
    
    /**
     * 恢复默认布局
     */
    void restoreDefaultLayout();
    
    /**
     * 设置 UI 缩放
     * @param scale 缩放比例
     */
    void setUIScale(float scale);
    
    /**
     * 设置主题
     * @param theme 主题类型
     */
    void setTheme(String theme);
}
