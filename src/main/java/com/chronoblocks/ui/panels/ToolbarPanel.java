package com.chronoblocks.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;

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
 * - SelectionRenderSettingsPanel - 选区渲染设置
 * - SelectionFilterPanel - 选择过滤器
 * - SelectionHistoryPanel - 选择历史
 * - HelpPanel - 帮助面板
 */
public class ToolbarPanel extends AbstractPanel implements IPanelRegistryAware {

    private final SelectionManager selectionManager;
    private IPanelRegistry panelRegistry;

    /**
     * 构造函数 - 初始化选择管理器
     */
    public ToolbarPanel() {
        this.selectionManager = SelectionManager.get();
        if (this.selectionManager == null) {
            logger.error("[ToolbarPanel] 严重: SelectionManager.get() 返回 null，工具栏将无法工作！");
        }
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
        // 关键依赖检查
        if (selectionManager == null) {
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "严重错误: SelectionManager 未初始化!");
            return; // 提前退出，防止NPE
        }

        // 确保选择管理器状态是最新的
        if (!selectionManager.hasCurrentTool()) {
            logger.debug("[ToolbarPanel] 没有当前工具，尝试设置默认工具");
            try {
                var world = net.minecraft.client.MinecraftClient.getInstance().world;
                if (world != null) {
                    // 尝试设置默认工具
                    selectionManager.setCurrentTool("click", world);
                }
            } catch (Exception e) {
                logger.warn("[ToolbarPanel] 设置默认工具失败", e);
            }
        }

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
        if (selectionManager == null) return;

        // 选择工具图标（不调用sameLine，由外部管理换行）
        UITheme.label(Icons.TOOLS, UITheme.DarkTheme.TEXT_SELECTED);

        // 获取实时的当前工具ID
        String currentToolId = selectionManager.getCurrentToolId();

        // 渲染所有注册的选择工具
        for (SelectionTool tool : selectionManager.getRegistry().getAllTools()) {
            // 在循环内管理换行
            ImGui.sameLine();

            boolean active = tool.getId().equals(currentToolId);

            if (active) {
                // 使用主题高亮色
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }

            // 使用更唯一的按钮ID，避免冲突
            String icon = getToolIcon(tool.getId());
            String buttonLabel = icon + " " + tool.getDisplayName();
            String buttonId = "ToolbarPanel_tool_" + tool.getId() + "_" + System.identityHashCode(tool);

            // 使用更明确的按钮样式
            if (ImGui.button(buttonLabel + "##" + buttonId, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                logger.info("[ToolbarPanel] 按钮被点击: {} (ID: {})", tool.getDisplayName(), tool.getId());

                // 立即更新UI状态，避免延迟
                try {
                    setCurrentSelectionTool(tool);
                } catch (Exception e) {
                    logger.error("[ToolbarPanel] 设置选择工具失败", e);
                }
            }

            if (active) {
                ImGui.popStyleColor();
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tool.getDisplayName() + "\n" + tool.getDescription());
            }
        }
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

    private void setCurrentSelectionTool(SelectionTool tool) {
        if (selectionManager != null && tool != null) {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                try {
                    // 确保工具切换是原子操作
                    String previousToolId = selectionManager.getCurrentToolId();
                    selectionManager.setCurrentTool(tool.getId(), world);

                    // 验证切换是否成功
                    String newToolId = selectionManager.getCurrentToolId();
                    if (newToolId.equals(tool.getId())) {
                        logger.info("[ChronoBlocks] Selection tool switched from {} to {}", previousToolId, tool.getDisplayName());
                    } else {
                        logger.warn("[ChronoBlocks] Tool switch failed: expected {}, got {}", tool.getId(), newToolId);
                    }
                } catch (Exception e) {
                    logger.error("[ChronoBlocks] Failed to switch tool to {}", tool.getDisplayName(), e);
                }
            } else {
                logger.warn("[ChronoBlocks] World is null, cannot switch tool");
            }
        } else {
            logger.warn("[ChronoBlocks] SelectionManager or tool is null");
        }
    }

    /**
     * 根据工具ID获取对应的自定义图标
     */
    private String getToolIcon(String toolId) {
        return switch (toolId) {
            // 基础工具
            case "click" -> Icons.SELECT;      // 点击选择工具
            case "box" -> Icons.BOX;           // 框选工具
            case "wand" -> Icons.MAGIC;        // 魔棒工具

            // 高级工具
            case "face" -> Icons.COLOR;        // 面选择工具
            case "geometry" -> Icons.GRID;     // 几何选择工具（球体/圆柱体/金字塔）
            case "sphere" -> Icons.SPHERE;     // 球体选择工具
            case "cylinder" -> Icons.CYLINDER; // 圆柱体选择工具
            case "pyramid" -> Icons.DIAMOND;   // 金字塔选择工具
            case "brush" -> Icons.BRUSH;       // 笔刷选择工具
            case "lasso" -> Icons.LASSO;       // 套索选择工具

            // 多模式选择工具
            case "box_v3" -> Icons.BOX;        // 框选工具v3

            default -> Icons.TOOLS;            // 默认工具图标
        };
    }

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