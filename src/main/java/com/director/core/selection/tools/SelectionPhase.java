package com.director.core.selection.tools;

/**
 * 📐 框选状态机
 * 
 * 用于 BoxSelectTool 的状态管理：
 * - IDLE：空闲状态
 * - FIRST_POINT_SET：已确定第一角点，等待第二角点（右键）或拖拽结束
 * - DRAGGING：正在拖出一个盒子（左键按下移动）
 * - GIZMO_EDIT：正在用 Gizmo 精调盒子（Ctrl + 角点激活）
 * 
 * @author Director
 * @version 1.0
 */
public enum SelectionPhase {
    
    /** 空闲状态 */
    IDLE,
    
    /** 已设置第一个角点，等待第二个角点（右键）或拖拽结束 */
    FIRST_POINT_SET,
    
    /** 正在拖拽创建选区 */
    DRAGGING,
    
    /** 正在使用 Gizmo 编辑选区 */
    GIZMO_EDIT
}
