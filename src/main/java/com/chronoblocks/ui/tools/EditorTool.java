package com.chronoblocks.ui.tools;

/**
 * 编辑器工具（UI 一等公民抽象）。
 *
 * <p>当前阶段仅用于把 ToolbarPanel 从具体 Manager 中解耦出来：
 * ToolbarPanel 只依赖 EditorTool + ToolRegistry，而不直接触达 SelectionManager。</p>
 */
public interface EditorTool {

    /** 工具唯一 ID（例如 "click" / "box" / "wand"） */
    String getId();

    /** 工具显示名（用于按钮与 Tooltip 标题） */
    String getDisplayName();

    /** 工具图标（当前项目使用 Icons 的私有区字体图标或 Emoji） */
    String getIcon();

    /** 工具描述（用于 Tooltip 说明） */
    String getDescription();

    /** 工具分组（用于 Toolbar 分段） */
    ToolGroup getGroup();

    /** 当前是否可用（用于 Disabled 视觉与点击拦截） */
    boolean isEnabled();

    /** 当前是否激活（用于高亮） */
    boolean isActive();
}


