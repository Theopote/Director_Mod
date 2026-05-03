package com.director.ui.commands;

import com.director.ui.timeline.model.camera.path.CameraPathType;

/**
 * 摄像机命令接口 - 导演级语义
 * 
 * <p>职责：管理摄像机路径创建、对齐、视角控制等
 *
 * <p>当前状态（2026-05）：
 * 在默认实现中，以下命令仍处于未实现阶段，调用时会抛出
 * {@link UnsupportedOperationException}，避免静默成功。
 * 调用方在接入前应具备异常处理或做能力检测。
 * 
 * @author Director Team
 * @version 1.0
 */
public interface CameraCommands {
    
    /**
     * 创建摄像机路径
     * @param type 路径类型（Orbit / Dolly / Crane / Free）
    * @throws UnsupportedOperationException 当实现尚未提供该能力时
     */
    void createCameraPath(CameraPathType type);
    
    /**
     * 对齐摄像机到当前视角
        * @throws UnsupportedOperationException 当实现尚未提供该能力时
     */
    void alignCameraToCurrentView();
    
    /**
     * 从当前视角创建镜头
        * @throws UnsupportedOperationException 当实现尚未提供该能力时
     */
    void createShotFromCurrentView();
    
    /**
     * 设置默认摄像机视角（可选）
     * @throws UnsupportedOperationException 当实现尚未提供该能力时
     */
    void setDefaultCameraView();
}
