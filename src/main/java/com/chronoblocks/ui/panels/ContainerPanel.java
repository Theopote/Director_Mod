package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.ui.container.ContainerDialogs;
import com.chronoblocks.ui.container.ContainerPanelController;
import com.chronoblocks.ui.container.ContainerPanelState;
import com.chronoblocks.ui.container.ContainerToolbar;
import com.chronoblocks.ui.container.ContainerTreeView;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 容器面板根组件，负责面板级布局与子组件生命周期。
 */
public class ContainerPanel extends AbstractPanel implements ContainerManager.ContainerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPanel.class);

    private static final float MIN_PANEL_RATIO = 0.15f;
    private static final float MAX_PANEL_RATIO = 0.85f;
    private static final float SPLITTER_WIDTH = 8.0f;
    private static final float SPLITTER_LINE_WIDTH = 1.0f;
    private static final float SPLITTER_HIGHLIGHT_WIDTH = 2.0f;
    private static final int SPLITTER_LINE_COLOR = 0x40FFFFFF;
    private static final int SPLITTER_HIGHLIGHT_COLOR = 0x80FFFFFF;
    private static final int SPLITTER_SHADOW_COLOR = 0x10FFFFFF;
    private static final String SPLITTER_ID = "ContainerPanelSplitter";

    // 右侧属性面板折叠把手宽度（紧凑）
    private static final float PROPERTIES_HANDLE_WIDTH = 22.0f;

    /** 容器树焦点状态（在 doRender 开头更新，供 ShortcutContext / ShortcutDispatcher 使用） */
    private static volatile boolean _containerTreeFocused;

    private final ContainerManager containerManager;
    private final TimelineCommandHistory commandHistory;
    private final ContainerPropertiesPanel propertiesPanel;

    private final ContainerPanelState state;
    private final ContainerPanelController controller;
    private final ContainerToolbar toolbar;
    private final ContainerTreeView treeView;
    private final ContainerDialogs dialogs;
    private final GroupPropertiesPanel groupPropertiesPanel;

    public ContainerPanel(ContainerManager containerManager,
                          TimelineCommandHistory commandHistory) {
        this.containerManager = containerManager;
        this.commandHistory = commandHistory;

        this.state = new ContainerPanelState();
        this.controller = new ContainerPanelController(containerManager, commandHistory, state);
        this.propertiesPanel = new ContainerPropertiesPanel(controller);
        this.toolbar = new ContainerToolbar(controller);
        this.treeView = new ContainerTreeView(controller, state);
        this.dialogs = new ContainerDialogs(controller, state);
        this.groupPropertiesPanel = new GroupPropertiesPanel(containerManager);

        containerManager.addListener(this);
    }

    @Override
    public String getPanelName() {
        return "Container";
    }

    @Override
    public String getWindowName() {
        return "Container###ContainerPanel";
    }

    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }

    @Override
    protected void doRender() {
        _containerTreeFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
        handleKeyboardShortcuts();

        renderHeader();
        ImGui.separator();
        toolbar.render();
        ImGui.separator();

        renderResizableLayout();
        dialogs.render();
    }

    private void renderHeader() {
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, Icons.CONTAINER + " 容器管理 ");
        ImGui.sameLine();
        ImGui.textDisabled(String.format("(%d 个容器)", containerManager.getContainerCount()));

        // ✅ 紧凑：把"分割比例"变成 tooltip，不占 header 位置
        ImGui.sameLine();
        ImGui.textDisabled(state.isPropertiesCollapsed() ? "(属性已折叠 · P)" : "(属性已展开 · P)");
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text("布局");
            ImGui.separator();
            ImGui.text(String.format("左侧宽度: %.0f%%", state.getLeftPanelWidthRatio() * 100));
            ImGui.text("点击右侧边界箭头可折叠/展开属性面板");
            ImGui.endTooltip();
        }

        ImGui.sameLine();
        renderHeaderControls();
    }

    private void renderHeaderControls() {
        boolean showText = state.isShowToolbarText();

        boolean canUndo = controller.canUndo();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.smallButton(headerButtonLabel(Icons.UNDO, "撤销", "undo"))) {
            controller.undo();
        }
        if (!canUndo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("撤销");
        }

        ImGui.sameLine();
        boolean canRedo = controller.canRedo();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.smallButton(headerButtonLabel(Icons.REDO, "重做", "redo"))) {
            controller.redo();
        }
        if (!canRedo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重做");
        }

        // 图标/文字显示切换（与 ToolbarPanel 类似）
        ImGui.sameLine();
        String toggleIcon = showText ? Icons.TOOLS : Icons.TAG;
        if (ImGui.smallButton(toggleIcon + "##containerHeaderToggleText")) {
            state.setShowToolbarText(!showText);
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text("按钮显示");
            ImGui.separator();
            ImGui.text(showText ? "当前：图标 + 文字" : "当前：仅图标");
            ImGui.text("点击切换显示方式");
            ImGui.endTooltip();
        }

        // 布局：展开/折叠全部、重置分割 收起到弹出菜单，减轻 header 拥挤
        ImGui.sameLine();
        if (ImGui.smallButton(headerButtonLabel(Icons.MORE_VERTICAL, "布局", "layoutMenu"))) {
            ImGui.openPopup("ContainerLayoutPopup##layout");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("展开/折叠树、重置左右分割比例");
        }
        if (ImGui.beginPopup("ContainerLayoutPopup##layout")) {
            String expandLabel = state.isExpandAll() ? "折叠全部" : "展开全部";
            if (ImGui.menuItem(expandLabel)) {
                controller.toggleExpandAll();
            }
            if (ImGui.menuItem("重置分割")) {
                state.setLeftPanelWidthRatio(0.5f);
                state.setLeftRatioBeforeCollapse(0.5f);
            }
            ImGui.endPopup();
        }
    }

    /** 容器树是否有焦点（供 ShortcutContext / ShortcutDispatcher 做 Delete 焦点路由） */
    public static boolean isContainerTreeFocused() {
        return _containerTreeFocused;
    }

    /** 请求删除选中容器（有选中则 setPendingDeletionRequested，由 Facade/Shortcut 调用；Delete 已迁出 handleKeyboardShortcuts） */
    public void requestDeleteSelected() {
        if (!containerManager.getSelectedContainersView().isEmpty()) {
            state.setPendingDeletionRequested(true);
        }
    }

    private String headerButtonLabel(String icon, String text, String id) {
        // smallButton 只能接收 label，所以用“##id”隔离 ID
        if (state.isShowToolbarText()) {
            return icon + " " + text + "##" + id;
        }
        return icon + "##" + id;
    }

    private void renderResizableLayout() {
        float windowWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY();

        if (state.isPropertiesCollapsed()) {
            renderCollapsedLayout(windowWidth, availableHeight);
            return;
        }

        // ✅ 展开状态：保持现有 splitter 布局
        float leftWidth = windowWidth * state.getLeftPanelWidthRatio() - SPLITTER_WIDTH / 2;

        if (ImGui.beginChild("ContainerTreeChild", leftWidth, availableHeight, true)) {
            treeView.render();
        }
        ImGui.endChild();

        ImGui.sameLine();
        renderVerticalSplitter(availableHeight, windowWidth);

        ImGui.sameLine();
        if (ImGui.beginChild("PropertiesChild", 0, availableHeight, true)) {
            renderPropertiesPanel();
        }
        ImGui.endChild();
    }

    private void renderCollapsedLayout(float windowWidth, float availableHeight) {
        float leftWidth = Math.max(0, windowWidth - PROPERTIES_HANDLE_WIDTH);

        if (ImGui.beginChild("ContainerTreeChild", leftWidth, availableHeight, true)) {
            treeView.render();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // 右侧把手：一个窄 child，里面放箭头按钮
        if (ImGui.beginChild("PropertiesHandleChild", PROPERTIES_HANDLE_WIDTH, availableHeight, true)) {
            // 垂直居中
            float btnSize = PROPERTIES_HANDLE_WIDTH - 4.0f;
            float centerY = (availableHeight - btnSize) * 0.5f;
            ImGui.setCursorPosY(Math.max(2.0f, centerY));

            if (ImGui.button(Icons.CHEVRON_RIGHT + "##expandProperties", btnSize, btnSize)) {
                togglePropertiesCollapsed();
            }
            if (ImGui.isItemHovered()) {
                Container selected = containerManager.getSelectedContainer();
                ImGui.beginTooltip();
                if (selected != null) {
                    ImGui.text("选中: " + selected.getName());
                    ImGui.text("类型: " + selected.getType().toString());
                    ImGui.separator();
                }
                ImGui.text("展开属性面板");
                ImGui.text("快捷键：P");
                ImGui.endTooltip();
            }
        }
        ImGui.endChild();
    }

    private void renderVerticalSplitter(float height, float windowWidth) {
        ImGui.invisibleButton(SPLITTER_ID, SPLITTER_WIDTH, height);

        // ✅ 双击 splitter：折叠/展开（可选但很顺手）
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            togglePropertiesCollapsed();
        }

        handleSplitterDrag(windowWidth);

        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }

        drawSplitterVisuals();
    }

    private void togglePropertiesCollapsed() {
        boolean collapsed = state.isPropertiesCollapsed();

        if (collapsed) {
            // 展开：double-clamp 确保比例合法（防止布局恢复时的异常值）
            float ratio = clampRatio(state.getLeftRatioBeforeCollapse());
            state.setPropertiesCollapsed(false);
            state.setLeftPanelWidthRatio(ratio);
        } else {
            // 折叠：保存当前比例
            state.setLeftRatioBeforeCollapse(state.getLeftPanelWidthRatio());
            state.setPropertiesCollapsed(true);
        }
    }

    private float clampRatio(float ratio) {
        return Math.max(MIN_PANEL_RATIO, Math.min(MAX_PANEL_RATIO, ratio));
    }

    private void handleSplitterDrag(float windowWidth) {
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            float mouseDeltaX = ImGui.getIO().getMouseDeltaX();
            float ratio = state.getLeftPanelWidthRatio() + mouseDeltaX / windowWidth;
            ratio = clampRatio(ratio);
            state.setLeftPanelWidthRatio(ratio);
            state.setLeftRatioBeforeCollapse(ratio); // ✅ 同步保存"上次比例"
        }
    }

    private void drawSplitterVisuals() {
        ImVec2 splitterPos = ImGui.getItemRectMin();
        ImVec2 splitterSize = ImGui.getItemRectSize();
        float centerX = splitterPos.x + splitterSize.x / 2;
        boolean active = ImGui.isItemHovered() || ImGui.isItemActive();

        float lineWidth = active ? SPLITTER_HIGHLIGHT_WIDTH : SPLITTER_LINE_WIDTH;
        float lineX = centerX - lineWidth / 2;
        int lineColor = active ? SPLITTER_HIGHLIGHT_COLOR : SPLITTER_LINE_COLOR;

        ImGui.getWindowDrawList().addRectFilled(
            lineX, splitterPos.y,
            lineX + lineWidth, splitterPos.y + splitterSize.y,
            lineColor, 0.0f
        );

        ImGui.getWindowDrawList().addRectFilled(
            lineX + 1, splitterPos.y,
            lineX + lineWidth + 1, splitterPos.y + splitterSize.y,
            SPLITTER_SHADOW_COLOR, 0.0f
        );
    }

    private void renderPropertiesPanel() {
        // ✅ 顶部"属性"标题和折叠按钮
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("属性");
        ImGui.sameLine();
        if (ImGui.smallButton(Icons.CHEVRON_LEFT + "##collapsePropertiesTop")) {
            togglePropertiesCollapsed();
            return;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("折叠属性面板\n快捷键：P");
        }

        ImGui.separator();

        Container selected = containerManager.getSelectedContainer();
        if (selected == null) {
            ImGui.textDisabled("(未选择容器或组)");
            ImGui.spacing();
            ImGui.textWrapped("在左侧容器树中选择一个条目以查看和编辑属性。");
            return;
        }

        if (selected.getType() == ContainerType.GROUP) {
            groupPropertiesPanel.render(selected);
        } else {
            propertiesPanel.render(selected);
        }
    }

    private void handleKeyboardShortcuts() {
        // ✅ 检查窗口焦点（包括子窗口），确保在容器树列表区域也能响应快捷键
        // 注意：ImGui.isWindowFocused() 必须在 ImGui.begin() 之后调用（已在 AbstractPanel.render() 中确保）
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            return;
        }

        // 避免在文本输入时误触
        if (ImGui.getIO().getWantTextInput()) {
            return;
        }

        // ===== 新增：P 键折叠 / 展开属性面板 =====
        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_P)) {
            togglePropertiesCollapsed();
        }

        // ===== 原有快捷键（Undo / Redo；Delete 已迁到 UIShortcutCoordinator/ShortcutDispatcher） =====
        boolean ctrl = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);

        if (ctrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            if (commandHistory.canUndo()) {
                commandHistory.undo();
            }
        }

        if (ctrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_Y)) {
            if (commandHistory.canRedo()) {
                commandHistory.redo();
            }
        }
    }

    @Override
    public void onContainerAdded(Container container) {
        LOGGER.debug("[ContainerPanel] 容器添加: {}", container.getName());
    }

    @Override
    public void onContainerRemoved(Container container) {
        LOGGER.debug("[ContainerPanel] 容器删除: {}", container.getName());
    }

    @Override
    public void onContainerMoved(Container container, Container newParent) {
        LOGGER.debug("[ContainerPanel] 容器移动: {} -> {}", container.getName(),
            newParent != null ? newParent.getName() : "根目录");
    }

    @Override
    public void onSelectionChanged(Set<Container> selected) {
        LOGGER.debug("[ContainerPanel] 选中变化: {} 个容器", selected.size());
    }
}