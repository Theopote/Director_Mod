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

    private void renderCreationButtons() {
        if (ImGui.button(Icons.ADD + " 新建容器", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            controller.createNewContainer();
        }

        ImGui.sameLine();
        if (ImGui.button(Icons.FOLDER + " 新建分组", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            controller.createNewGroupContainer();
        }

        ImGui.sameLine();
        if (ImGui.button(Icons.CONTAINER + " 生成容器", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            controller.generateContainerFromSelection();
        }
    }

    private void renderContainerControlButtons() {
        Container selectedContainer = controller.getSelectedContainer();
        boolean hasSelection = selectedContainer != null;
        boolean isGroup = hasSelection && selectedContainer.getType() == ContainerType.GROUP;

        boolean hasMinecraftSelection = ContainerSelectionUtil.hasMinecraftSelection();

        ImGui.sameLine();
        boolean canIntake = hasSelection && !isGroup && hasMinecraftSelection;
        if (hasSelection && selectedContainer.isLocked()) {
            canIntake = false;
        }
        if (!canIntake) ImGui.beginDisabled();
        if (ImGui.button(Icons.DOWNLOAD + " 收纳选区", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
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

        ImGui.sameLine();
        if (!hasSelection || isGroup || selectedContainer.isLocked()) ImGui.beginDisabled();
        boolean intakeEnabled = hasSelection && !isGroup && selectedContainer.isIntakeEnabled() && !selectedContainer.isLocked();
        String intakeText = intakeEnabled ? Icons.PAUSE + " 停止接收" : Icons.PLAY + " 实时接收";
        if (ImGui.button(intakeText, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (selectedContainer != null) {
                controller.toggleIntake(selectedContainer.getId());
            }
        }
        if (!hasSelection || isGroup || selectedContainer.isLocked()) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            if (!hasSelection) {
                ImGui.setTooltip("请先选择一个容器");
            } else if (isGroup) {
                ImGui.setTooltip("组无法开启实时接收");
            } else if (intakeEnabled) {
                ImGui.setTooltip("停止实时接收选中的方块");
            } else {
                ImGui.setTooltip("开启实时接收模式：每次选中方块时自动收纳到此容器");
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