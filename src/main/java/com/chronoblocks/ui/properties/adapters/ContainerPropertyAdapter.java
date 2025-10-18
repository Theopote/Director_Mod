package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.container.Container;
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
        
        boolean modified = false;
        
        // 标题
        UITheme.title(Icons.CONTAINER + " 容器属性编辑器");
        
        // ========== 基本信息 ==========
        if (ImGui.collapsingHeader(Icons.CLIPBOARD + " 基本信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 名称编辑
            ImString nameBuffer = new ImString(container.getName(), 256);
            if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                container.setName(nameBuffer.get());
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 容器名称已更新: {}", nameBuffer.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的显示名称");
            }
            
            // 容器类型
            ContainerType currentType = container.getType();
            ContainerType[] types = ContainerType.values();
            
            int currentIdx = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == currentType) {
                    currentIdx = i;
                    break;
                }
            }
            
            ImInt selectedIdx = new ImInt(currentIdx);
            String[] typeNames = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                typeNames[i] = types[i].toString(); // "图标 + 名称"
            }
            
            if (ImGui.combo("容器类型", selectedIdx, typeNames)) {
                container.setType(types[selectedIdx.get()]);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 容器类型已更新: {}", 
                    types[selectedIdx.get()].getDisplayName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器的用途分类");
            }
            
            // UUID显示
            UITheme.spacingSmall();
            UITheme.label("UUID: " + container.getId().toString(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        // ========== 视觉属性 ==========
        if (ImGui.collapsingHeader(Icons.VISIBLE + " 视觉属性", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 可见性
            ImBoolean visible = new ImBoolean(container.isVisible());
            if (ImGui.checkbox("可见", visible)) {
                container.setVisible(visible.get());
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 可见性已更新: {}", visible.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("控制容器在世界中的可见性");
            }
            
            // 锁定状态
            ImBoolean locked = new ImBoolean(container.isLocked());
            if (ImGui.checkbox("锁定", locked)) {
                container.setLocked(locked.get());
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 锁定状态已更新: {}", locked.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("锁定后禁止编辑");
            }
            
            // 不透明度
            float[] opacity = {container.getOpacity()};
            if (ImGui.sliderFloat("不透明度", opacity, 0.0f, 1.0f, "%.2f")) {
                container.setOpacity(opacity[0]);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 不透明度已更新: {}", opacity[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器整体的透明度");
            }
            
            // 百分比显示
            UITheme.spacingSmall();
            UITheme.label(String.format("%.0f%%", opacity[0] * 100), UITheme.DarkTheme.TEXT_DISABLED);
            
            // 颜色标记
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
        
        // ========== 动画模式 ==========
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画模式")) {
            AnimationMode currentMode = container.getAnimationMode();
            AnimationMode[] modes = AnimationMode.values();
            
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
                modeNames[i] = modes[i].getDisplayName();
            }
            
            if (ImGui.combo("动画效果", selectedIdx, modeNames)) {
                container.setAnimationMode(modes[selectedIdx.get()]);
                modified = true;
                LOGGER.debug("[ContainerPropertyAdapter] 动画模式已更新: {}", 
                    modes[selectedIdx.get()].getDisplayName());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("容器出现/消失的动画方式");
            }
            
            // 显示当前模式的描述
            UITheme.spacingSmall();
            UITheme.label(currentMode.getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        // ========== 边界信息 ==========
        if (ImGui.collapsingHeader(Icons.RULER + " 边界信息")) {
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
            
            UITheme.label(String.format("尺寸: %d × %d × %d", width, height, depth), 
                UITheme.DarkTheme.TEXT_SELECTED);
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("宽 × 高 × 深");
            }
        }
        
        // ========== 统计信息 ==========
        if (ImGui.collapsingHeader(Icons.CHART + " 统计信息")) {
            int blockCount = container.getBlockCount();
            int totalBlockCount = container.getTotalBlockCount();
            int volume = container.getBoundingBox().getVolume();
            
            UITheme.label("方块数量: " + blockCount, UITheme.DarkTheme.TEXT);
            UITheme.label("总方块数（含子容器）: " + totalBlockCount, UITheme.DarkTheme.TEXT);
            UITheme.label("体积: " + volume + " 方块³", UITheme.DarkTheme.TEXT);
            
            if (volume > 0) {
                float fillRate = (blockCount / (float)volume) * 100;
                UITheme.label(String.format("填充率: %.1f%%", fillRate), UITheme.DarkTheme.TEXT_DISABLED);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("实际方块数 / 包围盒体积");
                }
            }
        }
        
        // ========== 层级结构 ==========
        if (ImGui.collapsingHeader(Icons.PARENT + " 层级结构")) {
            // 父容器
            Container parent = container.getParent();
            if (parent != null) {
                UITheme.label("父容器: " + parent.getName(), UITheme.DarkTheme.TEXT_SELECTED);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("点击跳转到父容器（未实现）");
                }
            } else {
                UITheme.label("父容器: 无（顶层容器）", UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            UITheme.spacingSmall();
            
            // 子容器列表
            List<Container> children = container.getChildren();
            if (children.isEmpty()) {
                UITheme.label("子容器: 无", UITheme.DarkTheme.TEXT_DISABLED);
            } else {
                if (ImGui.treeNode("子容器 (" + children.size() + ")")) {
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
        
        // ========== 元数据 ==========
        if (ImGui.collapsingHeader(Icons.CALENDAR + " 元数据")) {
            long createdTime = container.getCreatedTime();
            long modifiedTime = container.getModifiedTime();
            
            UITheme.label("创建时间: " + DATE_FORMAT.format(new Date(createdTime)), 
                UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("修改时间: " + DATE_FORMAT.format(new Date(modifiedTime)), 
                UITheme.DarkTheme.TEXT_DISABLED);
            
            long ageSeconds = (System.currentTimeMillis() - createdTime) / 1000;
            long ageSinceModified = (System.currentTimeMillis() - modifiedTime) / 1000;
            
            UITheme.spacingSmall();
            UITheme.label("存在时长: " + formatDuration(ageSeconds), UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("距上次修改: " + formatDuration(ageSinceModified), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        UITheme.separator();
        
        // ========== 快捷操作 ==========
        UITheme.beginGroup("快捷操作");
        
        // 第一行
        if (ImGui.button("克隆容器", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            Container cloned = container.clone();
            LOGGER.info("[ContainerPropertyAdapter] 克隆容器: {} -> {}", 
                container.getName(), cloned.getName());
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
            // TODO: 实现同步逻辑
            LOGGER.info("[ContainerPropertyAdapter] 同步容器到世界: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("将容器内容同步到 Minecraft 世界");
        }
        
        // 第二行
        if (ImGui.button("刷新统计", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            // 强制重新计算统计信息
            modified = true;
            LOGGER.debug("[ContainerPropertyAdapter] 刷新统计: {}", container.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重新计算容器统计信息");
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("重置中心点", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            // 触发中心点重新计算
            if (container.getBlockCount() > 0) {
                // 通过添加空集合触发更新
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

