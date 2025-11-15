package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.*;
import com.chronoblocks.ui.container.ContainerPanelController;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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

    // ---------------- UI 缓冲区 ----------------
    private final ImString nameBuffer = new ImString(256);
    private final ImString descBuffer = new ImString(512);
    private final ImInt typeIndex = new ImInt(0);
    private final ImInt animModeIndex = new ImInt(0);
    private final float[] colorBuffer = new float[4];
    private final float[] opacityBuffer = new float[1];

    // 缓存当前正在编辑的容器
    private UUID currentEditingContainerId = null;

    // 动画模式名称数组（静态缓存）
    private static final String[] ANIM_MODE_NAMES;

    static {
        AnimationMode[] modes = AnimationMode.values();
        ANIM_MODE_NAMES = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            ANIM_MODE_NAMES[i] = modes[i].getDisplayName();
        }
    }

    private final ContainerPanelController controller;

    public ContainerPropertiesPanel(ContainerPanelController controller) {
        this.controller = controller;
    }

    // ---------------- UI 入口 ----------------
    public void render(Container container) {
        if (container == null) {
            renderEmptyState();
            return;
        }

        syncBuffersIfNeeded(container);

        renderHeader(container);
        ImGui.separator();

        ImGui.beginChild("ContainerProperties", 0, 0, false);

        renderBasicProperties(container);
        ImGui.separator();

        renderAnimationPreset(container);
        ImGui.separator();

        renderVisualProperties(container);
        ImGui.separator();

        renderReadonlyInfo(container);

        ImGui.endChild();
    }

    // ----------------------------------------------------------
    // 缓冲区同步（只在容器切换时同步）
    // ----------------------------------------------------------
    private void syncBuffersIfNeeded(Container container) {
        if (currentEditingContainerId != null &&
            currentEditingContainerId.equals(container.getId())) {
            return; // buffer 保持用户输入
        }

        currentEditingContainerId = container.getId();

        nameBuffer.set(container.getName());

        Object desc = container.getMetadata("description", "");
        descBuffer.set(desc != null ? desc.toString() : "");

        typeIndex.set(container.getType().ordinal());

        // color
        int c = container.getColor();
        colorBuffer[0] = ( c        & 0xFF) / 255.0f; // R
        colorBuffer[1] = ((c >> 8 ) & 0xFF) / 255.0f; // G
        colorBuffer[2] = ((c >> 16) & 0xFF) / 255.0f; // B
        colorBuffer[3] = ((c >> 24) & 0xFF) / 255.0f; // A

        opacityBuffer[0] = container.getOpacity();

        // 动画 preset
        animModeIndex.set(container.getDefaultAnimationPreset().getMode().ordinal());
    }

    // ----------------------------------------------------------
    // UI：空状态
    // ----------------------------------------------------------
    private void renderEmptyState() {
        ImGui.textDisabled("(未选择容器)");
        ImGui.spacing();
        ImGui.textWrapped("在左侧容器树中选择一个容器以查看和编辑其属性。");
    }

    // ----------------------------------------------------------
    // UI：标题
    // ----------------------------------------------------------
    private void renderHeader(Container container) {
        ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f,
                container.getType().getIcon() + " 容器属性");
        ImGui.sameLine();
        ImGui.textDisabled("(ID: " + container.getId().toString().substring(0, 8) + ")");
    }

    // ----------------------------------------------------------
    // UI：基本属性
    // ----------------------------------------------------------
    private void renderBasicProperties(Container container) {
        if (!ImGui.collapsingHeader("基本属性")) return;

        // 名称
        if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.AutoSelectAll)) {
            String newName = nameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(container.getName())) {
                controller.renameContainer(container.getId(), newName);
            }
        }

        // 类型（建议只读）
        ImGui.textDisabled("类型: " + container.getType().getDisplayName());

        // 描述（用户自由填写）
        if (ImGui.inputTextMultiline("描述", descBuffer, 0, 70)) {
            controller.setMetadata(container.getId(), "description", descBuffer.get());
        }
    }

    // ----------------------------------------------------------
    // UI：动画默认预设
    // ----------------------------------------------------------
    private void renderAnimationPreset(Container container) {
        if (!ImGui.collapsingHeader("默认动画预设")) return;

        ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
        AnimationMode[] modes = AnimationMode.values();

        // Mode
        animModeIndex.set(preset.getMode().ordinal());
        if (ImGui.combo("出现方式", animModeIndex, ANIM_MODE_NAMES)) {
            AnimationMode newMode = modes[animModeIndex.get()];
            controller.setPresetMode(container.getId(), newMode);
        }

        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
        ImGui.textWrapped(preset.getMode().getDescription());
        ImGui.popStyleColor();

        ImGui.spacing();

        // Duration
        float dur = preset.getSuggestedDuration();
        float[] durBuf = { dur };
        if (ImGui.sliderFloat("建议时长 (秒)", durBuf, 0.1f, 60f, "%.1fs")) {
            controller.setPresetDuration(container.getId(), durBuf[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("新建时间线片段时的默认时长，可在片段属性中单独调整");
        }

        // Delay
        float[] delayBuf = { preset.getDelay() };
        if (ImGui.sliderFloat("默认延迟", delayBuf, 0f, 10f, "%.1fs")) {
            controller.setPresetDelay(container.getId(), delayBuf[0]);
        }

        // Easing
        ImString easingBuf = new ImString(preset.getEasing(), 64);
        if (ImGui.inputText("Easing 曲线", easingBuf)) {
            controller.setPresetEasing(container.getId(), easingBuf.get());
        }

        // 播放排序
        ImString orderingBuf = new ImString(preset.getOrdering(), 64);
        if (ImGui.inputText("播放排序", orderingBuf)) {
            controller.setPresetOrdering(container.getId(), orderingBuf.get());
        }

        ImGui.spacing();
        ImGui.textDisabled("提示：这些仅是创建时间线片段时的默认参数。");
    }

    // ----------------------------------------------------------
    // UI：外观属性
    // ----------------------------------------------------------
    private void renderVisualProperties(Container container) {
        if (!ImGui.collapsingHeader("视觉属性")) return;

        // 容器状态（显示/隐藏）
        ContainerState currentState = container.getState();
        boolean isHidden = currentState == ContainerState.HIDDEN;
        
        ImGui.text("容器状态:");
        ImGui.sameLine();
        String stateText = isHidden ? "隐藏" : "可见";
        UITheme.label(stateText, isHidden ? 
            UITheme.DarkTheme.TEXT_DISABLED : UITheme.DarkTheme.TEXT_SELECTED);
        
        ImGui.sameLine();
        String toggleText = isHidden ? "显示容器" : "隐藏容器";
        if (ImGui.button(toggleText)) {
            controller.toggleContainerState(container.getId());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isHidden ? 
                "显示容器中的方块（方块将在 Minecraft 世界中可见）" :
                "隐藏容器中的方块（方块将在 Minecraft 世界中不可见，看起来像被清除）");
        }

        ImGui.separator();

        // 不透明度
        if (ImGui.sliderFloat("不透明度", opacityBuffer, 0f, 1f, "%.2f")) {
            controller.setContainerOpacity(container.getId(), opacityBuffer[0]);
        }

        // 颜色
        if (ImGui.colorEdit4("标记颜色", colorBuffer)) {
            int newColor =
                    ((int)(colorBuffer[3] * 255) << 24) |
                    ((int)(colorBuffer[2] * 255) << 16) |
                    ((int)(colorBuffer[1] * 255) << 8)  |
                    ((int)(colorBuffer[0] * 255));

            controller.setContainerColor(container.getId(), newColor);
        }

        ImGui.spacing();

        // 锁定
        boolean locked = container.isLocked();
        if (ImGui.checkbox("锁定", locked)) {
            controller.setContainerLocked(container.getId(), !locked);
        }
    }

    // ----------------------------------------------------------
    // UI：只读信息（不需要可编辑）
    // ----------------------------------------------------------
    private void renderReadonlyInfo(Container container) {
        if (!ImGui.collapsingHeader("统计信息")) return;

        ImGui.text("方块数: " + container.getBlockCount());
        ImGui.text("子容器: " + container.getChildren().size());
        ImGui.text("总方块数: " + container.getTotalBlockCount());

        Container.BoundingBox bbox = container.getBoundingBox();
        ImGui.text(String.format("大小: %dx%dx%d (体积 %d)",
                bbox.getWidth(), bbox.getHeight(), bbox.getDepth(), bbox.getVolume()));

        if (container.getCenter() != null) {
            ImGui.text("中心: " + container.getCenter());
        }

        ImGui.textDisabled("创建时间: " + new java.util.Date(container.getCreatedTime()));
        ImGui.textDisabled("修改时间: " + new java.util.Date(container.getModifiedTime()));
    }
}