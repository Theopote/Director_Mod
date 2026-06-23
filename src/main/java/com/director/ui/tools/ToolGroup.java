package com.director.ui.tools;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

/**
 * 工具分组（用于 ToolbarPanel 分段渲染）
 */
public enum ToolGroup {
    SELECTION(DirectorLang.ToolGroupKeys.SELECTION),
    ACTOR(DirectorLang.ToolGroupKeys.ACTOR),
    WORLD(DirectorLang.ToolGroupKeys.WORLD),
    TIMELINE(DirectorLang.ToolGroupKeys.TIMELINE);

    private final String langKey;

    ToolGroup(String langKey) {
        this.langKey = langKey;
    }

    public String getDisplayName() {
        return DirectorI18n.translate(langKey);
    }
}
