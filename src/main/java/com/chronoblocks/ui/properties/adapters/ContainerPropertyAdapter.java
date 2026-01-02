package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerAnimationPreset;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;
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
 * 容器属性适配器 v4.7 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 容器系统提供专用属性编辑器。
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
 * @author ChronoBlocks Team
 * @version 4.7
 * @since 2025-10-15
 */
public class ContainerPropertyAdapter implements IPropertyAdapter<Container> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPropertyAdapter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean renderProperties(Container container) {
        if (container == null) {
            UITheme.spacingLarge();
            UITheme.label("容器为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        if (container.getType() == ContainerType.GROUP) {
            return renderGroupProperties(container);
        }
        return renderStandardContainerProperties(container);
    }

    private boolean renderGroupProperties(Container container) {
        boolean modified = false;
        UITheme.title(Icons.FOLDER + " 组属性编辑器");

        if (ImGui.collapsingHeader(Icons.CLIPBOARD + " 基本信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderNameAndTypeSection(container, false);
        }

        if (ImGui.collapsingHeader(Icons.VISIBLE + " 显示与控制", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderVisibilityAndLockSection(container);
        }

        if (ImGui.collapsingHeader(Icons.CHART + " 内容统计", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderGroupContentStatistics(container);
        }

        if (ImGui.collapsingHeader(Icons.PARENT + " 层级结构", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderHierarchySection(container);
        }

        if (ImGui.collapsingHeader(Icons.CALENDAR + " 元数据")) {
            renderMetadataSection(container);
        }

        return modified;
    }

    private boolean renderStandardContainerProperties(Container container) {
        boolean modified = false;
        UITheme.title(Icons.CONTAINER + " 容器属性编辑器");

        if (ImGui.collapsingHeader(Icons.CLIPBOARD + " 基本信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderNameAndTypeSection(container, true);
        }

        if (ImGui.collapsingHeader(Icons.VISIBLE + " 视觉属性", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderVisibilityAndLockSection(container);

            float[] opacity = {container.getOpacity()};
            if (ImGui.sliderFloat("不透明度", opacity, 0.0f, 1.0f, "%.2f")) {
                container.setOpacity(opacity[0]);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 不透明度已更新: {}", opacity[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器整体的透明度");
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

            if (ImGui.colorEdit4("标记颜色", colorRGBA)) {
                int newColor = ((int)(colorRGBA[3] * 255) << 24) |
                               ((int)(colorRGBA[0] * 255) << 16) |
                               ((int)(colorRGBA[1] * 255) << 8) |
                               (int)(colorRGBA[2] * 255);
                container.setColor(newColor);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 颜色已更新: 0x{}", Integer.toHexString(newColor));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器在编辑器中的标记颜色");
            }
        }

        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画模式")) {
            modified |= renderAnimationSection(container);
        }

        if (ImGui.collapsingHeader(Icons.RULER + " 边界信息")) {
            renderBoundingBoxSection(container);
        }

        if (ImGui.collapsingHeader(Icons.CHART + " 统计信息")) {
            renderStatisticsSection(container);
        }

        if (ImGui.collapsingHeader(Icons.PARENT + " 层级结构")) {
            renderHierarchySection(container);
        }

        if (ImGui.collapsingHeader(Icons.CALENDAR + " 元数据")) {
            renderMetadataSection(container);
        }

        UITheme.separator();
        modified |= renderQuickActions(container);

        return modified;
    }

    private boolean renderNameAndTypeSection(Container container, boolean allowTypeChange) {
        boolean modified = false;
        ImString nameBuffer = new ImString(container.getName(), 256);
        if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            container.setName(nameBuffer.get());
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 容器名称已更新: {}", nameBuffer.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("对象的显示名称");
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

            if (ImGui.combo("容器类型", selectedIdx, typeNames)) {
                container.setType(availableTypes.get(selectedIdx.get()));
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 容器类型已更新: {}", availableTypes.get(selectedIdx.get()).getDisplayName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的用途分类");
            }
        } else {
            UITheme.spacingSmall();
            UITheme.label("类型: " + container.getType().getDisplayName(), UITheme.DarkTheme.TEXT_DISABLED);
        }

        UITheme.spacingSmall();
        UITheme.label("UUID: " + container.getId().toString(), UITheme.DarkTheme.TEXT_DISABLED);
        return modified;
    }

    private boolean renderVisibilityAndLockSection(Container container) {
        boolean modified = false;

        // 使用容器状态（ContainerState）而不是旧的 visible 属性
        boolean isVisible = container.getState() == com.chronoblocks.core.container.ContainerState.VISIBLE;
        ImBoolean visible = new ImBoolean(isVisible);
        if (ImGui.checkbox("可见", visible)) {
            container.toggleState();
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 容器状态已更新: {}", container.getState());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制容器中的方块在 Minecraft 世界中的可见性（隐藏时方块看起来像被清除）");
        }

        ImBoolean locked = new ImBoolean(container.isLocked());
        if (ImGui.checkbox("锁定", locked)) {
            container.setLocked(locked.get());
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 锁定状态已更新: {}", locked.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("锁定后禁止编辑");
        }

        return modified;
    }

    private void renderGroupContentStatistics(Container container) {
        List<Container> children = container.getChildren();
        long groupCount = children.stream().filter(child -> child.getType() == ContainerType.GROUP).count();
        long containerCount = children.size() - groupCount;

        UITheme.label("直接子项: " + children.size(), UITheme.DarkTheme.TEXT);
        UITheme.label("子容器: " + containerCount, UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label("子组: " + groupCount, UITheme.DarkTheme.TEXT);

        UITheme.spacingSmall();
        UITheme.label("后代节点总数: " + countDescendants(container), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderHierarchySection(Container container) {
        Container parent = container.getParent();
        if (parent != null) {
            UITheme.label("父节点: " + parent.getName(), UITheme.DarkTheme.TEXT_SELECTED);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("点击跳转到父节点（未实现）");
            }
        } else {
            UITheme.label("父节点: 无（顶层）", UITheme.DarkTheme.TEXT_DISABLED);
        }

        UITheme.spacingSmall();

        List<Container> children = container.getChildren();
        if (children.isEmpty()) {
            UITheme.label("子节点: 无", UITheme.DarkTheme.TEXT_DISABLED);
        } else {
            if (ImGui.treeNode("子节点 (" + children.size() + ")")) {
                for (int i = 0; i < children.size(); i++) {
                    Container child = children.get(i);
                    ImGui.pushID(i);
                    String icon = child.getType().getIcon();
                    UITheme.label(icon + " " + child.getName(), UITheme.DarkTheme.TEXT);
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(String.format("%s - %d 方块",
                            child.getType().getDisplayName(),
                            child.getBlockCount()));
                    }
                    ImGui.popID();
                }
                ImGui.treePop();
            }
        }

        UITheme.spacingSmall();
        UITheme.label("层级深度: " + container.getDepth(), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderMetadataSection(Container container) {
        long createdTime = container.getCreatedTime();
        long modifiedTime = container.getModifiedTime();

        UITheme.label("创建时间: " + DATE_FORMAT.format(new Date(createdTime)), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.label("修改时间: " + DATE_FORMAT.format(new Date(modifiedTime)), UITheme.DarkTheme.TEXT_DISABLED);

        long ageSeconds = (System.currentTimeMillis() - createdTime) / 1000;
        long ageSinceModified = (System.currentTimeMillis() - modifiedTime) / 1000;

        UITheme.spacingSmall();
        UITheme.label("存在时长: " + formatDuration(ageSeconds), UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.label("距上次修改: " + formatDuration(ageSinceModified), UITheme.DarkTheme.TEXT_DISABLED);
    }

    private boolean renderAnimationSection(Container container) {
        boolean modified = false;
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
        AnimationMode currentMode = preset != null ? preset.getMode() : AnimationMode.INSTANT;

        // ✅ 统一语义：容器预设只提供“单块动画(How)”，排序/切片/随机等属于片段的“序列行为(Who comes first)”
        // 拆除容器（DEMOLITION）默认用于擦除轨道：更合理只暴露 EXIT 类动画。
        boolean isDemolition = false;
        try {
            isDemolition = container.getType() == ContainerType.DEMOLITION;
        } catch (Exception ignored) {
        }

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
                modeNames[i] = m.getDisplayName() + "（旧/将迁移为序列）";
            } else if (isDemolition && m == AnimationMode.ASSEMBLE) {
                modeNames[i] = "碎片散射";
            } else {
                modeNames[i] = m.getDisplayName();
            }
        }

        if (ImGui.combo("默认动画效果", selectedIdx, modeNames)) {
            int idx = Math.max(0, Math.min(selectedIdx.get(), modes.length - 1));
            ContainerAnimationPreset updated = preset != null ? preset.copy() : ContainerAnimationPreset.defaultPreset();
            updated.setMode(modes[idx]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 默认动画预设已更新: {}", updated.getMode());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(isDemolition
                    ? "拆除容器：作为“擦除片段”的默认退出动画（方块会在退出动画结束后被清除）"
                    : "作为新建片段初始值的默认入场动画（排序请在片段的“序列行为”里设置）");
        }

        UITheme.spacingSmall();
        if (preset != null) {
            UITheme.label(preset.getMode().getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        if (!java.util.List.of(baseModes).contains(currentMode)) {
            UITheme.spacingSmall();
            UITheme.label("提示：该模式属于旧版本的\"排序策略\"，拖到时间线后会自动迁移到「序列行为」面板。", UITheme.DarkTheme.TEXT_DISABLED);
        }

        // ===== 动画模式参数 =====
        UITheme.separator();
        UITheme.label("动画细节参数", UITheme.DarkTheme.TEXT_SELECTED);
        
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
            com.chronoblocks.core.animation.transform.IAnimationTransformer transformer = 
                com.chronoblocks.core.animation.transform.AnimationTransformerFactory.getInstance().getTransformer(currentMode);
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
            imgui.ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "错误: " + e.getMessage());
        }

        UITheme.separator();

        float[] duration;
        duration = new float[]{preset.getSuggestedDuration()};
        if (ImGui.dragFloat("建议时长", duration, 0.1f, 0.05f, 120.0f, "%.2fs")) {
            ContainerAnimationPreset updated;
            updated = preset.copy();
            updated.setSuggestedDuration(duration[0]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("用于初始化片段长度，实际片段仍可单独调整");
        }

        float[] delay;
        delay = new float[]{preset.getDelay()};
        if (ImGui.dragFloat("默认延迟", delay, 0.05f, 0.0f, 30.0f, "%.2fs")) {
            ContainerAnimationPreset updated;
            updated = preset.copy();
            updated.setDelay(delay[0]);
            container.setDefaultAnimationPreset(updated);
            modified = true;
        }

        UITheme.spacingSmall();
        UITheme.label("提示：时间线片段的设置将覆盖这里的默认值。", UITheme.DarkTheme.TEXT_DISABLED);
        return modified;
    }

    private void renderBoundingBoxSection(Container container) {
        var boundingBox = container.getBoundingBox();
        BlockPos min = boundingBox.getMin();
        BlockPos max = boundingBox.getMax();
        BlockPos center = container.getCenter();

        UITheme.label("最小点: " + formatBlockPos(min), UITheme.DarkTheme.TEXT);
        UITheme.label("最大点: " + formatBlockPos(max), UITheme.DarkTheme.TEXT);
        if (center != null) {
            UITheme.label("中心点: " + formatBlockPos(center), UITheme.DarkTheme.TEXT_SELECTED);
        }

        UITheme.spacingSmall();

        int width = boundingBox.getWidth();
        int height = boundingBox.getHeight();
        int depth = boundingBox.getDepth();

        UITheme.label(String.format("尺寸: %d × %d × %d", width, height, depth), UITheme.DarkTheme.TEXT_SELECTED);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("宽 × 高 × 深");
        }
    }

    private void renderStatisticsSection(Container container) {
        int blockCount = container.getBlockCount();
        int totalBlockCount = container.getTotalBlockCount();
        int volume = container.getBoundingBox().getVolume();

        UITheme.label("方块数量: " + blockCount, UITheme.DarkTheme.TEXT);
        UITheme.label("总方块数（含子容器）: " + totalBlockCount, UITheme.DarkTheme.TEXT);
        UITheme.label("体积: " + volume + " 方块³", UITheme.DarkTheme.TEXT);

        if (volume > 0) {
            float fillRate = (blockCount / (float) volume) * 100;
            UITheme.label(String.format("填充率: %.1f%%", fillRate), UITheme.DarkTheme.TEXT_DISABLED);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("实际方块数 / 包围盒体积");
            }
        }
    }

    private boolean renderQuickActions(Container container) {
        boolean modified = false;
        UITheme.beginGroup("快捷操作");

        if (ImGui.button("克隆容器", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            Container cloned = container.clone();
            LOGGER.info("[ContainerPropertyAdapter] 克隆容器: {} -> {}", container.getName(), cloned.getName());
            // TODO: 添加到容器管理器
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("创建此容器的副本");
        }

        ImGui.sameLine();

        if (ImGui.button("清空方块", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            container.clearBlocks();
            modified = true;
            LOGGER.info("[ContainerPropertyAdapter] 清空容器方块: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("移除容器中的所有方块");
        }

        ImGui.sameLine();

        if (ImGui.button("同步到世界", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            LOGGER.info("[ContainerPropertyAdapter] 同步容器到世界: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("将容器内容同步到 Minecraft 世界");
        }

        if (ImGui.button("刷新统计", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 刷新统计: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重新计算容器统计信息");
        }

        ImGui.sameLine();

        if (ImGui.button("重置中心点", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (container.getBlockCount() > 0) {
                container.addBlocks(java.util.Collections.emptySet());
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 重置中心点: {}", container.getName());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重新计算容器中心点");
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
        return "容器属性编辑器";
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
            return seconds + " 秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " 小时";
        } else {
            return (seconds / 86400) + " 天";
        }
    }
}

