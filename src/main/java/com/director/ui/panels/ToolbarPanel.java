package com.director.ui.panels;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.aware.IPanelRegistryAware;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import com.director.ui.tools.EditorTool;
import com.director.ui.tools.ToolGroup;
import com.director.ui.tools.ToolRegistry;
import com.director.ui.commands.UICommandFacade;

import java.util.List;

/**
 * Director Toolbar - Facade-based implementation
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
 *   <li>{@code PlaybackOrchestrator} - 必须通过 Facade 访问</li>
 * </ul>
 * <p>
 * 这是防止架构退化的"铁门"。如果发现这些导入，说明架构已被破坏。
 */
public class ToolbarPanel extends AbstractPanel implements IPanelRegistryAware, ICommandsAware {

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

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

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

        // 使用统一的元素间距；图标模式用更小的 FramePadding，使图标在按钮内尽量占满并居中（ButtonTextAlign 在 DirectorStyleScope 中全局为 0.5,0.5）
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        if (SHOW_TOOL_TEXT) {
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        } else {
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.ICON_BUTTON_PADDING_X, UITheme.Spacing.ICON_BUTTON_PADDING_Y);
        }

        // 工具按钮组（组级换行优先，组内再换行）
        renderSelectionToolButtons();

        // 工具栏主体只保留工具区；属性/过滤/历史由 Window → Toggle Panels 控制
        // FPS/关闭已迁移到 MenuBarPanel 右侧（全局状态/窗口级操作）

        // 移除集成的面板渲染，现在使用独立面板
        // 这些面板现在通过Window菜单的Toggle Panels来管理
        // 或者通过工具栏按钮打开独立面板

        // 快捷键处理已迁移到 ShortcutDispatcher（统一入口）
        // 不再在此处处理快捷键输入

        ImGui.popStyleVar(2);
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

    /** 当前 ItemSpacing.x，用于换行时预留间距 */
    private static float getItemSpacingX() {
        return ImGui.getStyle().getItemSpacing().x;
    }

    /**
     * 组级换行优先：
     * - 先 sameLine 到上一项末尾，再根据「剩余宽度」决定是否换行，确保组/按钮随面板宽度自动换行
     * - 进入组内后，对每个工具按钮：若当前行放不下则换行并对齐到 buttonsStartX
     *
     * @return false 表示已经渲染过至少一个组；true 表示仍然是第一个组（通常只会发生在 group 为空时）
     */
    private boolean renderToolGroupWrapped(ToolGroup group, String groupIcon, boolean firstGroup) {
        List<EditorTool> tools = toolRegistry.getToolsByGroup(group);
        if (tools.isEmpty()) {
            return firstGroup;
        }

        float minGroupWidth = estimateMinGroupWidth(tools.getFirst());
        float spx = getItemSpacingX();

        if (!firstGroup) {
            ImGui.sameLine(); // 先回到上一项末尾，才能用 getContentRegionAvailX 得到当前行剩余宽度
            if (minGroupWidth + spx > ImGui.getContentRegionAvailX()) {
                ImGui.newLine();
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
            float avail = ImGui.getContentRegionAvailX();

            // 组内换行：当前行放不下则换行，并对齐到 buttonsStartX；下一项间距由下一项判断时处理
            if (buttonW > avail) {
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

            // 有下一个按钮则 sameLine，换行由下一次循环开头的「放不下则 newLine」统一处理
            if (i < tools.size() - 1) {
                ImGui.sameLine();
            }
        }

        return false;
    }

    private void renderToolTextToggleWrapped(boolean firstGroup) {
        float toggleW = UITheme.Spacing.BUTTON_HEIGHT;
        float minW = toggleW + getItemSpacingX();

        if (!firstGroup) {
            ImGui.sameLine();
            if (minW > ImGui.getContentRegionAvailX()) {
                ImGui.newLine();
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
            ImGui.text(tr(DirectorLang.Toolbar.TOOL_DISPLAY_TITLE));
            ImGui.separator();
            ImGui.text(tr(SHOW_TOOL_TEXT
                    ? DirectorLang.Toolbar.TOOL_DISPLAY_ICONS_TEXT
                    : DirectorLang.Toolbar.TOOL_DISPLAY_ICONS_ONLY));
            ImGui.text(tr(DirectorLang.Toolbar.TOOL_DISPLAY_TOGGLE_HINT));
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
        String shortcut = com.director.ui.input.ShortcutBindings.getToolShortcutText(tool.getId());
        if (shortcut != null && !shortcut.isBlank()) {
            ImGui.separator();
            ImGui.text(DirectorI18n.translate(DirectorLang.Toolbar.SHORTCUT, shortcut));
        }
        ImGui.endTooltip();
    }

    // 工具切换与图标映射已统一交由 ToolRegistry/SelectionTool 自身提供（避免 ToolbarPanel 硬编码）

    // 移除不再使用的方法，现在使用独立面板

    /**
     * 设置命令门面（供依赖注入使用）
     */
    @Override
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

    // 快捷键处理已迁移到 ShortcutDispatcher（统一入口）
    // 不再需要 handleKeyInput() 方法
}