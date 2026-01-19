package com.chronoblocks.ui.timeline.model.camera.path;

/**
 * CameraPathType - 摄像机路径类型
 * 
 * <p>用于标识不同的路径生成策略，用于 UI 显示和序列化。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum CameraPathType {
    /** 自由路径（现有关键帧插值路径） */
    FREE,
    
    /** 围绕路径（Orbit - 围绕中心点旋转） */
    ORBIT,
    
    /** 推拉路径（Dolly - 沿直线移动，未来） */
    DOLLY,
    
    /** 升降路径（Crane - 垂直移动，未来） */
    CRANE,
    
    /** 沿轨道路径（Rail - 沿预设路径移动，未来） */
    RAIL
}
