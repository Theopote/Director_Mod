package com.chronoblocks.ui.commands;

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
     * 保存当前布局（自动保存到当前世界）
     */
    void saveCurrentLayout();
    
    /**
     * 显示保存布局对话框
     */
    void showSaveLayoutDialog();
    
    /**
     * 显示载入布局对话框
     */
    void showLoadLayoutDialog();
    
    /**
     * 保存当前布局为预设
     * @param presetName 预设名称
     * @return 是否保存成功
     */
    boolean saveLayoutPreset(String presetName);
    
    /**
     * 列出所有可用的布局预设
     * @return 预设名称列表
     */
    java.util.List<String> listLayoutPresets();
    
    /**
     * 加载布局预设
     * @param presetName 预设名称
     * @return 是否加载成功
     */
    boolean loadLayoutPreset(String presetName);
    
    /**
     * 删除布局预设
     * @param presetName 预设名称
     * @return 是否删除成功
     */
    boolean deleteLayoutPreset(String presetName);
    
    /**
     * 恢复默认布局
     */
    void restoreDefaultLayout();
    
    /**
     * 重新载入当前世界的布局
     */
    void reloadLayout();
    
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
