package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;

/**
 * 顶部工具栏组件，负责渲染所有按钮并响应用户操作。
 */
public class ContainerToolbar {

    private final ContainerPanelController controller;

    public ContainerToolbar(ContainerPanelController controller) {
        this.controller = controller;
    }

    public void render() {
        renderCreationButtons();
        renderContainerControlButtons();
        renderSelectionInfo();
    }

    private boolean showText() {
        try {
            return controller != null && controller.getState() != null && controller.getState().isShowToolbarText();
        } catch (Exception ignored) {
            return true;
        }
    }

    /** 估算按钮宽度，用于换行判断（与 ToolbarPanel.getToolButtonWidth 逻辑一致）。 */
    private float estimateButtonWidth(String icon, String text) {
        if (showText()) {
            float pad = ImGui.getStyle().getFramePadding().x * 2f;
            return ImGui.calcTextSize(icon + " " + text).x + pad;
        }
        return UITheme.Spacing.BUTTON_HEIGHT;
    }

    /**
     * 先 sameLine，若剩余宽度放不下下一按钮则 newLine。
     * 与 ToolbarPanel 的 renderToolGroupWrapped / 组内换行 逻辑一致。
     */
    private void sameLineOrWrap(String icon, String text) {
        ImGui.sameLine();
        float spx = ImGui.getStyle().getItemSpacing().x;
        if (estimateButtonWidth(icon, text) + spx > ImGui.getContentRegionAvailX()) {
            ImGui.newLine();
        }
    }

    private boolean toolbarButton(String id, String icon, String text, String tooltip) {
        boolean showText = showText();
        String label = showText ? (icon + " " + text + "##" + id) : (icon + "##" + id);
        float w = showText ? 0 : UITheme.Spacing.BUTTON_HEIGHT;
        boolean clicked = ImGui.button(label, w, UITheme.Spacing.BUTTON_HEIGHT);
        if (ImGui.isItemHovered() && tooltip != null && !tooltip.isBlank()) {
            ImGui.setTooltip(tooltip);
        }
        return clicked;
    }

    private void renderCreationButtons() {
        if (toolbarButton("container_create", Icons.ADD, "新建容器", "创建一个新的容器")) {
            controller.createNewContainer();
        }

        sameLineOrWrap(Icons.FOLDER, "新建分组");
        if (toolbarButton("group_create", Icons.FOLDER, "新建分组", "创建一个新的分组（仅用于组织容器）")) {
            controller.createNewGroupContainer();
        }

        sameLineOrWrap(Icons.CONTAINER, "生成容器");
        if (toolbarButton("container_generate", Icons.CONTAINER, "生成容器", "用当前 Minecraft 选区生成容器")) {
            controller.generateContainerFromSelection();
        }

        sameLineOrWrap(Icons.ERASER, "生成拆除容器");
        if (toolbarButton("container_generate_demolition", Icons.ERASER, "生成拆除容器", "用当前 Minecraft 选区生成拆除容器")) {
            controller.generateDemolitionContainerFromSelection();
        }
    }

    private void renderContainerControlButtons() {
        Container selectedContainer = controller.getSelectedContainer();
        boolean hasSelection = selectedContainer != null;
        boolean isGroup = hasSelection && selectedContainer.getType() == ContainerType.GROUP;

        boolean hasMinecraftSelection = ContainerSelectionUtil.hasMinecraftSelection();

        sameLineOrWrap(Icons.DOWNLOAD, "收纳选区");
        boolean canIntake = hasSelection && !isGroup && hasMinecraftSelection;
        if (hasSelection && selectedContainer.isLocked()) {
            canIntake = false;
        }
        if (!canIntake) ImGui.beginDisabled();
        if (toolbarButton("intake_selection", Icons.DOWNLOAD, "收纳选区", "将当前选区添加到选中的容器")) {
            controller.intakeCurrentSelection();
        }
        if (!canIntake) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            if (!hasSelection) {
                ImGui.setTooltip("请先选择一个容器");
            } else if (isGroup) {
                ImGui.setTooltip("组仅用于分类，无法收纳方块");
            } else if (!hasMinecraftSelection) {
                ImGui.setTooltip("请在Minecraft中选择方块");
            } else {
                ImGui.setTooltip("将当前选区添加到选中的容器");
            }
        }

        boolean intakeEnabled = hasSelection && !isGroup && selectedContainer.isIntakeEnabled() && !selectedContainer.isLocked();
        String intakeIcon = intakeEnabled ? Icons.PAUSE : Icons.PLAY;
        String intakeLabel = intakeEnabled ? "停止接收" : "实时接收";
        sameLineOrWrap(intakeIcon, intakeLabel);
        if (!hasSelection || isGroup || selectedContainer.isLocked()) ImGui.beginDisabled();
        if (toolbarButton("toggle_intake", intakeIcon, intakeLabel,
            intakeEnabled ? "停止实时接收选中的方块" : "开启实时接收模式：每次选中方块时自动收纳到此容器")) {
            if (selectedContainer != null) {
                controller.toggleIntake(selectedContainer.getId());
            }
        }
        if (!hasSelection || isGroup || selectedContainer.isLocked()) ImGui.endDisabled();
        // tooltip 已在 toolbarButton() 中设置；这里保留原逻辑的特殊提示（覆盖更精准）
        if (ImGui.isItemHovered()) {
            if (!hasSelection) {
                ImGui.setTooltip("请先选择一个容器");
            } else if (isGroup) {
                ImGui.setTooltip("组无法开启实时接收");
            }
        }

		// 顶部不再提供“释放”按钮，释放操作保留在右键菜单中
    }

    private void renderSelectionInfo() {
        Container selected = controller.getSelectedContainer();
        if (selected != null) {
            ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f,
                "选中: " + selected.getName() + " (" + selected.getType().getDisplayName() + ")");
        } else {
            ImGui.textDisabled("(未选择容器)");
        }
    }
}