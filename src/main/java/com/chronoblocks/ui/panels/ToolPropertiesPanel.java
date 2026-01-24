package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
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
 * @author ChronoBlocks Team
 */
public class ToolPropertiesPanel extends AbstractPanel {

    public static final String PANEL_NAME = "ToolProperties";

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
        return Icons.TOOLS + " 工具属性（已集成）###ToolPropertiesPanel";
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    protected void doRender() {
        UITheme.label(Icons.INFO + " 工具属性已集成到属性面板", UITheme.DarkTheme.TEXT);
        ImGui.spacing();
        ImGui.textWrapped("请使用右侧「属性」面板查看和编辑工具参数。选中工具时，属性面板会自动显示该工具的配置。");
    }
}
