package com.director.timeline.model.camera.path;

/**
 * CameraPathType - 摄像机路径类型
 * 
 * <p>用于标识不同的路径生成策略，用于 UI 显示和序列化。
 * 
 * @author Director Team
 * @version 1.0
 */
public enum CameraPathType {
    /** 自定义路径（用户在片段内设置摄像机关键帧） */
    CUSTOM,
    
    /** 围绕路径（Orbit - 围绕中心点旋转） */
    ORBIT,
    
    /** 推拉路径（Dolly - 沿直线移动，未来） */
    DOLLY,
    
    /** 升降路径（Crane - 垂直移动，未来） */
    CRANE,
    
    /** 沿轨道路径（Rail - 沿预设路径移动，未来） */
    RAIL
}
