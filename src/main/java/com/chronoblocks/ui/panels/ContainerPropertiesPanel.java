package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.*;
import com.chronoblocks.ui.container.ContainerColorPicker;
import com.chronoblocks.ui.container.ContainerPanelController;
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

    // ---------------- UI 缓冲区 ----------------
    private final ImString nameBuffer = new ImString(256);
    private final ImString descBuffer = new ImString(512);
    private final ImInt typeIndex = new ImInt(0);
    private final ImInt animModeIndex = new ImInt(0);
    private final float[] tagColorBuffer = new float[4];
    
    // 容器类型选项（排除 GROUP 类型）
    private static final ContainerType[] AVAILABLE_TYPES;
    private static final String[] TYPE_NAMES;
    
    static {
        ContainerType[] allTypes = ContainerType.values();
        java.util.List<ContainerType> available = new java.util.ArrayList<>();
        for (ContainerType type : allTypes) {
            if (type != ContainerType.GROUP) {
                available.add(type);
            }
        }
        AVAILABLE_TYPES = available.toArray(new ContainerType[0]);
        TYPE_NAMES = new String[AVAILABLE_TYPES.length];
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            TYPE_NAMES[i] = AVAILABLE_TYPES[i].toString();
        }
    }

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

        // ✅ 移除外部 header，因为 ContainerPanel 已经有了"属性"标题
        // 容器信息显示在内容区域顶部
        ImGui.beginChild("ContainerProperties", 0, 0, false);
        
        // 在内容区域顶部显示容器信息（更紧凑）
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

        // 设置类型索引（从可用类型中找到当前类型的索引）
        ContainerType currentType = container.getType();
        int currentTypeIndex = 0;
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            if (AVAILABLE_TYPES[i] == currentType) {
                currentTypeIndex = i;
                break;
            }
        }
        typeIndex.set(currentTypeIndex);

        // UI 标签颜色（从 metadata 读取）
        Integer tagColor = (Integer) container.getMetadata("tagColor", null);
        int defaultColor = tagColor != null ? tagColor : 0xFFFFFFFF;
        float[] colorArray = ContainerColorPicker.colorIntToFloat(defaultColor);
        System.arraycopy(colorArray, 0, tagColorBuffer, 0, 4);

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
    // UI：容器信息（紧凑显示）
    // ----------------------------------------------------------
    private void renderContainerInfo(Container container) {
        ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f,
                container.getType().getIcon() + " " + container.getName());
        ImGui.sameLine();
        ImGui.textDisabled("(ID: " + container.getId().toString().substring(0, 8) + ")");
        ImGui.spacing();
    }

    // ----------------------------------------------------------
    // UI：基本属性
    // ----------------------------------------------------------
    private void renderBasicProperties(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once); // ✅ 默认收起（更紧凑）
        if (!ImGui.collapsingHeader("基本属性")) return;

        // 名称
        if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.AutoSelectAll)) {
            String newName = nameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(container.getName())) {
                controller.renameContainer(container.getId(), newName);
            }
        }

        // 类型（可选择）
        int currentTypeIndex = 0;
        ContainerType currentType = container.getType();
        for (int i = 0; i < AVAILABLE_TYPES.length; i++) {
            if (AVAILABLE_TYPES[i] == currentType) {
                currentTypeIndex = i;
                break;
            }
        }
        typeIndex.set(currentTypeIndex);
        
        if (ImGui.combo("类型", typeIndex, TYPE_NAMES)) {
            ContainerType newType = AVAILABLE_TYPES[typeIndex.get()];
            if (newType != currentType) {
                controller.setContainerType(container.getId(), newType);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("容器的用途分类，用于标识和组织不同类型的容器");
        }

        // 描述（用户自由填写）
        if (ImGui.inputTextMultiline("描述", descBuffer, 0, 55)) {
            controller.setMetadata(container.getId(), "description", descBuffer.get());
        }
    }

    // ----------------------------------------------------------
    // UI：动画默认预设
    // ----------------------------------------------------------
    private void renderAnimationPreset(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once); // ✅ 默认收起
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
    // UI：容器属性（非渲染相关）
    // ----------------------------------------------------------
    private void renderVisualProperties(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once); // ✅ 默认收起
        if (!ImGui.collapsingHeader("容器属性")) return;

        // UI 标签颜色（用于容器树视图中的颜色标记，不影响渲染）
        ImGui.text("UI 标签颜色:");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在容器树视图中显示的颜色标记，用于分类和识别，不影响 Minecraft 世界中的渲染效果");
        }
        
        if (ContainerColorPicker.renderColorPicker("标签颜色", tagColorBuffer)) {
            int newTagColor = ContainerColorPicker.colorFloatToInt(tagColorBuffer);
            controller.setMetadata(container.getId(), "tagColor", newTagColor);
        }

        ImGui.sameLine();
        if (ImGui.button("清除##clearTagColor")) {
            controller.setMetadata(container.getId(), "tagColor", null);
            // 重置缓冲区为白色（默认）
            float[] defaultColorArray = ContainerColorPicker.colorIntToFloat(0xFFFFFFFF);
            System.arraycopy(defaultColorArray, 0, tagColorBuffer, 0, 4);
            // 强制重新同步缓冲区（下次渲染时会从容器读取）
            currentEditingContainerId = null;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("清除 UI 标签颜色");
        }

        ImGui.separator();

        // 锁定和容器状态（同一行）
        boolean locked = container.isLocked();
        if (ImGui.checkbox("锁定", locked)) {
            controller.setContainerLocked(container.getId(), !locked);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("锁定容器后，将无法修改容器的内容和属性");
        }

        ImGui.sameLine();
        
        // 容器状态（显示/隐藏）
        ContainerState currentState = container.getState();
        boolean isVisible = currentState == ContainerState.VISIBLE;
        if (ImGui.checkbox("可见", isVisible)) {
            controller.toggleContainerState(container.getId());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isVisible ? 
                "隐藏容器中的方块（方块将在 Minecraft 世界中不可见，看起来像被清除）" :
                "显示容器中的方块（方块将在 Minecraft 世界中可见）");
        }
    }

    // ----------------------------------------------------------
    // UI：只读信息（不需要可编辑）
    // ----------------------------------------------------------
    private void renderReadonlyInfo(Container container) {
        ImGui.setNextItemOpen(false, ImGuiCond.Once); // ✅ 默认收起
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