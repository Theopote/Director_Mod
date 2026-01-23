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
import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.PanelId;

import java.util.List;

/**
 * ChronoBlocks Toolbar - Facade-based implementation
 * <p>
 * 职责：将用户点击翻译成 Facade 命令
 * <p>
 * 核心原则：
 * - 不直接改 TimelineModel
 * - 不直接调 Engine
 * - 不维护"播放中 / 当前工具"状态
 * - 只做一件事：把"用户点击"翻译成 Facade 命令
 * 
 * <p><strong>🚫 禁止导入的业务类（架构防护）：</strong>
 * <ul>
 *   <li>{@code TimelineModel} - 必须通过 Facade 访问</li>
 *   <li>{@code BlockAnimationEngine} - 必须通过 Facade 访问</li>
 *   <li>{@code PlaybackController} - 必须通过 Facade 访问</li>
 * </ul>
 * <p>
 * 这是防止架构退化的"铁门"。如果发现这些导入，说明架构已被破坏。
 */
public class ToolbarPanel extends AbstractPanel implements IPanelRegistryAware {

    private final ToolRegistry toolRegistry;
    private IPanelRegistry panelRegistry;
    private UICommandFacade commands;

    /**
     * 工具按钮是否显示文字（默认：图标模式，更紧凑）
     *
     * <p>说明：这里先用静态字段做会话级别开关；如果你希望跨存档/跨会话持久化，
     * 后续可以把它接入 UI 配置系统或布局持久化。</p>
     */
    private static boolean SHOW_TOOL_TEXT = false;

    /**
     * 工具分组“图标列”的固定宽度。
     *
     * <p>用于解决窄窗口时不同组图标字符宽度不一致导致的缩进对齐问题。</p>
     */
    private static final float GROUP_ICON_COLUMN_WIDTH = UITheme.Spacing.BUTTON_HEIGHT; // 24px：与按钮高度一致，视觉统一

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

        // 工具按钮组（组级换行优先，组内再换行）
        renderSelectionToolButtons();

        // 工具栏主体只保留：工具区 + 面板快捷区
        // FPS/关闭已迁移到 MenuBarPanel 右侧（全局状态/窗口级操作）
        boolean firstSection = false; // 工具区已渲染

        renderPanelShortcutsSection(firstSection);

        // 移除集成的面板渲染，现在使用独立面板
        // 这些面板现在通过Window菜单的Toggle Panels来管理
        // 或者通过工具栏按钮打开独立面板

        // 快捷键处理已迁移到 ShortcutDispatcher（统一入口）
        // 不再在此处处理快捷键输入

        ImGui.popStyleVar(2);
    }

    /**
     * 面板快捷按钮区块（属性/过滤/历史）
     *
     * <p>规则：先区块换行，再区块内按钮换行。</p>
     * <p>注意：帮助面板已移除，可通过 Window 菜单 → Toggle Panels 控制。</p>
     */
    private boolean renderPanelShortcutsSection(boolean firstSection) {
        // 区块总宽估算（用于决定“区块整体是否换行”）
        float totalW = estimatePanelShortcutsTotalWidth();
        beginSection(firstSection, totalW);

        renderPanelToggleButtonWrapped(Icons.SETTINGS, "属性", ToolPropertiesPanel.class, "打开工具属性面板", ToolPropertiesPanel.PANEL_NAME);
        renderPanelToggleButtonWrapped(Icons.FILTER, "过滤", SelectionFilterPanel.class, "打开选择过滤器面板", SelectionFilterPanel.PANEL_NAME);
        renderPanelToggleButtonWrapped(Icons.UNDO, "历史", SelectionHistoryPanel.class, "打开选择历史面板", SelectionHistoryPanel.PANEL_NAME);

        return false;
    }

    /**
     * 区块级换行优先：如果当前行剩余宽度不够，则整块换行；否则同一行继续。
     */
    private void beginSection(boolean firstSection, float minSectionWidth) {
        if (firstSection) {
            return;
        }
        // 留一点间距，避免紧贴
        float required = minSectionWidth + UITheme.Spacing.ELEMENT_SPACING;
        if (required > ImGui.getContentRegionAvailX()) {
            ImGui.newLine();
        } else {
            ImGui.sameLine();
        }
    }

    private float estimatePanelShortcutsTotalWidth() {
        // 估算三个按钮在同一行时的总宽（用来决定是否整块换行）
        // 注意：帮助按钮已移除，现在只有属性/过滤/历史三个按钮
        float spacing = UITheme.Spacing.ELEMENT_SPACING;
        float w1 = estimatePanelButtonWidth(Icons.SETTINGS, "属性");
        float w2 = estimatePanelButtonWidth(Icons.FILTER, "过滤");
        float w3 = estimatePanelButtonWidth(Icons.UNDO, "历史");
        return w1 + w2 + w3 + spacing * 2;
    }

    private float estimatePanelButtonWidth(String icon, String label) {
        return estimateButtonWidth(icon + " " + label);
    }

    private float estimateButtonWidth(String visibleText) {
        float textW = ImGui.calcTextSize(visibleText).x;
        float paddingX = ImGui.getStyle().getFramePadding().x * 2.0f;
        return textW + paddingX;
    }

    /**
     * 面板按钮：区块内换行版本。
     */
    private void renderPanelToggleButtonWrapped(String icon, String label, Class<? extends IPanel> panelClass,
                                                String tooltip, String... fallbackPanelNames) {
        float btnW = estimatePanelButtonWidth(icon, label);
        if (btnW + UITheme.Spacing.ELEMENT_SPACING > ImGui.getContentRegionAvailX()) {
            ImGui.newLine();
        } else {
            ImGui.sameLine();
        }
        renderPanelToggleButton(icon, label, panelClass, tooltip, fallbackPanelNames);
    }

    /* ---------- 选择工具按钮 ---------- */
    private void renderSelectionToolButtons() {
        // 组级换行优先：先决定“下一组是否换行”，再进入组内渲染并对工具按钮进行换行
        boolean firstGroup;

        firstGroup = renderToolGroupWrapped(ToolGroup.SELECTION, Icons.TOOLS, true);
        firstGroup = renderToolGroupWrapped(ToolGroup.WORLD, Icons.CONTAINER, firstGroup);
        firstGroup = renderToolGroupWrapped(ToolGroup.TIMELINE, Icons.TIMELINE, firstGroup);

        // “图标/文字切换”也视为一个小组：窄时允许它整体换行
        renderToolTextToggleWrapped(firstGroup);
    }

    /**
     * 组级换行优先：
     * - 先决定“下一组是否整体换行”
     * - 进入组内后，再对工具按钮做自动换行（换行时保持按钮对齐，不把下一组挤进来）
     *
     * @return false 表示已经渲染过至少一个组；true 表示仍然是第一个组（通常只会发生在 group 为空时）
     */
    private boolean renderToolGroupWrapped(ToolGroup group, String groupIcon, boolean firstGroup) {
        List<EditorTool> tools = toolRegistry.getToolsByGroup(group);
        if (tools.isEmpty()) {
            return firstGroup;
        }

        float minGroupWidth = estimateMinGroupWidth(tools.getFirst());
        if (!firstGroup) {
            if (minGroupWidth > ImGui.getContentRegionAvailX()) {
                ImGui.newLine();
            } else {
                ImGui.sameLine();
            }
        }

        // 组图标（只绘制一次），并使用“固定图标列宽”保证所有组按钮起始 X 一致
        float groupStartX = ImGui.getCursorPosX();
        UITheme.label(groupIcon, UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.sameLine();
        ImGui.setCursorPosX(groupStartX + GROUP_ICON_COLUMN_WIDTH);
        float buttonsStartX = ImGui.getCursorPosX();

        for (int i = 0; i < tools.size(); i++) {
            EditorTool tool = tools.get(i);
            if (tool == null) continue;

            float buttonW = getToolButtonWidth(tool);

            // 组内换行：如果当前行容不下按钮，则换行并把 X 对齐到 buttonsStartX
            if (buttonW > ImGui.getContentRegionAvailX()) {
                ImGui.newLine();
                ImGui.setCursorPosX(buttonsStartX);
            }

            // 状态显示：只读查询，不缓存
            boolean active = commands != null && commands.tools() != null 
                    ? commands.tools().isToolActive(tool.getId())
                    : tool.isActive();
            boolean enabled = tool.isEnabled();

            if (active) {
                float[] highlightColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3]);
            }
            if (!enabled) {
                ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.45f);
            }

            String label = getToolButtonLabel(tool);
            String buttonId = "ToolbarPanel_tool_" + tool.getId();
            float drawW = SHOW_TOOL_TEXT ? 0 : UITheme.Spacing.BUTTON_HEIGHT;
            if (ImGui.button(label + "##" + buttonId, drawW, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (enabled && commands != null && commands.tools() != null) {
                    logger.info("[ToolbarPanel] 工具按钮点击: {} (ID: {})", tool.getDisplayName(), tool.getId());
                    commands.tools().activateTool(tool.getId());
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

            // 下一个按钮：能放下才 sameLine，否则让下次循环触发 newLine+对齐
            if (i < tools.size() - 1) {
                float nextW = getToolButtonWidth(tools.get(i + 1));
                if (nextW <= ImGui.getContentRegionAvailX()) {
                    ImGui.sameLine();
                }
            }
        }

        return false;
    }

    private void renderToolTextToggleWrapped(boolean firstGroup) {
        float toggleW = UITheme.Spacing.BUTTON_HEIGHT;
        float minW = toggleW + UITheme.Spacing.ELEMENT_SPACING;

        if (!firstGroup) {
            if (minW > ImGui.getContentRegionAvailX()) {
                ImGui.newLine();
            } else {
                ImGui.sameLine();
            }
        }

        renderToolTextToggleButton();
    }

    private float estimateMinGroupWidth(EditorTool firstTool) {
        // 使用固定图标列宽（不要用 calcTextSize，否则不同图标宽度会导致对齐漂移）
        float minBtnW = firstTool != null ? getToolButtonWidth(firstTool) : UITheme.Spacing.BUTTON_HEIGHT;
        return GROUP_ICON_COLUMN_WIDTH + UITheme.Spacing.ELEMENT_SPACING + minBtnW;
    }

    private float getToolButtonWidth(EditorTool tool) {
        if (tool == null) return UITheme.Spacing.BUTTON_HEIGHT;
        if (!SHOW_TOOL_TEXT) {
            return UITheme.Spacing.BUTTON_HEIGHT;
        }
        String label = getToolButtonLabel(tool);
        float textW = ImGui.calcTextSize(label).x;
        float paddingX = ImGui.getStyle().getFramePadding().x * 2.0f;
        return textW + paddingX;
    }

    private String getToolButtonLabel(EditorTool tool) {
        if (tool == null) return Icons.TOOLS;
        String icon = tool.getIcon();
        return SHOW_TOOL_TEXT ? (icon + " " + tool.getDisplayName()) : icon;
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

        // 快捷键：从 ShortcutBindings 系统读取（若存在）
        String shortcut = com.chronoblocks.ui.input.ShortcutBindings.getToolShortcutText(tool.getId());
        if (shortcut != null && !shortcut.isBlank()) {
            ImGui.separator();
            ImGui.text("快捷键: " + shortcut);
        }
        ImGui.endTooltip();
    }

    // 工具切换与图标映射已统一交由 ToolRegistry/SelectionTool 自身提供（避免 ToolbarPanel 硬编码）

    // 移除不再使用的方法，现在使用独立面板

    /**
     * 设置命令门面（供依赖注入使用）
     */
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }
    
    /**
     * 设置面板注册表引用
     */
    @Override
    public void setPanelRegistry(IPanelRegistry panelRegistry) {
        this.panelRegistry = panelRegistry;
    }

    /**
     * 切换面板显示状态（通过 Facade）
     */
    private void togglePanel(Class<? extends IPanel> panelClass, String... fallbackPanelNames) {
        if (commands == null || commands.view() == null) {
            return;
        }
        
        // 尝试通过 PanelId 获取面板 ID
        String panelId = null;
        if (panelClass != null) {
            if (panelRegistry != null) {
                IPanel panel = panelRegistry.getPanel(panelClass);
                if (panel != null) {
                    panelId = PanelId.fromPanel(panel);
                }
            }
        }
        
        // 如果通过类找不到，尝试通过 fallback 名称
        if (panelId == null && fallbackPanelNames != null) {
            for (String name : fallbackPanelNames) {
                if (name != null && !name.isBlank() && panelRegistry != null) {
                    IPanel panel = panelRegistry.getPanelByName(name);
                    if (panel != null) {
                        panelId = PanelId.fromPanel(panel);
                        if (panelId != null) break;
                    }
                }
            }
        }
        
        if (panelId != null) {
            commands.view().togglePanel(panelId);
        }
    }

    // 快捷键处理已迁移到 ShortcutDispatcher（统一入口）
    // 不再需要 handleKeyInput() 方法

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