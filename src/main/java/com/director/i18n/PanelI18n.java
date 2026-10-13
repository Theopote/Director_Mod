package com.director.i18n;

import com.director.ui.PanelId;

/**
 * 面板显示标题的 lang 键解析（{@code gui.director.panel.*}）。
 * <p>
 * {@link PanelId} 常量用于内部标识；用户可见标题通过本类获取。
 */
public final class PanelI18n {

    private PanelI18n() {
    }

    public static String title(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return "";
        }
        String key = keyFor(panelId);
        return key != null ? DirectorI18n.translate(key) : panelId;
    }

    public static String keyFor(String panelId) {
        return switch (panelId) {
            case PanelId.TIMELINE -> DirectorLang.Panel.TIMELINE;
            case PanelId.TOOLBAR -> DirectorLang.Panel.TOOLBAR;
            case PanelId.MENU_BAR -> DirectorLang.Panel.MENU_BAR;
            case PanelId.STATUS_BAR -> DirectorLang.Panel.STATUS_BAR;
            case PanelId.PROPERTIES -> DirectorLang.Panel.PROPERTIES;
            case PanelId.TOOL_PROPERTIES -> DirectorLang.Panel.TOOL_PROPERTIES;
            case PanelId.CONTAINER -> DirectorLang.Panel.CONTAINER;
            case PanelId.ACTORS -> DirectorLang.Panel.ACTORS;
            case PanelId.LIGHTS -> "gui.director.panel.lights";
            case PanelId.SELECTION_FILTER -> DirectorLang.Panel.SELECTION_FILTER;
            case PanelId.SELECTION_HISTORY -> DirectorLang.Panel.SELECTION_HISTORY;
            case PanelId.RENDER_SETTINGS -> DirectorLang.Panel.RENDER_SETTINGS;
            default -> null;
        };
    }
}
