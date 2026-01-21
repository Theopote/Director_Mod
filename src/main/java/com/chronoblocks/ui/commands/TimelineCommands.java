package com.chronoblocks.ui.commands;

import com.chronoblocks.ui.timeline.model.camera.path.CameraPathType;

/**
 * 时间线命令接口 - 时间线结构与视图管理
 * 
 * <p>职责：管理时间线设置、视图操作、轨道管理等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface TimelineCommands {
    
    /**
     * 打开时间线设置对话框
     */
    void openTimelineSettings();
    
    /**
     * 缩放到全部内容
     */
    void zoomToFit();
    
    /**
     * 滚动到播放头
     */
    void scrollToPlayhead();
    
    /**
     * 添加摄像机轨道
     * @param type 摄像机路径类型（Orbit / Dolly / Crane / Free）
     */
    void addCameraTrack(CameraPathType type);
    
    /**
     * 添加演员轨道（为未来预留）
     */
    void addActorTrack();
    
    /**
     * 添加天气轨道（为未来预留）
     */
    void addWeatherTrack();
    
    /**
     * 添加音频轨道（为未来预留）
     */
    void addAudioTrack();
}
