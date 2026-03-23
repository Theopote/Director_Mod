package com.director.ui.tools;

/**
 * 工具分组（用于 ToolbarPanel 分段渲染）
 */
public enum ToolGroup {
    SELECTION("选择"),
    WORLD("世界"),
    TIMELINE("时间线");

    private final String displayName;

    ToolGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}


