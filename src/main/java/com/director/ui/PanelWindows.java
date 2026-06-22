package com.director.ui;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

/**
 * ImGui 窗口标题与稳定 ID（{@code 可见标题###WindowId}）的统一构建。
 * <p>
 * Dock 布局与各面板 {@code getWindowName()} 须使用此处方法，避免翻译标题与停靠 ID 不一致。
 */
public final class PanelWindows {

    public static final String TOOLBAR_ID = "###ToolbarPanel";
    public static final String TIMELINE_ID = "###TimelinePanel";

    private PanelWindows() {
    }

    public static String toolbar() {
        return DirectorI18n.translate(DirectorLang.Toolbar.PANEL_TITLE) + TOOLBAR_ID;
    }

    public static String timeline() {
        return DirectorI18n.translate(DirectorLang.Timeline.PANEL_TITLE) + TIMELINE_ID;
    }
}
