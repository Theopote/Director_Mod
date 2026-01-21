package com.chronoblocks.ui.commands;

/**
 * 帮助命令接口 - 辅助功能
 * 
 * <p>职责：管理帮助文档、教程、反馈等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface HelpCommands {
    
    /**
     * 显示快捷键帮助
     */
    void showShortcutHelp();
    
    /**
     * 打开用户手册
     */
    void openUserManual();
    
    /**
     * 打开视频教程
     */
    void openVideoTutorial();
    
    /**
     * 报告 Bug
     */
    void reportBug();
    
    /**
     * 显示关于对话框
     */
    void showAboutDialog();
}
