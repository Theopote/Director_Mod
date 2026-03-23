package com.director.ui.panels;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.ui.container.ContainerDialogs;
import com.director.ui.container.ContainerPanelController;
import com.director.ui.container.ContainerPanelState;
import com.director.ui.container.ContainerToolbar;
import com.director.ui.container.ContainerTreeView;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
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
    /** 折叠把手内箭头颜色（ARGB），与容器树锁/可见性图标同一体系。 */
    private static final int HANDLE_CHEVRON_COLOR = 0xFFC0C0C0;

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
        this.groupPropertiesPanel = new GroupPropertiesPanel(controller);

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

        // 右侧把手：InvisibleButton 作命中区 + DrawList 按 icon 包围盒与 rect 中心对齐绘制，
        // 与容器树锁/可见性图标一致；避免箭头按文字 baseline 对齐导致与按钮不同轴。
        if (ImGui.beginChild("PropertiesHandleChild", PROPERTIES_HANDLE_WIDTH, availableHeight, true)) {
            float contentW = ImGui.getContentRegionAvailX();
            float contentH = ImGui.getContentRegionAvailY();
            float btnSize = Math.min(PROPERTIES_HANDLE_WIDTH - 4.0f, Math.min(contentW, contentH));
            float offX = Math.max(0, (contentW - btnSize) * 0.5f);
            float offY = Math.max(0, (contentH - btnSize) * 0.5f);
            ImGui.setCursorPos(offX, offY);

            if (ImGui.invisibleButton("##expandProperties", btnSize, btnSize)) {
                togglePropertiesCollapsed();
            }
            if (ImGui.isItemHovered()) {
                java.util.Set<Container> sel = containerManager.getSelectedContainersView();
                int n = sel != null ? sel.size() : 0;
                ImGui.beginTooltip();
                if (n == 1) {
                    Container selected = containerManager.getSelectedContainer();
                    if (selected != null) {
                        ImGui.text("选中: " + selected.getName());
                        ImGui.text("类型: " + selected.getType().toString());
                        ImGui.separator();
                    }
                } else if (n > 1) {
                    ImGui.text("选中 " + n + " 个容器");
                    ImGui.separator();
                }
                ImGui.text("展开属性面板");
                ImGui.text("快捷键：P");
                ImGui.endTooltip();
            }
            imgui.ImVec2 rMin = ImGui.getItemRectMin();
            imgui.ImVec2 rMax = ImGui.getItemRectMax();
            // 1px 浅色边框，与全局按钮一致（此按钮无填充，仅边框+箭头）
            ImGui.getWindowDrawList().addRect(rMin.x, rMin.y, rMax.x, rMax.y, ImGui.getColorU32(ImGuiCol.Border), ImGui.getStyle().getFrameRounding(), 0, 1.0f);
            // 按 icon 包围盒与 rect 中心对齐，不用 baseline
            imgui.ImVec2 sz = ImGui.calcTextSize(Icons.CHEVRON_RIGHT);
            float ix = rMin.x + (rMax.x - rMin.x - sz.x) * 0.5f;
            float iy = rMin.y + (rMax.y - rMin.y - sz.y) * 0.5f;
            iy += 1f; // ▶ 字形视觉重心偏上，optical offset 使与图标同轴
            int a = (HANDLE_CHEVRON_COLOR >> 24) & 0xFF;
            int r = (HANDLE_CHEVRON_COLOR >> 16) & 0xFF, g = (HANDLE_CHEVRON_COLOR >> 8) & 0xFF, b = HANDLE_CHEVRON_COLOR & 0xFF;
            int abgr = (a << 24) | (b << 16) | (g << 8) | r;
            ImGui.getWindowDrawList().addText(ix, iy, abgr, Icons.CHEVRON_RIGHT);
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
        // ✅ 顶部"属性"标题和折叠按钮；折叠钮与把手展开钮同一套：InvisibleButton + DrawList 按 icon 包围盒居中
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("属性");
        ImGui.sameLine();
        float lineH = ImGui.getFrameHeight();
        float cbh = 18f;
        ImGui.setCursorPosY(ImGui.getCursorPosY() + Math.max(0, (lineH - cbh) * 0.5f));
        if (ImGui.invisibleButton("##collapsePropertiesTop", cbh, cbh)) {
            togglePropertiesCollapsed();
            return;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("折叠属性面板\n快捷键：P");
        }
        imgui.ImVec2 cMin = ImGui.getItemRectMin();
        imgui.ImVec2 cMax = ImGui.getItemRectMax();
        // 背景框，像按钮；使用主题 Button/ButtonHovered/ButtonActive + 1px 浅色边框（与全局 frame 一致）
        int bgCol = ImGui.getColorU32(ImGui.isItemActive() ? ImGuiCol.ButtonActive : ImGui.isItemHovered() ? ImGuiCol.ButtonHovered : ImGuiCol.Button);
        float rounding = ImGui.getStyle().getFrameRounding();
        ImGui.getWindowDrawList().addRectFilled(cMin.x, cMin.y, cMax.x, cMax.y, bgCol, rounding);
        ImGui.getWindowDrawList().addRect(cMin.x, cMin.y, cMax.x, cMax.y, ImGui.getColorU32(ImGuiCol.Border), rounding, 0, 1.0f);
        // 图标
        imgui.ImVec2 cSz = ImGui.calcTextSize(Icons.CHEVRON_LEFT);
        float cIx = cMin.x + (cMax.x - cMin.x - cSz.x) * 0.5f;
        float cIy = cMin.y + (cMax.y - cMin.y - cSz.y) * 0.5f;
        cIy += 1f; // ◀ 与 ▶ 同族 optical offset
        int ca = (HANDLE_CHEVRON_COLOR >> 24) & 0xFF;
        int cr = (HANDLE_CHEVRON_COLOR >> 16) & 0xFF, cg = (HANDLE_CHEVRON_COLOR >> 8) & 0xFF, cb = HANDLE_CHEVRON_COLOR & 0xFF;
        int cabgr = (ca << 24) | (cb << 16) | (cg << 8) | cr;
        ImGui.getWindowDrawList().addText(cIx, cIy, cabgr, Icons.CHEVRON_LEFT);

        ImGui.separator();

        java.util.Set<Container> selSet = containerManager.getSelectedContainersView();
        int selCount = selSet != null ? selSet.size() : 0;
        if (selCount == 0) {
            ImGui.textDisabled("(未选择容器或组)");
            ImGui.spacing();
            ImGui.textWrapped("在左侧容器树中选择一个条目以查看和编辑属性。");
            return;
        }
        if (selCount > 1) {
            ImGui.textDisabled("已选择 " + selCount + " 个容器");
            ImGui.spacing();
            ImGui.textWrapped("多选时仅显示主选容器属性，或使用右键菜单进行批量操作。");
            Container primary = containerManager.getSelectedContainer();
            if (primary != null) {
                ImGui.separator();
                if (primary.getType() == ContainerType.GROUP) {
                    groupPropertiesPanel.render(primary);
                } else {
                    propertiesPanel.render(primary);
                }
            }
            return;
        }

        Container selected = containerManager.getSelectedContainer();
        if (selected == null) {
            ImGui.textDisabled("(未选择容器或组)");
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