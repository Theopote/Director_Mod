package com.director.ui.panels;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具属性面板（占位）
 * <p>
 * 工具属性已集成到「属性」面板，由 PropertyContext 仲裁在 TOOL 时显示
 * SelectionToolPropertyAdapter。本类仅作过渡兼容：旧布局或 Window → Toggle Panels
 * 若仍引用 ToolProperties，则显示下方提示，引导用户使用属性面板。
 * </p>
 *
 * @author Director Team
 */
public class ToolPropertiesPanel extends AbstractPanel {

    public static final String PANEL_NAME = PanelId.TOOL_PROPERTIES;

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPropertiesPanel.class);

    public ToolPropertiesPanel() {
        LOGGER.debug("[ToolPropertiesPanel] 占位实例（工具属性已集成到属性面板）");
    }

    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }

    @Override
    public String getWindowName() {
        return PanelWindows.toolProperties();
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    protected void doRender() {
        UITheme.label(Icons.INFO + " " + t(DirectorLang.ToolPropertiesPanel.EMBEDDED_TITLE), UITheme.DarkTheme.TEXT);
        ImGui.spacing();
        ImGui.textWrapped(t(DirectorLang.ToolPropertiesPanel.EMBEDDED_HINT));
    }

    private static String t(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }
}
