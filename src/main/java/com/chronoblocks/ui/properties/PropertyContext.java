package com.chronoblocks.ui.properties;

/**
 * 属性上下文枚举 - 属性面板仲裁层
 * <p>
 * 决定 PropertiesPanel 当前应展示哪一类属性的语义。
 * 由 PropertyContextService 根据 UI 状态计算得出。
 * </p>
 *
 * <ul>
 *   <li><b>TOOL</b> - 当前选择工具的参数（画笔半径、魔棒阈值等）</li>
 *   <li><b>TIMELINE</b> - 时间线对象（关键帧、片段、轨道、摄像机等）</li>
 *   <li><b>WORLD</b> - 世界/场景对象（预留）</li>
 *   <li><b>NONE</b> - 无明确上下文，显示「未选择」或项目级占位</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 */
public enum PropertyContext {
    /** 当前激活工具 */
    TOOL,

    /** 时间线相关选择（Track / Clip / Keyframe 等） */
    TIMELINE,

    /** 世界/场景对象（预留） */
    WORLD,

    /** 无明确上下文 */
    NONE
}
