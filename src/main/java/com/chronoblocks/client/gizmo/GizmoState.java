package com.chronoblocks.client.gizmo;

/**
 * 🧭 GizmoState - Gizmo 交互状态
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum GizmoState {
    /**
     * 空闲状态
     */
    IDLE,
    
    /**
     * 悬停状态（鼠标在 Handle 上）
     */
    HOVER,
    
    /**
     * 拖拽状态（正在拖动 Handle）
     */
    DRAGGING
}

