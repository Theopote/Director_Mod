package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerType;
import com.director.core.container.AnimationMode;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import com.director.ui.util.UIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 容器属性适配器 v4.7 - Director Properties System
 * 
 * <p>为 Director 容器系统提供专用属性编辑器。
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>基本信息（名称、类型、ID）</li>
 *   <li>视觉属性（可见性、锁定、不透明度、颜色）</li>
 *   <li>动画模式（12种动画效果）</li>
 *   <li>边界信息（最小/最大坐标、尺寸）</li>
 *   <li>统计信息（方块数、体积、填充率）</li>
 *   <li>层级结构（父容器、子容器列表）</li>
 *   <li>元数据（创建时间、修改时间）</li>
 *   <li>快捷操作（克隆、清空、同步等）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>快捷操作 - 克隆、清空等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>颜色拾取 - 容器标记颜色</li>
 *   <li>层级展示 - 父子关系可视化</li>
 * </ul>
 * 
 * @author Director Team
 * @version 4.7
 * @since 2025-10-15
 */
public class ContainerPropertyAdapter implements IPropertyAdapter<Container> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPropertyAdapter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    // 使用持久缓冲区，避免每帧重建导致 IME 合成中断。
    private final ImString nameBuffer = UIUtils.createInputBuffer(UIUtils.DEFAULT_NAME_MAX_CHARACTERS);
    private java.util.UUID editingContainerId;

    @Override
    public boolean renderProperties(Container container) {
        if (container == null) {
            UITheme.spacingLarge();
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.NULL_CONTAINER), UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean timelineControlled = isTimelineControlled(container);
        if (timelineControlled) {
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.TIMELINE_READONLY), UITheme.DarkTheme.TEXT_DISABLED);
            ImGui.beginDisabled();
        }
        try {
            if (container.getType() == ContainerType.GROUP) {
                return renderGroupProperties(container);
            }
            return renderStandardContainerProperties(container);
        } finally {
            if (timelineControlled) {
                ImGui.endDisabled();
            }
        }
    }

    private boolean isTimelineControlled(Container container) {
        if (container == null) {
            return false;
        }
        var timelineService = DirectorServices.get().containerTimeline();
        if (timelineService.isContainerOnTimeline(container.getId())) {
            return true;
        }
        for (Container child : container.getChildren()) {
            if (isTimelineControlled(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean renderGroupProperties(Container container) {
        boolean modified = false;
        UITheme.title(Icons.FOLDER + " " + tr(DirectorLang.Properties.AdapterContainer.GROUP_TITLE));

        if (ImGui.collapsingHeader(Icons.CLIPBOARD + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_BASIC), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderNameAndTypeSection(container, false);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.VISIBLE + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_DISPLAY), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderVisibilityAndLockSection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.CHART + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_CONTENT_STATS), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderGroupContentStatistics(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.PARENT + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_HIERARCHY), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderHierarchySection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.CALENDAR + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_METADATA))) {
            ImGui.indent();
            renderMetadataSection(container);
            ImGui.unindent();
        }

        return modified;
    }

    private boolean renderStandardContainerProperties(Container container) {
        boolean modified = false;
        UITheme.title(Icons.CONTAINER + " " + tr(DirectorLang.Properties.AdapterContainer.CONTAINER_TITLE));

        if (ImGui.collapsingHeader(Icons.CLIPBOARD + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_BASIC), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderNameAndTypeSection(container, true);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.VISIBLE + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_VISUAL), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderVisibilityAndLockSection(container);

            float[] opacity = {container.getOpacity()};
            if (ImGui.sliderFloat(tr(DirectorLang.Properties.AdapterContainer.OPACITY), opacity, 0.0f, 1.0f, "%.2f")) {
                container.setOpacity(opacity[0]);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 不透明度已更新: {}", opacity[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.OPACITY_TOOLTIP));
            }

            UITheme.spacingSmall();
            UITheme.label(String.format("%.0f%%", opacity[0] * 100), UITheme.DarkTheme.TEXT_DISABLED);

            UITheme.spacingSmall();
            int color = container.getColor();
            float[] colorRGBA = new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >> 24) & 0xFF) / 255.0f
            };

            if (ImGui.colorEdit4(tr(DirectorLang.Properties.AdapterContainer.MARKER_COLOR), colorRGBA)) {
                int newColor = ((int)(colorRGBA[3] * 255) << 24) |
                               ((int)(colorRGBA[0] * 255) << 16) |
                               ((int)(colorRGBA[1] * 255) << 8) |
                               (int)(colorRGBA[2] * 255);
                container.setColor(newColor);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 颜色已更新: 0x{}", Integer.toHexString(newColor));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.MARKER_COLOR_TOOLTIP));
            }
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.ANIMATION + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_ANIMATION))) {
            ImGui.indent();
            modified |= renderAnimationSection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.RULER + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_BOUNDS))) {
            ImGui.indent();
            renderBoundingBoxSection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.CHART + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_STATS))) {
            ImGui.indent();
            renderStatisticsSection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.PARENT + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_HIERARCHY), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderHierarchySection(container);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.CALENDAR + " " + tr(DirectorLang.Properties.AdapterContainer.SECTION_METADATA))) {
            ImGui.indent();
            renderMetadataSection(container);
            ImGui.unindent();
        }

        UITheme.separator();
        modified |= renderQuickActions(container);

        return modified;
    }

    private boolean renderNameAndTypeSection(Container container, boolean allowTypeChange) {
        boolean modified = false;
        syncNameBufferIfNeeded(container);

        boolean nameEnterPressed = ImGui.inputText(tr(DirectorLang.Properties.AdapterContainer.NAME), nameBuffer,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        if (nameEnterPressed || ImGui.isItemDeactivatedAfterEdit()) {
            String cleanedName = UIUtils.cleanString(nameBuffer.get());
            if (!cleanedName.isEmpty() && !cleanedName.equals(container.getName())) {
                container.setName(cleanedName);
            }
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 容器名称已更新: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.NAME_TOOLTIP));
        }

        if (allowTypeChange) {
            ContainerType currentType = container.getType();
            java.util.List<ContainerType> availableTypes = new java.util.ArrayList<>();
            for (ContainerType type : ContainerType.values()) {
                if (type != ContainerType.GROUP) {
                    availableTypes.add(type);
                }
            }

            int currentIdx = Math.max(0, availableTypes.indexOf(currentType));

            ImInt selectedIdx = new ImInt(currentIdx);
            String[] typeNames = new String[availableTypes.size()];
            for (int i = 0; i < availableTypes.size(); i++) {
                typeNames[i] = availableTypes.get(i).toString();
            }

            if (ImGui.combo(tr(DirectorLang.Properties.AdapterContainer.CONTAINER_TYPE), selectedIdx, typeNames)) {
                container.setType(availableTypes.get(selectedIdx.get()));
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 容器类型已更新: {}", availableTypes.get(selectedIdx.get()).getDisplayName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.CONTAINER_TYPE_TOOLTIP));
            }
        } else {
            UITheme.spacingSmall();
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.TYPE_LABEL, container.getType().getDisplayName()), UITheme.DarkTheme.TEXT_DISABLED);
        }

        UITheme.spacingSmall();
        UITheme.label("UUID: " + container.getId().toString(), UITheme.DarkTheme.TEXT_DISABLED);
        return modified;
    }

    private void syncNameBufferIfNeeded(Container container) {
        if (container == null) {
            editingContainerId = null;
            return;
        }
        if (editingContainerId != null && editingContainerId.equals(container.getId())) {
            return;
        }
        editingContainerId = container.getId();
        nameBuffer.set(container.getName());
    }

    private boolean renderVisibilityAndLockSection(Container container) {
        boolean modified = false;

        // 使用容器状态（ContainerState）而不是旧的 visible 属性
        boolean isVisible = container.getState() == com.director.core.container.ContainerState.VISIBLE;
        ImBoolean visible = new ImBoolean(isVisible);
        if (ImGui.checkbox(tr(DirectorLang.Properties.AdapterContainer.VISIBLE), visible)) {
            container.toggleState();
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 容器状态已更新: {}", container.getState());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.VISIBLE_TOOLTIP));
        }

        ImBoolean locked = new ImBoolean(container.isLocked());
        if (ImGui.checkbox(tr(DirectorLang.Properties.AdapterContainer.LOCKED), locked)) {
            container.setLocked(locked.get());
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 锁定状态已更新: {}", locked.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.LOCKED_TOOLTIP));
        }

        return modified;
    }

    private void renderGroupContentStatistics(Container container) {
        List<Container> children = container.getChildren();
        long groupCount = children.stream().filter(child -> child.getType() == ContainerType.GROUP).count();
        long containerCount = children.size() - groupCount;

        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.DIRECT_CHILDREN, children.size()), UITheme.DarkTheme.TEXT);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CHILD_CONTAINERS, containerCount), UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CHILD_GROUPS, groupCount), UITheme.DarkTheme.TEXT);

        UITheme.spacingSmall();
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.DESCENDANT_COUNT, countDescendants(container)), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderHierarchySection(Container container) {
        Container parent = container.getParent();
        if (parent != null) {
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.PARENT, parent.getName()), UITheme.DarkTheme.TEXT_SELECTED);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.PARENT_JUMP_TOOLTIP));
            }
        } else {
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.PARENT_NONE), UITheme.DarkTheme.TEXT_DISABLED);
        }

        UITheme.spacingSmall();

        List<Container> children = container.getChildren();
        if (children.isEmpty()) {
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CHILDREN_NONE), UITheme.DarkTheme.TEXT_DISABLED);
        } else {
            if (ImGui.treeNode(tr(DirectorLang.Properties.AdapterContainer.CHILDREN_TREE, children.size()))) {
                for (int i = 0; i < children.size(); i++) {
                    Container child = children.get(i);
                    ImGui.pushID(i);
                    String icon = child.getType().getIcon();
                    UITheme.label(icon + " " + child.getName(), UITheme.DarkTheme.TEXT);
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.CHILD_TOOLTIP,
                            child.getType().getDisplayName(),
                            child.getBlockCount()));
                    }
                    ImGui.popID();
                }
                ImGui.treePop();
            }
        }

        UITheme.spacingSmall();
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.DEPTH, container.getDepth()), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderMetadataSection(Container container) {
        long createdTime = container.getCreatedTime();
        long modifiedTime = container.getModifiedTime();

        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CREATED_AT, DATE_FORMAT.format(new Date(createdTime))), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.MODIFIED_AT, DATE_FORMAT.format(new Date(modifiedTime))), UITheme.DarkTheme.TEXT_DISABLED);

        long ageSeconds = (System.currentTimeMillis() - createdTime) / 1000;
        long ageSinceModified = (System.currentTimeMillis() - modifiedTime) / 1000;

        UITheme.spacingSmall();
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.AGE, formatDuration(ageSeconds)), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.SINCE_MODIFIED, formatDuration(ageSinceModified)), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private boolean renderAnimationSection(Container container) {
        boolean modified = false;
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
        AnimationMode currentMode = preset != null ? preset.getMode() : AnimationMode.INSTANT;

        // ✅ 统一语义：容器预设只提供“单块动画(How)”，排序/切片/随机等属于片段的“序列行为(Who comes first)”
        // 拆除容器（DEMOLITION）默认用于擦除轨道：更合理只暴露 EXIT 类动画。
        boolean isDemolition = container.getType() == ContainerType.DEMOLITION;

        AnimationMode[] baseModes = isDemolition
                ? new AnimationMode[]{AnimationMode.INSTANT, AnimationMode.FADE_OUT, AnimationMode.EXPLODE, AnimationMode.IMPLODE, AnimationMode.SHRINK_OUT, AnimationMode.GRAVITY_FALL, AnimationMode.FLY_OUT}
                : new AnimationMode[]{AnimationMode.INSTANT, AnimationMode.FADE_IN, AnimationMode.GROW_UP, AnimationMode.ASSEMBLE, AnimationMode.FALL_DOWN, AnimationMode.RISE_UP, AnimationMode.FLY_IN, AnimationMode.POP_IN, AnimationMode.SHAKER};

        // 当前预设可能来自旧版本（例如 RANDOM/SLICE/WAVE/SPIRAL/GROW_DOWN/EXPAND），为了不“看不见”，把它临时塞回列表
        java.util.List<AnimationMode> modeList = new java.util.ArrayList<>(java.util.List.of(baseModes));
        if (currentMode != null && !modeList.contains(currentMode)) {
            modeList.addFirst(currentMode);
        }
        AnimationMode[] modes = modeList.toArray(new AnimationMode[0]);

        int currentIdx = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == currentMode) {
                currentIdx = i;
                break;
            }
        }

        ImInt selectedIdx = new ImInt(currentIdx);
        String[] modeNames = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            AnimationMode m = modes[i];
            // 旧模式提示
            if (i == 0 && m == currentMode && !java.util.List.of(baseModes).contains(m)) {
                modeNames[i] = m.getDisplayName() + tr(DirectorLang.Properties.AdapterContainer.LEGACY_MODE_SUFFIX);
            } else if (isDemolition && m == AnimationMode.ASSEMBLE) {
                modeNames[i] = tr(DirectorLang.Properties.AdapterContainer.FRAGMENT_SCATTER);
            } else {
                modeNames[i] = m.getDisplayName();
            }
        }

        if (ImGui.combo(tr(DirectorLang.Properties.AdapterContainer.DEFAULT_ANIMATION), selectedIdx, modeNames)) {
            int idx = Math.max(0, Math.min(selectedIdx.get(), modes.length - 1));
            ContainerAnimationPreset updated = preset != null ? preset.copy() : ContainerAnimationPreset.defaultPreset();
            updated.setMode(modes[idx]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 默认动画预设已更新: {}", updated.getMode());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isDemolition
                    ? tr(DirectorLang.Properties.AdapterContainer.ANIMATION_DEMOLITION_TOOLTIP)
                    : tr(DirectorLang.Properties.AdapterContainer.ANIMATION_BUILD_TOOLTIP));
        }

        UITheme.spacingSmall();
        if (preset != null) {
            UITheme.label(preset.getMode().getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        if (!java.util.List.of(baseModes).contains(currentMode)) {
            UITheme.spacingSmall();
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.LEGACY_MODE_HINT), UITheme.DarkTheme.TEXT_DISABLED);
        }

        UITheme.separator();
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.ANIMATION_PARAMS), UITheme.DarkTheme.TEXT_SELECTED);
        
        // 确保 preset 存在
        if (preset == null) {
            preset = ContainerAnimationPreset.defaultPreset();
            container.setDefaultAnimationPreset(preset);
            modified = true;
        }
        
        // 获取参数 Map（确保从实际的 preset 获取）
        Map<String, Object> params = preset.getParams();
        
        // 填入默认值，避免 NPE
        try {
            com.director.core.animation.transform.IAnimationTransformer transformer = 
                com.director.core.animation.transform.AnimationTransformerFactory.getInstance().getTransformer(currentMode);
            if (transformer != null) {
                Map<String, Object> defaultParams = transformer.getDefaultParams();
                if (defaultParams != null) {
                    defaultParams.forEach(params::putIfAbsent);
                }
                
                // 调用 Transformer 的 renderUI 方法（由每个 Transformer 自己负责渲染UI）
                try {
                    boolean paramsModified = transformer.renderUI(params);
                    if (paramsModified) {
                        modified = true;
                        // 确保参数修改后保存到容器
                        container.setDefaultAnimationPreset(preset);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Transformer.renderUI() 失败，使用默认参数渲染: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("渲染动画参数时出错: {}", e.getMessage(), e);
            imgui.ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, tr(DirectorLang.Properties.AdapterContainer.RENDER_ERROR, e.getMessage()));
        }

        UITheme.separator();

        float[] duration;
        duration = new float[]{preset.getSuggestedDuration()};
        if (ImGui.dragFloat(tr(DirectorLang.Properties.AdapterContainer.SUGGESTED_DURATION), duration, 0.1f, 0.05f, 120.0f, "%.2fs")) {
            ContainerAnimationPreset updated;
            updated = preset.copy();
            updated.setSuggestedDuration(duration[0]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.SUGGESTED_DURATION_TOOLTIP));
        }

        float[] delay;
        delay = new float[]{preset.getDelay()};
        if (ImGui.dragFloat(tr(DirectorLang.Properties.AdapterContainer.DEFAULT_DELAY), delay, 0.05f, 0.0f, 30.0f, "%.2fs")) {
            ContainerAnimationPreset updated;
            updated = preset.copy();
            updated.setDelay(delay[0]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
        }

        UITheme.spacingSmall();
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CLIP_OVERRIDE_HINT), UITheme.DarkTheme.TEXT_DISABLED);
        return modified;
    }

    private void renderBoundingBoxSection(Container container) {
        var boundingBox = container.getBoundingBox();
        BlockPos min = boundingBox.getMin();
        BlockPos max = boundingBox.getMax();
        BlockPos center = container.getCenter();

        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.MIN_POINT, formatBlockPos(min)), UITheme.DarkTheme.TEXT);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.MAX_POINT, formatBlockPos(max)), UITheme.DarkTheme.TEXT);
        if (center != null) {
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.CENTER_POINT, formatBlockPos(center)), UITheme.DarkTheme.TEXT_SELECTED);
        }

        UITheme.spacingSmall();

        int width = boundingBox.getWidth();
        int height = boundingBox.getHeight();
        int depth = boundingBox.getDepth();

        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.DIMENSIONS, width, height, depth), UITheme.DarkTheme.TEXT_SELECTED);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.DIMENSIONS_TOOLTIP));
        }
    }

    private void renderStatisticsSection(Container container) {
        int blockCount = container.getBlockCount();
        int totalBlockCount = container.getTotalBlockCount();
        int volume = container.getBoundingBox().getVolume();

        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.BLOCK_COUNT, blockCount), UITheme.DarkTheme.TEXT);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.TOTAL_BLOCKS, totalBlockCount), UITheme.DarkTheme.TEXT);
        UITheme.label(tr(DirectorLang.Properties.AdapterContainer.VOLUME, volume), UITheme.DarkTheme.TEXT);

        if (volume > 0) {
            float fillRate = (blockCount / (float) volume) * 100;
            UITheme.label(tr(DirectorLang.Properties.AdapterContainer.FILL_RATE, fillRate), UITheme.DarkTheme.TEXT_DISABLED);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.FILL_RATE_TOOLTIP));
            }
        }
    }

    private boolean renderQuickActions(Container container) {
        boolean modified = false;
        UITheme.beginGroup(tr(DirectorLang.Properties.AdapterContainer.QUICK_ACTIONS));

        if (ImGui.button(tr(DirectorLang.Properties.AdapterContainer.CLONE), 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            Container cloned = container.clone();
            LOGGER.info("[ContainerPropertyAdapter] 克隆容器: {} -> {}", container.getName(), cloned.getName());
            // TODO: 添加到容器管理器
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.CLONE_TOOLTIP));
        }

        ImGui.sameLine();

        if (ImGui.button(tr(DirectorLang.Properties.AdapterContainer.CLEAR_BLOCKS), 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            container.clearBlocks();
            modified = true;
            LOGGER.info("[ContainerPropertyAdapter] 清空容器方块: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.CLEAR_BLOCKS_TOOLTIP));
        }

        ImGui.sameLine();

        if (ImGui.button(tr(DirectorLang.Properties.AdapterContainer.SYNC_WORLD), 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            LOGGER.info("[ContainerPropertyAdapter] 同步容器到世界: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.SYNC_WORLD_TOOLTIP));
        }

        if (ImGui.button(tr(DirectorLang.Properties.AdapterContainer.REFRESH_STATS), 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 刷新统计: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.REFRESH_STATS_TOOLTIP));
        }

        ImGui.sameLine();

        if (ImGui.button(tr(DirectorLang.Properties.AdapterContainer.RESET_CENTER), 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (container.getBlockCount() > 0) {
                container.addBlocks(java.util.Collections.emptySet());
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 重置中心点: {}", container.getName());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.AdapterContainer.RESET_CENTER_TOOLTIP));
        }

        UITheme.endGroup();
        return modified;
    }

    private int countDescendants(Container container) {
        int total = 0;
        for (Container child : container.getChildren()) {
            total += 1 + countDescendants(child);
        }
        return total;
    }

    @Override
    public Class<Container> getTargetType() {
        return Container.class;
    }
    
    @Override
    public String getAdapterName() {
        return tr(DirectorLang.Properties.AdapterContainer.ADAPTER_NAME);
    }
    
    /**
     * 格式化 BlockPos 为字符串
     */
    private String formatBlockPos(BlockPos pos) {
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 格式化时长为可读字符串
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return tr(DirectorLang.Properties.AdapterContainer.DURATION_SECONDS, seconds);
        } else if (seconds < 3600) {
            return tr(DirectorLang.Properties.AdapterContainer.DURATION_MINUTES, seconds / 60);
        } else if (seconds < 86400) {
            return tr(DirectorLang.Properties.AdapterContainer.DURATION_HOURS, seconds / 3600);
        } else {
            return tr(DirectorLang.Properties.AdapterContainer.DURATION_DAYS, seconds / 86400);
        }
    }
}

