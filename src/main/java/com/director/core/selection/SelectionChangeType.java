package com.director.core.selection;

/**
 * 📢 选区变化类型枚举
 * <p>
 * 用于 SelectionEvents.fireChanged() 的事件标识。
 * 区分不同的选区变化来源和类型。
 * 
 * @author Director
 * @version 1.0
 */
public enum SelectionChangeType {
    
    /** 通用更新（添加、删除、交集操作） */
    SELECTION_UPDATED,
    
    /** 清空选区 */
    SELECTION_CLEARED,
    
    /** 创建新选区（如框选完成） */
    SELECTION_CREATED,
    
    /** 实时预览变化（如拖拽中） */
    PREVIEW_UPDATED
}
