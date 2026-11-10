package com.director.ui.panels;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.PanelWindows;
import com.director.ui.PanelId;
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
     * 工具按钮是否显示文字（默认只显示图标）
     *
     * <p>说明：这里先用静态字段做会话级别开关；如果你希望跨存档/跨会话持久化，
     * 后续可以把它接入 UI 配置系统或布局持久化。</p>
     */
    private static boolean SHOW_TOOL_TEXT = false;

    private static final float BUTTON_SIZE = 32f;
    private static final ToolGroup[] GROUPS = {ToolGroup.SELECTION, ToolGroup.ACTOR, ToolGroup.WORLD, ToolGroup.TIMELINE};
    private static final String[] ACTOR_TOOL_IDS = {"actor_move", "actor_rotate", "actor_scale", "actor_look_at"};
    private static final String[] ACTOR_TOOL_LABELS = {"move", "rotate", "scale", "look"};

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
        return PanelId.TOOLBAR;
    }

    @Override
    public String getWindowName() {
        return PanelWindows.toolbar();
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
        toolRegistry.initializeIfNeeded();
        toolRegistry.ensureDefaultTool("click");

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        try {
            float width = ImGui.getContentRegionAvailX();
            layoutTools(toolRegistry, width, true);
            layoutActorModes(toolRegistry, width, true);
        } finally {
            ImGui.popStyleVar();
        }
    }

    /** Calculates the initial height for the default dock node using the rendered row layout. */
    public float preferredDockHeight(float dockWidth) {
        ToolRegistry registry = toolRegistry;
        registry.initializeIfNeeded();
        float padding = UITheme.Spacing.PANEL_PADDING * 2;
        float width = Math.max(1f, dockWidth - padding);
        return ImGui.getFrameHeight() + padding
                + layoutTools(registry, width, false) + layoutActorModes(registry, width, false);
    }

    private float layoutTools(ToolRegistry registry, float width, boolean render) {
        // Snapshot the mode so a toggle applies consistently from the next frame.
        boolean showText = SHOW_TOOL_TEXT;
        ButtonRows rows = new ButtonRows(width);
        for (ToolGroup group : GROUPS) {
            boolean firstInGroup = true;
            for (EditorTool tool : registry.getToolsByGroup(group)) {
                if (tool == null || ToolRegistry.isPlaceholderToolId(tool.id())) {
                    continue;
                }
                String label = showText ? tool.icon() + " " + tool.getDisplayName() : tool.icon();
                float buttonWidth = showText ? textButtonWidth(label) : BUTTON_SIZE;
                float gap = rows.place(buttonWidth, firstInGroup);
                if (render) {
                    placeButton(gap, firstInGroup);
                    renderToolButton(tool, group, label, Math.min(buttonWidth, rows.width));
                }
                firstInGroup = false;
            }
        }
        float gap = rows.place(BUTTON_SIZE, true);
        if (render) {
            placeButton(gap, true);
            renderToolTextToggleButton(Math.min(BUTTON_SIZE, rows.width));
        }
        return rows.height();
    }

    private float layoutActorModes(ToolRegistry registry, float width, boolean render) {
        if (!ToolRegistry.ACTOR_SELECT_TOOL_ID.equals(registry.getActiveNonSelectionToolId())) {
            return 0f;
        }
        var mode = com.director.ui.actor.gizmo.ActorGizmoController.getInstance().getPreferredMode();
        String hint = tr("gui.director.actor.tools." + switch (mode) {
            case LOOK_AT -> "look_hint";
            case ROTATE -> "rotate_hint";
            case SCALE -> "scale_hint";
            default -> "transform_hint";
        });
        ButtonRows rows = new ButtonRows(width);
        if (render) {
            ImGui.separator();
            ImGui.textUnformatted(tr("gui.director.actor.tools.whole"));
        }
        for (int i = 0; i < ACTOR_TOOL_IDS.length; i++) {
            String label = tr("gui.director.actor.tools." + ACTOR_TOOL_LABELS[i]);
            float buttonWidth = textButtonWidth(label);
            float gap = rows.place(buttonWidth, false);
            if (render) {
                placeButton(gap, false);
                boolean selected = mode == switch (i) {
                    case 1 -> com.director.client.gizmo.GizmoMode.ROTATE;
                    case 2 -> com.director.client.gizmo.GizmoMode.SCALE;
                    case 3 -> com.director.client.gizmo.GizmoMode.LOOK_AT;
                    default -> com.director.client.gizmo.GizmoMode.TRANSLATE;
                };
                if (toolButton(label + "##" + ACTOR_TOOL_IDS[i], Math.min(buttonWidth, rows.width), selected, true)) {
                    activateTool(ACTOR_TOOL_IDS[i]);
                }
            }
        }
        if (render) {
            ImGui.textWrapped(hint);
        }
        return rows.height() + ImGui.getTextLineHeight() + ImGui.calcTextSize(hint, false, rows.width).y
                + 4 * UITheme.Spacing.ELEMENT_SPACING + 1f;
    }

    private static float textButtonWidth(String label) {
        return Math.max(BUTTON_SIZE, ImGui.calcTextSize(label).x + 2 * UITheme.Spacing.BUTTON_PADDING_X);
    }

    private static void placeButton(float gap, boolean groupStart) {
        if (gap <= 0) {
            return;
        }
        ImGui.sameLine(0, gap);
        if (groupStart) {
            float x = ImGui.getCursorScreenPosX() - gap * 0.5f;
            float y = ImGui.getCursorScreenPosY();
            ImGui.getWindowDrawList().addLine(x, y + 4f, x, y + BUTTON_SIZE - 4f,
                    ImGui.getColorU32(ImGuiCol.Separator), 0.5f);
        }
    }

    private void renderToolButton(EditorTool tool, ToolGroup group, String label, float width) {
        boolean active = commands != null && commands.tools() != null
                ? commands.tools().isToolActive(tool.id()) : tool.isActive();
        if (toolButton(label + "##ToolbarPanel_tool_" + tool.id(), width, active, tool.isEnabled())) {
            activateTool(tool.id());
        }
        if (ImGui.isItemHovered()) {
            renderToolTooltip(tool, group);
        }
    }

    private static boolean toolButton(String label, float width, boolean active, boolean enabled) {
        if (active) {
            float[] fill = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
            float[] border = UITheme.toFloatArrayPublic(UITheme.Semantic.INFO);
            ImGui.pushStyleColor(ImGuiCol.Button, fill[0], fill[1], fill[2], fill[3]);
            ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], border[3]);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f);
        }
        if (!enabled) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.45f);
        }
        try {
            return ImGui.button(label, width, BUTTON_SIZE) && enabled;
        } finally {
            if (!enabled) {
                ImGui.popStyleVar();
            }
            if (active) {
                ImGui.popStyleVar();
                ImGui.popStyleColor(2);
            }
        }
    }

    private void activateTool(String id) {
        if (commands != null && commands.tools() != null) {
            commands.tools().activateTool(id);
        }
    }

    private static final class ButtonRows {
        private final float width;
        private int count;
        private float usedWidth;

        private ButtonRows(float width) {
            this.width = Math.max(1f, width);
        }

        private float place(float desiredWidth, boolean groupStart) {
            float buttonWidth = Math.min(desiredWidth, width);
            float spacing = UITheme.Spacing.ELEMENT_SPACING;
            float gap = groupStart ? 2 * spacing : spacing;
            if (count == 0 || usedWidth + gap + buttonWidth > width) {
                count++;
                usedWidth = buttonWidth;
                return 0f;
            }
            usedWidth += gap + buttonWidth;
            return gap;
        }

        private float height() {
            return count * BUTTON_SIZE + Math.max(0, count - 1) * UITheme.Spacing.ELEMENT_SPACING;
        }
    }

    private void renderToolTextToggleButton(float width) {
        String icon = SHOW_TOOL_TEXT ? Icons.TOOLS : Icons.TAG;
        String label = icon + "##toggleToolText";

        // 这里也用方形按钮，保持一致
        if (ImGui.button(label, width, BUTTON_SIZE)) {
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

    private void renderToolTooltip(EditorTool tool, ToolGroup group) {
        if (tool == null) return;
        ImGui.beginTooltip();
        ImGui.textUnformatted(tool.getDisplayName());
        ImGui.textDisabled(group.getDisplayName());
        ImGui.separator();
        String usage = simplifyUsage(tool.getDescription());
        if (!usage.isBlank()) {
            ImGui.textWrapped(usage);
        }

        // 快捷键：从 ShortcutBindings 系统读取（若存在）
        String shortcut = com.director.ui.input.ShortcutBindings.getToolShortcutText(tool.id());
        if (shortcut != null && !shortcut.isBlank()) {
            ImGui.separator();
            ImGui.textDisabled(DirectorI18n.translate(DirectorLang.Toolbar.SHORTCUT, shortcut));
        }
        ImGui.endTooltip();
    }

    private String simplifyUsage(String description) {
        if (description == null) {
            return "";
        }
        String s = description.replace('\n', ' ').trim();
        if (s.isEmpty()) {
            return "";
        }
        int cut = firstSentenceCutIndex(s);
        return cut > 0 ? s.substring(0, cut).trim() : s;
    }

    private int firstSentenceCutIndex(String s) {
        int idx = -1;
        char[] marks = new char[] {'。', '.', '；', ';', '！', '!', '？', '?'};
        for (char mark : marks) {
            int i = s.indexOf(mark);
            if (i >= 0 && (idx < 0 || i < idx)) {
                idx = i + 1;
            }
        }
        return idx;
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
