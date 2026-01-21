package com.chronoblocks.ui.commands;

/**
 * 项目命令接口 - 项目生命周期管理
 * 
 * <p>职责：管理项目的创建、打开、保存、导出等生命周期操作
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface ProjectCommands {
    
    /**
     * 新建项目
     */
    void newProject();
    
    /**
     * 打开项目
     */
    void openProject();
    
    /**
     * 保存项目
     */
    void saveProject();
    
    /**
     * 另存为
     */
    void saveProjectAs();
    
    /**
     * 导出视频
     */
    void exportVideo();
    
    /**
     * 退出应用
     */
    void exitApplication();
}
