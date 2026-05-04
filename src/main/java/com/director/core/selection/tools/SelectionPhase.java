package com.director.core.selection.tools;

/**
 * 📐 框选状态机
 * <p>
 * 用于 BoxSelectTool 的状态管理：
 * - IDLE：空闲状态
 * - FIRST_POINT_SET：已确定第一角点，等待第二角点（左键再次点击）
 * - DRAGGING：保留状态（旧逻辑使用）
 * - GIZMO_EDIT：正在用 Gizmo 精调盒子（Ctrl + 角点激活）
 * 
 * @author Director
 * @version 1.0
 */
public enum SelectionPhase {
    
    /** 空闲状态 */
    IDLE,
    
    /** 已设置第一个角点，等待第二个角点（左键再次点击） */
    FIRST_POINT_SET,
    
    /** 保留状态：兼容历史拖拽流程 */
    DRAGGING,
    
    /** 正在使用 Gizmo 编辑选区 */
    GIZMO_EDIT
}
