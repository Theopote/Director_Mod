package com.director.ui;

import com.director.i18n.PanelI18n;
import com.director.ui.PanelId;
import com.director.ui.theme.Icons;

/**
 * ImGui 窗口标题与稳定 ID（{@code 可见标题###WindowId}）的统一构建。
 */
public final class PanelWindows {

    public static final String TOOLBAR_ID = "###ToolbarPanel";
    public static final String TIMELINE_ID = "###TimelinePanel";
    public static final String PROPERTIES_ID = "###PropertiesPanel";
    public static final String CONTAINER_ID = "###ContainerPanel";
    public static final String ACTORS_ID = "###ActorsPanel";
    public static final String SELECTION_FILTER_ID = "###SelectionFilterPanel";
    public static final String SELECTION_HISTORY_ID = "###SelectionHistoryPanel";
    public static final String RENDER_SETTINGS_ID = "###RenderSettingsPanel";
    public static final String TOOL_PROPERTIES_ID = "###ToolPropertiesPanel";

    private PanelWindows() {
    }

    public static String toolbar() {
        return PanelI18n.title(PanelId.TOOLBAR) + TOOLBAR_ID;
    }

    public static String timeline() {
        return PanelI18n.title(PanelId.TIMELINE) + TIMELINE_ID;
    }

    public static String properties() {
        return Icons.CLIPBOARD + " " + PanelI18n.title(PanelId.PROPERTIES) + PROPERTIES_ID;
    }

    public static String container() {
        return PanelI18n.title(PanelId.CONTAINER) + CONTAINER_ID;
    }

    public static String actors() {
        return Icons.TARGET + " " + PanelI18n.title(PanelId.ACTORS) + ACTORS_ID;
    }

    public static String selectionFilter() {
        return Icons.FILTER + " " + PanelI18n.title(PanelId.SELECTION_FILTER) + SELECTION_FILTER_ID;
    }

    public static String selectionHistory() {
        return Icons.UNDO + " " + PanelI18n.title(PanelId.SELECTION_HISTORY) + SELECTION_HISTORY_ID;
    }

    public static String renderSettings() {
        return Icons.COLOR + " " + PanelI18n.title(PanelId.RENDER_SETTINGS) + RENDER_SETTINGS_ID;
    }

    public static String toolProperties() {
        return Icons.TOOLS + " " + PanelI18n.title(PanelId.TOOL_PROPERTIES) + TOOL_PROPERTIES_ID;
    }
}
