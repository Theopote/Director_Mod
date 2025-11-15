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
        ImGui.sameLine();
        ImGui.textDisabled(String.format("分割: %.0f%%", state.getLeftPanelWidthRatio() * 100));
        ImGui.sameLine();
        if (ImGui.smallButton("重置分割")) {
            state.setLeftPanelWidthRatio(0.5f);
        }
        ImGui.sameLine();
        renderHeaderControls();
    }

    private void renderHeaderControls() {
        boolean canUndo = controller.canUndo();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.smallButton(Icons.UNDO + " 撤销")) {
            controller.undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();
        boolean canRedo = controller.canRedo();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.smallButton(Icons.REDO + " 重做")) {
            controller.redo();
        }
        if (!canRedo) ImGui.endDisabled();

        ImGui.sameLine();
        String expandLabel = state.isExpandAll() ? "折叠全部" : "展开全部";
        if (ImGui.smallButton(expandLabel)) {
            controller.toggleExpandAll();
        }
    }

    private void renderResizableLayout() {
        float windowWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY();

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

    private void renderVerticalSplitter(float height, float windowWidth) {
        ImGui.invisibleButton(SPLITTER_ID, SPLITTER_WIDTH, height);
        handleSplitterDrag(windowWidth);

        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }

        drawSplitterVisuals();
    }

    private void handleSplitterDrag(float windowWidth) {
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            float mouseDeltaX = ImGui.getIO().getMouseDeltaX();
            float ratio = state.getLeftPanelWidthRatio() + mouseDeltaX / windowWidth;
            ratio = Math.max(MIN_PANEL_RATIO, Math.min(MAX_PANEL_RATIO, ratio));
            state.setLeftPanelWidthRatio(ratio);
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
        if (!ImGui.isWindowFocused()) {
            return;
        }

        boolean ctrl = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);

        if (ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE)) {
            if (!containerManager.getSelectedContainersView().isEmpty()) {
                ImGui.openPopup("ConfirmDeletion");
            }
        }

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