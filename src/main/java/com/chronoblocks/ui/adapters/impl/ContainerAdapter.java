package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerAnimationPreset;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.theme.Icons;

/**
 * 📦 ContainerAdapter - 容器属性适配器
 *
 * <p>为 ChronoBlocks 容器对象提供专用属性编辑器。
 *
 * <h3>支持的属性：</h3>
 * <ul>
 *     <li>基础属性：名称、类型、ID</li>
 *     <li>视觉属性：颜色、不透明度、可见性</li>
 *     <li>动画属性：动画模式、锁定状态</li>
 *     <li>统计信息：方块数量、层级深度</li>
 *     <li>几何信息：中心点、边界框</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class ContainerAdapter extends BasePropertyAdapter<Container> {

    @Override
    protected boolean doRenderProperties(Container container) {
        boolean changed = false;

        // ========== 基础属性 ==========
        renderSectionHeader(Icons.CLIPBOARD + " 基础信息");
        
        changed |= renderTextProperty("名称",
                container::getName,
                container::setName,
                128);
        
        // 容器类型（下拉框）
        ContainerType[] types = ContainerType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].getDisplayName();
        }
        
        int currentTypeIndex = getCurrentTypeIndex(container.getType(), types);
        changed |= renderComboProperty("类型",
                () -> currentTypeIndex,
                index -> container.setType(types[index]),
                typeNames);
        
        // 只读信息
        renderReadOnlyText("ID", container.getId().toString().substring(0, 8) + "...");
        renderReadOnlyText("方块数量", String.valueOf(container.getBlockCount()));
        renderReadOnlyText("层级深度", String.valueOf(container.getDepth()));

        // ========== 视觉属性 ==========
        renderSectionHeader(Icons.PALETTE + " 视觉");
        
        changed |= renderFloatProperty("不透明度",
                container::getOpacity,
                container::setOpacity,
                0.01f, 0.0f, 1.0f);
        
        // 可见性（使用容器状态）
        changed |= renderBoolProperty("可见",
                () -> container.getState() == com.chronoblocks.core.container.ContainerState.VISIBLE,
                visible -> container.setState(visible ? 
                    com.chronoblocks.core.container.ContainerState.VISIBLE : 
                    com.chronoblocks.core.container.ContainerState.HIDDEN));
        
        // 颜色（ARGB 格式）
        float[] color = intColorToFloat(container.getColor());
        if (renderColorAlphaProperty(
                () -> color,
                newColor -> container.setColor(floatColorToInt(newColor)))) {
            changed = true;
        }

        // ========== 动画默认预设 ==========
        renderSectionHeader(Icons.ANIMATION + " 动画默认预设");
        AnimationMode[] modes = AnimationMode.values();
        String[] modeNames = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeNames[i] = modes[i].name();
        }

        changed |= renderComboProperty("默认动画模式",
                () -> container.getDefaultAnimationPreset().getMode().ordinal(),
                index -> {
                    ContainerAnimationPreset updated = container.getDefaultAnimationPreset().copy();
                    updated.setMode(modes[index]);
                    container.setDefaultAnimationPreset(updated);
                },
                modeNames);

        changed |= renderFloatProperty("建议时长 (秒)",
                () -> container.getDefaultAnimationPreset().getSuggestedDuration(),
                value -> {
                    ContainerAnimationPreset updated = container.getDefaultAnimationPreset().copy();
                    updated.setSuggestedDuration(value);
                    container.setDefaultAnimationPreset(updated);
                },
                0.1f, 0.05f, 600.0f);

        changed |= renderFloatProperty("默认延迟 (秒)",
                () -> container.getDefaultAnimationPreset().getDelay(),
                value -> {
                    ContainerAnimationPreset updated = container.getDefaultAnimationPreset().copy();
                    updated.setDelay(Math.max(0.0f, value));
                    container.setDefaultAnimationPreset(updated);
                },
                0.05f, 0.0f, 120.0f);

        changed |= renderTextProperty("默认 Easing",
                () -> container.getDefaultAnimationPreset().getEasing(),
                value -> {
                    ContainerAnimationPreset updated = container.getDefaultAnimationPreset().copy();
                    updated.setEasing(value);
                    container.setDefaultAnimationPreset(updated);
                });

        changed |= renderTextProperty("默认排序",
                () -> container.getDefaultAnimationPreset().getOrdering(),
                value -> {
                    ContainerAnimationPreset updated = container.getDefaultAnimationPreset().copy();
                    updated.setOrdering(value);
                    container.setDefaultAnimationPreset(updated);
                });
        
        changed |= renderBoolProperty("锁定",
                container::isLocked,
                container::setLocked);
        
        renderHelpMarker("锁定后无法编辑容器内容");

        // ========== 层级信息 ==========
        if (container.hasChildren() || container.getParent() != null) {
            renderSectionHeader("🌳 层级结构");
            
            if (container.getParent() != null) {
                renderReadOnlyText("父容器", container.getParent().getName());
            }
            
            if (container.hasChildren()) {
                renderReadOnlyText("子容器数量", String.valueOf(container.getChildren().size()));
                renderReadOnlyText("总方块数", String.valueOf(container.getTotalBlockCount()));
            }
        }

        // ========== 几何信息 ==========
        if (container.getBlockCount() > 0) {
            renderSectionHeader(Icons.PIN + " 几何");
            
            if (container.getCenter() != null) {
                renderReadOnlyText("中心点", String.format("(%d, %d, %d)",
                        container.getCenter().getX(),
                        container.getCenter().getY(),
                        container.getCenter().getZ()));
            }
            
            var bbox = container.getBoundingBox();
            if (bbox != null) {
                renderReadOnlyText("尺寸", String.format("%d × %d × %d",
                        bbox.getWidth(), bbox.getHeight(), bbox.getDepth()));
                renderReadOnlyText("体积", String.valueOf(bbox.getVolume()));
            }
        }

        return changed;
    }

    /**
     * 获取当前容器类型的索引
     */
    private int getCurrentTypeIndex(ContainerType current, ContainerType[] types) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == current) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 将整数颜色转换为浮点数数组 (RGBA)
     */
    private float[] intColorToFloat(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f, // R
                ((color >> 8) & 0xFF) / 255.0f,  // G
                (color & 0xFF) / 255.0f,         // B
                ((color >> 24) & 0xFF) / 255.0f  // A
        };
    }

    /**
     * 将浮点数数组转换为整数颜色 (ARGB)
     */
    private int floatColorToInt(float[] color) {
        int a = Math.round(color[3] * 255) & 0xFF;
        int r = Math.round(color[0] * 255) & 0xFF;
        int g = Math.round(color[1] * 255) & 0xFF;
        int b = Math.round(color[2] * 255) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onPropertyChanged(Container container) {
        super.onPropertyChanged(container);
        // 发布容器变更事件
        logger.debug("[ContainerAdapter] 容器属性已更新: {}", container.getName());
    }

    @Override
    public boolean validate(Container container) {
        // 验证名称不为空
        if (container.getName() == null || container.getName().trim().isEmpty()) {
            logger.warn("[ContainerAdapter] 容器名称为空");
            return false;
        }

        // 验证不透明度范围
        if (container.getOpacity() < 0 || container.getOpacity() > 1) {
            logger.warn("[ContainerAdapter] 不透明度超出范围: {}", container.getOpacity());
            return false;
        }

        return true;
    }

    @Override
    public Class<Container> getTargetType() {
        return Container.class;
    }

    @Override
    public int getPriority() {
        return 100; // 专用适配器，高优先级
    }

    @Override
    public String getDisplayName() {
        return "📦 容器编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑容器的名称、类型、视觉和动画属性";
    }
}

