package com.director.client.gizmo;

/**
 * 🧭 GizmoType - Gizmo 类型枚举
 * 
 * 定义不同的 Gizmo 操作模式
 * 
 * @author Director Team
 * @version 1.0
 */
public enum GizmoType {
    /**
     * 变换 Gizmo（平移/旋转/缩放）
     */
    TRANSFORM,
    
    /**
     * 边界 Gizmo（调整选区边界）
     */
    BOUNDS,
    
    /**
     * 轴心点 Gizmo（调整旋转中心）
     */
    PIVOT,
    
    /**
     * 动画 Gizmo（关键帧路径编辑）
     */
    ANIMATION
}

