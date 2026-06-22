package com.director.ui.container;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.container.ContainerTimelineService;
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

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
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
        String createContainer = tr(DirectorLang.ContainerPanel.Toolbar.CREATE_CONTAINER);
        if (toolbarButton("container_create", Icons.ADD, createContainer,
                tr(DirectorLang.ContainerPanel.Toolbar.CREATE_CONTAINER_TOOLTIP))) {
            controller.createNewContainer();
        }

        String createGroup = tr(DirectorLang.ContainerPanel.Toolbar.CREATE_GROUP);
        sameLineOrWrap(Icons.FOLDER, createGroup);
        if (toolbarButton("group_create", Icons.FOLDER, createGroup,
                tr(DirectorLang.ContainerPanel.Toolbar.CREATE_GROUP_TOOLTIP))) {
            controller.createNewGroupContainer();
        }

        String generateContainer = tr(DirectorLang.ContainerPanel.Toolbar.GENERATE_CONTAINER);
        sameLineOrWrap(Icons.CONTAINER, generateContainer);
        if (toolbarButton("container_generate", Icons.CONTAINER, generateContainer,
                tr(DirectorLang.ContainerPanel.Toolbar.GENERATE_CONTAINER_TOOLTIP))) {
            controller.generateContainerFromSelection();
        }

        String generateDemolition = tr(DirectorLang.ContainerPanel.Toolbar.GENERATE_DEMOLITION);
        sameLineOrWrap(Icons.ERASER, generateDemolition);
        if (toolbarButton("container_generate_demolition", Icons.ERASER, generateDemolition,
                tr(DirectorLang.ContainerPanel.Toolbar.GENERATE_DEMOLITION_TOOLTIP))) {
            controller.generateDemolitionContainerFromSelection();
        }
    }

    private void renderContainerControlButtons() {
        Container selectedContainer = controller.getSelectedContainer();
        boolean hasSelection = selectedContainer != null;
        boolean isGroup = hasSelection && selectedContainer.getType() == ContainerType.GROUP;
        boolean timelineControlled = hasSelection && isTimelineControlled(selectedContainer);

        boolean hasMinecraftSelection = ContainerSelectionUtil.hasMinecraftSelection();

        String intakeSelection = tr(DirectorLang.ContainerPanel.Toolbar.INTAKE_SELECTION);
        sameLineOrWrap(Icons.DOWNLOAD, intakeSelection);
        boolean canIntake = hasSelection && !isGroup && !timelineControlled && hasMinecraftSelection;
        if (hasSelection && selectedContainer.isLocked()) {
            canIntake = false;
        }
        if (!canIntake) ImGui.beginDisabled();
        if (toolbarButton("intake_selection", Icons.DOWNLOAD, intakeSelection,
                tr(DirectorLang.ContainerPanel.Toolbar.INTAKE_SELECTION_TOOLTIP))) {
            controller.intakeCurrentSelection();
        }
        if (!canIntake) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            if (!hasSelection) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_SELECT_CONTAINER_FIRST));
            } else if (isGroup) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_GROUP_NO_INTAKE));
            } else if (timelineControlled) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_TIMELINE_NO_INTAKE));
            } else if (!hasMinecraftSelection) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_SELECT_BLOCKS_MC));
            } else {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.INTAKE_SELECTION_TOOLTIP));
            }
        }

        boolean intakeEnabled = hasSelection && !isGroup && selectedContainer.isIntakeEnabled() && !selectedContainer.isLocked();
        String intakeIcon = intakeEnabled ? Icons.PAUSE : Icons.PLAY;
        String intakeLabel = intakeEnabled
            ? tr(DirectorLang.ContainerPanel.Toolbar.STOP_INTAKE)
            : tr(DirectorLang.ContainerPanel.Toolbar.LIVE_INTAKE);
        sameLineOrWrap(intakeIcon, intakeLabel);
        if (!hasSelection || isGroup || timelineControlled || selectedContainer.isLocked()) ImGui.beginDisabled();
        if (toolbarButton("toggle_intake", intakeIcon, intakeLabel,
            intakeEnabled
                ? tr(DirectorLang.ContainerPanel.Toolbar.STOP_INTAKE_TOOLTIP)
                : tr(DirectorLang.ContainerPanel.Toolbar.LIVE_INTAKE_TOOLTIP))) {
            if (selectedContainer != null) {
                controller.toggleIntake(selectedContainer.getId());
            }
        }
        if (!hasSelection || isGroup || timelineControlled || selectedContainer.isLocked()) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            if (!hasSelection) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_SELECT_CONTAINER_FIRST));
            } else if (isGroup) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_GROUP_NO_LIVE_INTAKE));
            } else if (timelineControlled) {
                ImGui.setTooltip(tr(DirectorLang.ContainerPanel.Toolbar.TOOLTIP_TIMELINE_NO_LIVE_INTAKE));
            }
        }

		// 顶部不再提供“释放”按钮，释放操作保留在右键菜单中
    }

    private boolean isTimelineControlled(Container container) {
        return container != null
            && DirectorServices.get().containerTimeline().isContainerOnTimeline(container.getId());
    }

    private void renderSelectionInfo() {
        java.util.Set<Container> selected = controller.getSelectedContainers();
        int n = selected != null ? selected.size() : 0;
        if (n == 0) {
            ImGui.textDisabled(tr(DirectorLang.ContainerPanel.Toolbar.NO_SELECTION));
        } else if (n > 1) {
            ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f,
                tr(DirectorLang.ContainerPanel.Toolbar.SELECTED_COUNT, n));
        } else {
            Container c = controller.getSelectedContainer();
            if (c != null) {
                ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f,
                    tr(DirectorLang.ContainerPanel.Toolbar.SELECTED_SINGLE, c.getName(), c.getType().getDisplayName()));
            } else {
                ImGui.textDisabled(tr(DirectorLang.ContainerPanel.Toolbar.NO_SELECTION));
            }
        }
    }
}
