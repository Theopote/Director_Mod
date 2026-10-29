package com.director.ui.panels;

import com.director.core.container.*;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.container.ContainerColorPicker;
import com.director.ui.container.ContainerPanelController;
import com.director.ui.util.UIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.UUID;

/**
 * 【重构版】
 * 容器属性面板 —— 仅负责编辑容器自身属性。
 * （不再操作时间线，不再持有命令历史）
 * <p>
 * 本面板是「纯 UI」、无业务逻辑，所有修改都通过 Controller 执行命令。
 */
public class ContainerPropertiesPanel {

    private final ImString nameBuffer = UIUtils.createInputBuffer(UIUtils.DEFAULT_NAME_MAX_CHARACTERS);
    private final ImString descBuffer = UIUtils.createInputBuffer(UIUtils.DEFAULT_DESCRIPTION_MAX_CHARACTERS);
    private final ImInt typeIndex = new ImInt(0);
    private final ImInt animModeIndex = new ImInt(0);
    private final float[] tagColorBuffer = new float[4];
    private final ImString easingBuffer = new ImString(64);
    private final ImString orderingBuffer = new ImString(64);

    private static final ContainerType[] AVAILABLE_TYPES;

    static {
        ContainerType[] allTypes = ContainerType.values();
        java.util.List<ContainerType> available = new java.util.ArrayList<>();
        for (ContainerType type : allTypes) {
            if (type != ContainerType.GROUP) {
                available.add(type);
            }
        }
        AVAILABLE_TYPES = available.toArray(new ContainerType[0]);
    }

    private UUID currentEditingContainerId = null;

    private final ContainerPanelController controller;

    public ContainerPropertiesPanel(ContainerPanelController controller) {
        this.controller = controller;
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    private static String[] buildTypeNames() {
        String[] names = new String[AVAILABLE_TYPES.length];
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            names[i] = AVAILABLE_TYPES[i].getDisplayName();
        }
        return names;
    }

    private static String[] buildAnimationModeNames() {
        AnimationMode[] modes = AnimationMode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            names[i] = modes[i].getDisplayName();
        }
        return names;
    }

    public void render(Container container) {
        if (container == null) {
            renderEmptyState();
            return;
        }

        syncBuffersIfNeeded(container);

        ImGui.beginChild("ContainerProperties", 0, 0, false);

        renderContainerInfo(container);

        renderBasicProperties(container);
        ImGui.spacing();

        renderAnimationPreset(container);
        ImGui.spacing();

        renderVisualProperties(container);
        ImGui.spacing();

        renderReadonlyInfo(container);

        ImGui.endChild();
    }

    private void syncBuffersIfNeeded(Container container) {
        if (currentEditingContainerId != null &&
            currentEditingContainerId.equals(container.getId())) {
            return;
        }

        currentEditingContainerId = container.getId();

        nameBuffer.set(container.getName());

        Object desc = container.getMetadata("description", "");
        descBuffer.set(desc != null ? desc.toString() : "");

        ContainerType currentType = container.getType();
        int currentTypeIndex = 0;
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            if (AVAILABLE_TYPES[i] == currentType) {
                currentTypeIndex = i;
                break;
            }
        }
        typeIndex.set(currentTypeIndex);

        Integer tagColor = (Integer) container.getMetadata("tagColor", null);
        int defaultColor = tagColor != null ? tagColor : 0xFFFFFFFF;
        float[] colorArray = ContainerColorPicker.colorIntToFloat(defaultColor);
        System.arraycopy(colorArray, 0, tagColorBuffer, 0, 4);

        ContainerAnimationPreset syncPreset = container.getDefaultAnimationPreset();
        animModeIndex.set(syncPreset.getMode().ordinal());
        easingBuffer.set(syncPreset.getEasing() != null ? syncPreset.getEasing() : "");
        orderingBuffer.set(syncPreset.getOrdering() != null ? syncPreset.getOrdering() : "");
    }

    private void renderEmptyState() {
        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedContainer.EMPTY_NO_SELECTION));
        ImGui.spacing();
        ImGui.textWrapped(tr(DirectorLang.Properties.EmbeddedContainer.EMPTY_HINT));
    }

    private void renderContainerInfo(Container container) {
        ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f,
                container.getType().getIcon() + " " + container.getName());
        ImGui.sameLine();
        ImGui.textDisabled("(ID: " + container.getId().toString().substring(0, 8) + ")");
        ImGui.spacing();
    }

    private void renderBasicProperties(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once);
        if (!ImGui.collapsingHeader(tr(DirectorLang.Properties.EmbeddedContainer.BASIC_PROPERTIES))) return;

        boolean nameEnterPressed = ImGui.inputText(tr(DirectorLang.Properties.EmbeddedContainer.NAME), nameBuffer,
                ImGuiInputTextFlags.AutoSelectAll | ImGuiInputTextFlags.EnterReturnsTrue);
        if (nameEnterPressed || ImGui.isItemDeactivatedAfterEdit()) {
            String newName = UIUtils.cleanString(nameBuffer.get());
            if (!newName.isEmpty() && !newName.equals(container.getName())) {
                controller.renameContainer(container.getId(), newName);
            }
        }

        int currentTypeIndex = 0;
        ContainerType currentType = container.getType();
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            if (AVAILABLE_TYPES[i] == currentType) {
                currentTypeIndex = i;
                break;
            }
        }
        typeIndex.set(currentTypeIndex);

        if (ImGui.combo(tr(DirectorLang.Properties.EmbeddedContainer.TYPE), typeIndex, buildTypeNames())) {
            ContainerType newType = AVAILABLE_TYPES[typeIndex.get()];
            if (newType != currentType) {
                controller.setContainerType(container.getId(), newType);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.EmbeddedContainer.TYPE_TOOLTIP));
        }

        if (ImGui.inputTextMultiline(tr(DirectorLang.Properties.EmbeddedContainer.DESCRIPTION), descBuffer, 0, 55)) {
            controller.setMetadata(container.getId(), "description", UIUtils.cleanString(descBuffer.get()));
        }
    }

    private void renderAnimationPreset(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once);
        if (!ImGui.collapsingHeader(tr(DirectorLang.Properties.EmbeddedContainer.ANIMATION_PRESET))) return;

        ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
        AnimationMode[] modes = AnimationMode.values();

        animModeIndex.set(preset.getMode().ordinal());
        if (ImGui.combo(tr(DirectorLang.Properties.EmbeddedContainer.APPEAR_MODE), animModeIndex, buildAnimationModeNames())) {
            AnimationMode newMode = modes[animModeIndex.get()];
            controller.setPresetMode(container.getId(), newMode);
        }

        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
        ImGui.textWrapped(preset.getMode().getDescription());
        ImGui.popStyleColor();

        ImGui.spacing();

        float dur = preset.getSuggestedDuration();
        float[] durBuf = { dur };
        if (ImGui.sliderFloat(tr(DirectorLang.Properties.EmbeddedContainer.SUGGESTED_DURATION), durBuf,
                ContainerAnimationPreset.MIN_SUGGESTED_DURATION,
                ContainerAnimationPreset.MAX_SUGGESTED_DURATION, "%.2fs")) {
            controller.setPresetDuration(container.getId(), durBuf[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.EmbeddedContainer.SUGGESTED_DURATION_TOOLTIP));
        }

        float[] delayBuf = { preset.getDelay() };
        if (ImGui.sliderFloat(tr(DirectorLang.Properties.EmbeddedContainer.DEFAULT_DELAY), delayBuf, 0f, 10f, "%.1fs")) {
            controller.setPresetDelay(container.getId(), delayBuf[0]);
        }

        if (ImGui.inputText(tr(DirectorLang.Properties.EmbeddedContainer.EASING_CURVE), easingBuffer)) {
            controller.setPresetEasing(container.getId(), easingBuffer.get());
        }

        if (ImGui.inputText(tr(DirectorLang.Properties.EmbeddedContainer.PLAY_ORDERING), orderingBuffer)) {
            controller.setPresetOrdering(container.getId(), orderingBuffer.get());
        }

        ImGui.spacing();
        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedContainer.PRESET_HINT));
    }

    private void renderVisualProperties(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once);
        if (!ImGui.collapsingHeader(tr(DirectorLang.Properties.EmbeddedContainer.CONTAINER_PROPERTIES))) return;

        ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.UI_TAG_COLOR));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.EmbeddedContainer.UI_TAG_COLOR_TOOLTIP));
        }

        String tagColorLabel = tr(DirectorLang.Properties.EmbeddedContainer.TAG_COLOR_PICKER);
        if (ContainerColorPicker.renderColorPicker(tagColorLabel, tagColorBuffer)) {
            int newTagColor = ContainerColorPicker.colorFloatToInt(tagColorBuffer);
            controller.setMetadata(container.getId(), "tagColor", newTagColor);
        }

        ImGui.sameLine();
        if (ImGui.button(tr(DirectorLang.Properties.EmbeddedContainer.CLEAR) + "##clearTagColor")) {
            controller.setMetadata(container.getId(), "tagColor", null);
            float[] defaultColorArray = ContainerColorPicker.colorIntToFloat(0xFFFFFFFF);
            System.arraycopy(defaultColorArray, 0, tagColorBuffer, 0, 4);
            currentEditingContainerId = null;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.EmbeddedContainer.CLEAR_TAG_TOOLTIP));
        }

        ImGui.separator();

        boolean locked = container.isLocked();
        if (ImGui.checkbox(tr(DirectorLang.Properties.EmbeddedContainer.LOCKED), locked)) {
            controller.setContainerLocked(container.getId(), !locked);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.EmbeddedContainer.LOCKED_TOOLTIP));
        }

        ImGui.sameLine();

        ContainerState currentState = container.getState();
        boolean isVisible = currentState == ContainerState.VISIBLE;
        if (ImGui.checkbox(tr(DirectorLang.Properties.EmbeddedContainer.VISIBLE), isVisible)) {
            controller.toggleContainerState(container.getId());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isVisible
                ? tr(DirectorLang.Properties.EmbeddedContainer.HIDE_TOOLTIP)
                : tr(DirectorLang.Properties.EmbeddedContainer.SHOW_TOOLTIP));
        }
    }

    private void renderReadonlyInfo(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once);
        if (!ImGui.collapsingHeader(tr(DirectorLang.Properties.EmbeddedContainer.STATS))) return;

        ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.BLOCK_COUNT, container.getBlockCount()));
        ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.CHILD_COUNT, container.getChildren().size()));
        ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.TOTAL_BLOCKS, container.getTotalBlockCount()));

        Container.BoundingBox bbox = container.getBoundingBox();
        ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.SIZE_VOLUME,
                bbox.getWidth(), bbox.getHeight(), bbox.getDepth(), bbox.getVolume()));

        if (container.getCenter() != null) {
            ImGui.text(tr(DirectorLang.Properties.EmbeddedContainer.CENTER, container.getCenter()));
        }

        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedContainer.CREATED_AT,
                new java.util.Date(container.getCreatedTime())));
        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedContainer.MODIFIED_AT,
                new java.util.Date(container.getModifiedTime())));
    }
}
