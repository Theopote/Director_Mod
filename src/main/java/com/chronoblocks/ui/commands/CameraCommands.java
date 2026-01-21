package com.chronoblocks.ui.commands;

import com.chronoblocks.ui.timeline.model.camera.path.CameraPathType;

/**
 * 摄像机命令接口 - 导演级语义
 * 
 * <p>职责：管理摄像机路径创建、对齐、视角控制等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface CameraCommands {
    
    /**
     * 创建摄像机路径
     * @param type 路径类型（Orbit / Dolly / Crane / Free）
     */
    void createCameraPath(CameraPathType type);
    
    /**
     * 对齐摄像机到当前视角
     */
    void alignCameraToCurrentView();
    
    /**
     * 从当前视角创建镜头
     */
    void createShotFromCurrentView();
    
    /**
     * 设置默认摄像机视角（可选）
     */
    void setDefaultCameraView();
}
