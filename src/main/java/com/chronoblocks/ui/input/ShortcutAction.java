package com.chronoblocks.ui.input;

/**
 * 快捷键动作枚举 - 定义所有可通过快捷键触发的用户意图
 * 
 * <p>职责：
 * <ul>
 *   <li>提供类型安全的快捷键动作标识</li>
 *   <li>将快捷键与具体实现解耦</li>
 *   <li>便于扩展和维护</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只关心"用户意图"，不关心"如何实现"</li>
 *   <li>每个动作代表一个明确的用户操作</li>
 *   <li>支持未来扩展（宏、自动化等）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum ShortcutAction {
    
    // ==================== 播放控制 ====================
    
    /** 播放/暂停切换 */
    PLAY_PAUSE,
    
    /** 停止播放 */
    STOP,
    
    /** 跳转到开始 */
    GO_TO_START,
    
    /** 跳转到结束 */
    GO_TO_END,
    
    /** 切换循环播放 */
    TOGGLE_LOOP,
    
    // ==================== 工具切换 ====================
    
    /** 点击选择工具 */
    TOOL_SELECT,
    
    /** 框选工具 */
    TOOL_BOX_SELECT,
    
    /** 魔棒工具 */
    TOOL_WAND,
    
    /** 笔刷工具 */
    TOOL_BRUSH,
    
    /** 面选择工具 */
    TOOL_FACE,
    
    /** 移动工具 */
    TOOL_MOVE,
    
    /** 旋转工具 */
    TOOL_ROTATE,
    
    /** 缩放工具 */
    TOOL_SCALE,
    
    /** 下一个工具 */
    TOOL_NEXT,
    
    /** 上一个工具 */
    TOOL_PREVIOUS,
    
    // ==================== 选择操作模式 ====================
    
    /** 加选模式 */
    SELECTION_MODE_ADD,
    
    /** 减选模式 */
    SELECTION_MODE_SUBTRACT,
    
    /** 交集模式 */
    SELECTION_MODE_INTERSECT,
    
    /** 清除选择 */
    CLEAR_SELECTION,
    
    // ==================== 面板切换 ====================
    
    /** 切换工具属性面板 */
    TOGGLE_TOOL_PROPERTIES,
    
    /** 切换选择过滤器面板 */
    TOGGLE_SELECTION_FILTER,
    
    /** 切换选择历史面板 */
    TOGGLE_SELECTION_HISTORY,
    
    // ==================== 时间线操作 ====================
    
    /** 撤销操作 */
    TIMELINE_UNDO,
    
    /** 重做操作 */
    TIMELINE_REDO,
    
    /** 删除选中项 */
    TIMELINE_DELETE,
    
    // ==================== Actor Gizmo 操作 ====================
    
    /** Gizmo 移动模式 */
    GIZMO_MOVE,
    
    /** Gizmo 旋转模式 */
    GIZMO_ROTATE,
    
    /** Gizmo 缩放模式 */
    GIZMO_SCALE
}