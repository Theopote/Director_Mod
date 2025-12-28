package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.EditorTool;
import com.chronoblocks.ui.tools.ToolGroup;
import com.chronoblocks.ui.tools.ToolRegistry;

import java.util.List;

/**
 * ChronoBlocks Toolbar v6.0 - 模块化重构版
 * <p>
 * 专注于核心工具栏功能：
 * - 选择工具（点击 / 框选 / 魔棒）
 * - 状态显示（工具模式 / 选择数量 / FPS）
 * - 渲染控制（容器显示开关）
 * - 面板调度（通过PanelRegistry管理子面板）
 * - 窗口控制（关闭）
 * <p>
 * 其他功能已模块化为独立面板：
 * - ToolPropertiesPanel - 工具属性
 * - RenderSettingsPanel - 渲染设置
 * - SelectionFilterPanel - 选择过滤器
 * - SelectionHistoryPanel - 选择历史
 * - HelpPanel - 帮助面板
 */
public class ToolbarPanel extends AbstractPanel implements IPanelRegistryAware {

    private final ToolRegistry toolRegistry;
    private IPanelRegistry panelRegistry;

    /**
     * 工具按钮是否显示文字（默认：图标模式，更紧凑）
     *
     * <p>说明：这里先用静态字段做会话级别开关；如果你希望跨存档/跨会话持久化，
     * 后续可以把它接入 UI 配置系统或布局持久化。</p>
     */
    private static boolean SHOW_TOOL_TEXT = false;

    /**
     * 构造函数 - 初始化选择管理器
     */
    public ToolbarPanel() {
        this.toolRegistry = ToolRegistry.get();
    }

    @Override
    public String getPanelName() {
        return "Toolbar";
    }

    @Override
    public String getWindowName() {
        return "Toolbar###ToolbarPanel";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    protected int getWindowFlags() {
        return imgui.flag.ImGuiWindowFlags.NoCollapse;
    }


    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }

    @Override
    protected void doRender() {
        // 统一入口：确保 ToolRegistry 已初始化且存在默认工具（避免 UI “无工具”状态漂移）
        toolRegistry.initializeIfNeeded();
        toolRegistry.ensureDefaultTool("click");

        // 使用统一的元素间距（6px）和按钮内边距（6px, 3px）
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);

        // 获取可用宽度，用于自动换行判断
        float availWidth = ImGui.getContentRegionAvailX();
        float spacing = UITheme.Spacing.ELEMENT_SPACING;

        // 定义一个用于检查是否需要换行的lambda函数
        java.util.function.Consumer<Float> checkWrap = (Float nextWidth) -> {
            float xNext = ImGui.getCursorPosX() + nextWidth + spacing;
            if (xNext > availWidth) {
                ImGui.newLine();
            } else {
                ImGui.sameLine();
            }
        };

        // 工具按钮组
        renderSelectionToolButtons();
        checkWrap.accept(180f);
        UITheme.separator();

        // 面板切换按钮组
        checkWrap.accept(60f);
        renderPanelToggleButton(Icons.SETTINGS, "属性", ToolPropertiesPanel.class, "打开工具属性面板", ToolPropertiesPanel.PANEL_NAME);
        checkWrap.accept(60f);
        renderPanelToggleButton(Icons.HELP, "帮助", HelpPanel.class, "打开帮助面板", HelpPanel.PANEL_NAME);
        checkWrap.accept(60f);
        renderPanelToggleButton(Icons.FILTER, "过滤", SelectionFilterPanel.class, "打开选择过滤器面板", SelectionFilterPanel.PANEL_NAME);
        checkWrap.accept(60f);
        renderPanelToggleButton(Icons.UNDO, "历史", SelectionHistoryPanel.class, "打开选择历史面板", SelectionHistoryPanel.PANEL_NAME);
        checkWrap.accept(60f);
        UITheme.separator();

        // 状态与关闭
        checkWrap.accept(120f);
        renderStatusIndicators();
        checkWrap.accept(100f);
        renderCloseButton();

        // 移除集成的面板渲染，现在使用独立面板
        // 这些面板现在通过Window菜单的Toggle Panels来管理
        // 或者通过工具栏按钮打开独立面板

        // 处理快捷键输入
        handleKeyInput();

        ImGui.popStyleVar(2);
    }

    /* ---------- 选择工具按钮 ---------- */
    private void renderSelectionToolButtons() {
        renderToolGroup(ToolGroup.SELECTION, Icons.TOOLS);

        // 预留：未来工具系统扩展（World/Timeline）
        List<EditorTool> worldTools = toolRegistry.getToolsByGroup(ToolGroup.WORLD);
        if (!worldTools.isEmpty()) {
            ImGui.sameLine();
            UITheme.separator();
            ImGui.sameLine();
            renderToolGroup(ToolGroup.WORLD, Icons.CONTAINER);
        }

        List<EditorTool> timelineTools = toolRegistry.getToolsByGroup(ToolGroup.TIMELINE);
        if (!timelineTools.isEmpty()) {
            ImGui.sameLine();
            UITheme.separator();
            ImGui.sameLine();
            renderToolGroup(ToolGroup.TIMELINE, Icons.TIMELINE);
        }

        // 工具栏显示选项：图标/文字切换（放在工具组末尾，且保持紧凑）
        ImGui.sameLine();
        UITheme.separator();
        ImGui.sameLine();
        renderToolTextToggleButton();
    }

    private void renderToolGroup(ToolGroup group, String groupIcon) {
        // 分组图标（不调用sameLine，由外部管理换行）
        UITheme.label(groupIcon, UITheme.DarkTheme.TEXT_SELECTED);

        for (EditorTool tool : toolRegistry.getToolsByGroup(group)) {
            ImGui.sameLine();

            boolean active = tool != null && tool.isActive();
            boolean enabled = tool != null && tool.isEnabled();

            if (active) {
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }

            if (!enabled) {
                ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.45f);
            }

            String toolId = tool != null ? tool.getId() : "null";
            String icon = tool != null ? tool.getIcon() : groupIcon;
            String buttonLabel = null;
            if (tool != null) {
                buttonLabel = SHOW_TOOL_TEXT
                        ? (icon + " " + tool.getDisplayName())
                        : icon;
            }
            String buttonId = "ToolbarPanel_tool_" + toolId;

            float buttonW = SHOW_TOOL_TEXT ? 0 : UITheme.Spacing.BUTTON_HEIGHT; // 图标模式：方形按钮
            if (ImGui.button(buttonLabel + "##" + buttonId, buttonW, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (enabled) {
                    logger.info("[ToolbarPanel] 工具按钮点击: {} (ID: {})",
                            tool.getDisplayName(),
                            toolId);
                    toolRegistry.activate(tool);
                }
            }

            if (!enabled) {
                ImGui.popStyleVar();
            }

            if (active) {
                ImGui.popStyleColor();
            }

            if (ImGui.isItemHovered()) {
                renderToolTooltip(tool);
            }
        }
    }

    private void renderToolTextToggleButton() {
        String icon = SHOW_TOOL_TEXT ? Icons.TOOLS : Icons.TAG;
        String label = icon + "##toggleToolText";

        // 这里也用方形按钮，保持一致
        if (ImGui.button(label, UITheme.Spacing.BUTTON_HEIGHT, UITheme.Spacing.BUTTON_HEIGHT)) {
            SHOW_TOOL_TEXT = !SHOW_TOOL_TEXT;
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text("工具按钮显示");
            ImGui.separator();
            ImGui.text(SHOW_TOOL_TEXT ? "当前：图标 + 文字" : "当前：仅图标");
            ImGui.text("点击切换显示方式");
            ImGui.endTooltip();
        }
    }

    private void renderToolTooltip(EditorTool tool) {
        if (tool == null) return;
        ImGui.beginTooltip();
        ImGui.text(tool.getDisplayName());
        ImGui.separator();
        String desc = tool.getDescription();
        if (desc != null && !desc.isBlank()) {
            ImGui.textWrapped(desc);
        }

        // 教学提示（来自 ToolRegistry 元数据）
        var meta = toolRegistry.getMetadata(tool.getId());
        if (meta != null && !meta.getTooltipLines().isEmpty()) {
            ImGui.separator();
            for (String line : meta.getTooltipLines()) {
                if (line != null && !line.isBlank()) {
                    ImGui.textWrapped(line);
                }
            }
        }

        // 快捷键：从 KeyBindings 系统读取（若存在）
        String shortcut = com.chronoblocks.ui.input.SelectionToolKeyBindings.getToolSwitchShortcut(tool.getId());
        if (shortcut != null && !shortcut.isBlank()) {
            ImGui.separator();
            ImGui.text("快捷键: " + shortcut);
        }
        ImGui.endTooltip();
    }

    /* ---------- 状态显示 ---------- */
    private void renderStatusIndicators() {
        float fps = ImGui.getIO().getFramerate();

        // 显示FPS，使用不同颜色表示性能状态
        if (fps >= 60) {
            float[] color = UITheme.toFloatArrayPublic(UITheme.DarkTheme.FPS_GOOD);
            ImGui.textColored(color[0], color[1], color[2], color[3], String.format("FPS: %.0f", fps));
        } else if (fps >= 30) {
            float[] color = UITheme.toFloatArrayPublic(UITheme.DarkTheme.FPS_WARN);
            ImGui.textColored(color[0], color[1], color[2], color[3], String.format("FPS: %.0f", fps));
        } else {
            float[] color = UITheme.toFloatArrayPublic(UITheme.DarkTheme.FPS_BAD);
            ImGui.textColored(color[0], color[1], color[2], color[3], String.format("FPS: %.0f", fps));
        }
        // 渲染开关已移除 - 功能在容器面板中实现
    }

    /* ---------- 关闭按钮 ---------- */
    private void renderCloseButton() {
        UITheme.pushDangerButtonColors();

        // 使用自动宽度
        if (ImGui.button("关闭##close", 0, UITheme.Spacing.BUTTON_HEIGHT)) handleCloseWindow();
        if (ImGui.isItemHovered()) ImGui.setTooltip("关闭 ChronoBlocks 窗口");

        ImGui.popStyleColor(3);
    }

    // 工具切换与图标映射已统一交由 ToolRegistry/SelectionTool 自身提供（避免 ToolbarPanel 硬编码）

    private void handleCloseWindow() {
        logger.info("[ChronoBlocks] Closing ChronoBlocks window...");
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof com.chronoblocks.ui.ChronoBlocksScreen screen) {
                screen.close();
                client.setScreen(null);
            }
        } catch (Exception e) {
            logger.error("[ChronoBlocks] Failed to close ChronoBlocks window", e);
        }
    }

    // 移除不再使用的方法，现在使用独立面板

    /**
     * 设置面板注册表引用
     */
    @Override
    public void setPanelRegistry(IPanelRegistry panelRegistry) {
        this.panelRegistry = panelRegistry;
        logger.info("[ToolbarPanel] 面板注册表已设置，面板数量: {}", panelRegistry != null ? panelRegistry.size() : 0);
        if (panelRegistry != null) {
            logger.info("[ToolbarPanel] 可用的面板:");
            for (IPanel panel : panelRegistry.getAllPanels()) {
                logger.info("[ToolbarPanel]   - {} (类型: {})", panel.getPanelName(), panel.getClass().getSimpleName());
            }
        }
    }

    /**
     * 切换面板显示状态
     */
    private void togglePanel(Class<? extends IPanel> panelClass, String... fallbackPanelNames) {
        String targetLabel = panelClass != null ? panelClass.getSimpleName() :
                (fallbackPanelNames != null && fallbackPanelNames.length > 0 ? fallbackPanelNames[0] : "未知面板");
        try {
            logger.info("[ToolbarPanel] 尝试切换面板: {}", targetLabel);

            if (panelRegistry == null) {
                logger.warn("[ToolbarPanel] 面板注册表未设置，无法切换面板: {}", targetLabel);
                return;
            }

            IPanel panel = null;

            if (panelClass != null) {
                panel = panelRegistry.getPanel(panelClass);
                if (panel == null) {
                    for (IPanel candidate : panelRegistry.getAllPanels()) {
                        if (panelClass.isInstance(candidate)) {
                            panel = candidate;
                            break;
                        }
                    }
                }
            }

            if (panel == null && fallbackPanelNames != null) {
                for (String candidateName : fallbackPanelNames) {
                    if (candidateName == null || candidateName.isBlank()) {
                        continue;
                    }
                    panel = panelRegistry.getPanelByName(candidateName);
                    if (panel != null) {
                        targetLabel = candidateName;
                        break;
                    }
                }
            }

            if (panel != null) {
                boolean currentVisible = panel.isVisible();
                boolean newVisible = !currentVisible;
                panel.setVisible(newVisible);
                logger.info("[ToolbarPanel] 切换面板: {} -> {} (从 {} 到 {})",
                        panel.getPanelName(), newVisible ? "显示" : "隐藏", currentVisible, newVisible);
            } else {
                String fallbackDescription = buildFallbackDescription(fallbackPanelNames);
                logger.warn("[ToolbarPanel] 未找到面板: {} (class: {}, fallback: {})",
                        targetLabel,
                        panelClass != null ? panelClass.getName() : "无",
                        fallbackDescription);
                logger.info("[ToolbarPanel] 可用的面板:");
                for (IPanel p : panelRegistry.getAllPanels()) {
                    if (p != null) {
                        logger.info("[ToolbarPanel]   - {} (类型: {})", p.getPanelName(), p.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[ToolbarPanel] 切换面板失败: {}", targetLabel, e);
        }
    }

    /**
     * 构建候选面板名称描述
     */
    private String buildFallbackDescription(String... fallbackPanelNames) {
        if (fallbackPanelNames == null || fallbackPanelNames.length == 0) {
            return "无候选名称";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : fallbackPanelNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("/");
            }
            builder.append(name);
        }
        return !builder.isEmpty() ? builder.toString() : "无候选名称";
    }

    /**
     * 处理快捷键输入
     */
    private void handleKeyInput() {
        try {
            com.chronoblocks.ui.input.SelectionToolKeyBindings.handleKeyInput();
        } catch (Exception e) {
            logger.error("[ToolbarPanel] 处理快捷键输入失败", e);
        }
    }

    /**
     * 渲染一个标准的工具栏按钮，用于切换指定面板的可见性。
     *
     * @param icon      按钮图标 (来自 Icons 类)
     * @param label     按钮的文本标签
     * @param tooltip   鼠标悬停时显示的工具提示文本
     */
    private void renderPanelToggleButton(String icon, String label, Class<? extends IPanel> panelClass,
                                         String tooltip, String... fallbackPanelNames) {
        String uniqueId = panelClass != null ? panelClass.getSimpleName()
                : (fallbackPanelNames != null && fallbackPanelNames.length > 0 ? fallbackPanelNames[0] : label);
        String buttonId = icon + " " + label + "##" + uniqueId + "Toggle";

        if (ImGui.button(buttonId, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            logger.info("[ToolbarPanel] 面板切换按钮被点击: {}", uniqueId);
            togglePanel(panelClass, fallbackPanelNames);
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }
}