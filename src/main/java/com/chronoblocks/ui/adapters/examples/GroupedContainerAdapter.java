package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.adapters.render.PropertyGroupRenderer;
import com.chronoblocks.ui.adapters.util.PropertySearchFilter;
import imgui.ImGui;
import imgui.type.ImString;
import imgui.type.ImInt;

import java.util.Arrays;

/**
 * 🧱 GroupedContainerAdapter - 分组容器属性适配器示例
 *
 * <p>展示如何使用 {@link PropertyGroupRenderer} 实现属性分组和搜索。
 *
 * <h3>✨ 特色功能：</h3>
 * <ul>
 *     <li>🎯 属性分组：Transform / Visual / Animation / Hierarchy / Geometry</li>
 *     <li>🔍 搜索支持：与搜索过滤器完全集成</li>
 *     <li>📊 自动隐藏：空分组自动隐藏</li>
 *     <li>🎨 分组高亮：匹配的分组会高亮显示</li>
 *     <li>✨ 标签系统：每个属性都有标签和类别</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 注册适配器（优先级200，高于ContainerAdapter的100）
 * PropertyAdapterRegistry.get().register(new GroupedContainerAdapter());
 *
 * // 选择容器
 * Container container = ...;
 * SelectionManager.get().setCurrentSelection(container);
 *
 * // 在搜索框输入
 * "opacity"       → 只显示 Visual 分组的不透明度属性
 * "#transform"    → 只显示 Transform 分组
 * "@animation"    → 只显示 Animation 分组
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class GroupedContainerAdapter extends BasePropertyAdapter<Container> {

    /** 分组渲染器 */
    private PropertyGroupRenderer groupRenderer;

    /** 搜索过滤器 */
    private PropertySearchFilter searchFilter;

    @Override
    protected boolean doRenderProperties(Container container) {
        boolean changed = false;

        // 创建分组渲染器
        if (searchFilter == null) {
            searchFilter = new PropertySearchFilter();
        }
        groupRenderer = new PropertyGroupRenderer(searchFilter);

        // ========== 基础信息分组 ==========
        if (groupRenderer.beginGroup("📝 基础信息", Arrays.asList("basic", "info"))) {
            changed |= renderBasicProperties(container);
            groupRenderer.endGroup();
        }

        // ========== 视觉属性分组 ==========
        if (groupRenderer.beginGroup("🎨 视觉", Arrays.asList("visual", "appearance"))) {
            changed |= renderVisualProperties(container);
            groupRenderer.endGroup();
        }

        // ========== 动画属性分组 ==========
        if (groupRenderer.beginGroup("🎬 动画", Arrays.asList("animation", "playback"))) {
            changed |= renderAnimationProperties(container);
            groupRenderer.endGroup();
        }

        // ========== 层级信息分组 ==========
        if (container.hasChildren() || container.getParent() != null) {
            if (groupRenderer.beginGroup("🌳 层级", Arrays.asList("hierarchy", "tree"))) {
                renderHierarchyProperties(container);
                groupRenderer.endGroup();
            }
        }

        // ========== 几何信息分组 ==========
        if (container.getBlockCount() > 0) {
            if (groupRenderer.beginGroup("📍 几何", Arrays.asList("geometry", "spatial"))) {
                renderGeometryProperties(container);
                groupRenderer.endGroup();
            }
        }

        return changed;
    }

    /**
     * 渲染基础属性
     */
    private boolean renderBasicProperties(Container container) {
        boolean changed = false;

        // 名称
        changed |= groupRenderer.renderPropertyWithResult("名称",
                Arrays.asList("name", "basic"),
                Arrays.asList("basic"),
                () -> {
                    ImString name = new ImString(container.getName(), 128);
                    if (ImGui.inputText("##name", name)) {
                        container.setName(name.get());
                        return true;
                    }
                    return false;
                });

        // 类型
        changed |= groupRenderer.renderPropertyWithResult("类型",
                Arrays.asList("type", "basic"),
                Arrays.asList("basic"),
                () -> {
                    ContainerType[] types = ContainerType.values();
                    String[] typeNames = new String[types.length];
                    for (int i = 0; i < types.length; i++) {
                        typeNames[i] = types[i].getDisplayName();
                    }
                    
                    int currentIndex = getCurrentTypeIndex(container.getType(), types);
                    ImInt index = new ImInt(currentIndex);
                    
                    if (ImGui.combo("##type", index, typeNames)) {
                        container.setType(types[index.get()]);
                        return true;
                    }
                    return false;
                });

        // 只读信息
        groupRenderer.renderProperty("ID",
                Arrays.asList("id", "basic"),
                Arrays.asList("metadata"),
                () -> {
                    ImGui.text("ID:");
                    ImGui.sameLine();
                    ImGui.textDisabled(container.getId().toString().substring(0, 8) + "...");
                });

        groupRenderer.renderProperty("方块数量",
                Arrays.asList("blocks", "count"),
                Arrays.asList("metadata"),
                () -> {
                    ImGui.text("方块数量:");
                    ImGui.sameLine();
                    ImGui.textDisabled(String.valueOf(container.getBlockCount()));
                });

        return changed;
    }

    /**
     * 渲染视觉属性
     */
    private boolean renderVisualProperties(Container container) {
        boolean changed = false;

        // 不透明度
        changed |= groupRenderer.renderPropertyWithResult("不透明度",
                Arrays.asList("opacity", "alpha", "visual"),
                Arrays.asList("visual"),
                () -> {
                    float[] opacity = {container.getOpacity()};
                    if (ImGui.sliderFloat("##opacity", opacity, 0.0f, 1.0f, "%.2f")) {
                        container.setOpacity(opacity[0]);
                        return true;
                    }
                    return false;
                });

        // 可见性
        changed |= groupRenderer.renderPropertyWithResult("可见",
                Arrays.asList("visible", "show", "visual"),
                Arrays.asList("visual"),
                () -> {
                    boolean visible = container.isVisible();
                    if (ImGui.checkbox("##visible", visible)) {
                        container.setVisible(!visible);
                        return true;
                    }
                    return false;
                });

        // 颜色
        changed |= groupRenderer.renderPropertyWithResult("颜色",
                Arrays.asList("color", "tint", "visual"),
                Arrays.asList("visual"),
                () -> {
                    float[] color = intColorToFloat(container.getColor());
                    if (ImGui.colorEdit4("##color", color)) {
                        container.setColor(floatColorToInt(color));
                        return true;
                    }
                    return false;
                });

        return changed;
    }

    /**
     * 渲染动画属性
     */
    private boolean renderAnimationProperties(Container container) {
        boolean changed = false;

        // 动画模式
        changed |= groupRenderer.renderPropertyWithResult("动画模式",
                Arrays.asList("mode", "animation"),
                Arrays.asList("animation"),
                () -> {
                    AnimationMode[] modes = AnimationMode.values();
                    String[] modeNames = new String[modes.length];
                    for (int i = 0; i < modes.length; i++) {
                        modeNames[i] = modes[i].name();
                    }
                    
                    int currentIndex = getCurrentModeIndex(container.getAnimationMode(), modes);
                    ImInt index = new ImInt(currentIndex);
                    
                    if (ImGui.combo("##animMode", index, modeNames)) {
                        container.setAnimationMode(modes[index.get()]);
                        return true;
                    }
                    return false;
                });

        // 锁定
        changed |= groupRenderer.renderPropertyWithResult("锁定",
                Arrays.asList("locked", "freeze", "animation"),
                Arrays.asList("animation"),
                () -> {
                    boolean locked = container.isLocked();
                    if (ImGui.checkbox("##locked", locked)) {
                        container.setLocked(!locked);
                        return true;
                    }
                    return false;
                });

        return changed;
    }

    /**
     * 渲染层级属性
     */
    private void renderHierarchyProperties(Container container) {
        groupRenderer.renderProperty("父容器",
                Arrays.asList("parent", "hierarchy"),
                Arrays.asList("hierarchy"),
                () -> {
                    if (container.getParent() != null) {
                        ImGui.text("父容器:");
                        ImGui.sameLine();
                        ImGui.textDisabled(container.getParent().getName());
                    } else {
                        ImGui.textDisabled("无父容器");
                    }
                });

        groupRenderer.renderProperty("子容器数量",
                Arrays.asList("children", "hierarchy"),
                Arrays.asList("hierarchy"),
                () -> {
                    ImGui.text("子容器:");
                    ImGui.sameLine();
                    ImGui.textDisabled(String.valueOf(container.getChildren().size()));
                });

        groupRenderer.renderProperty("层级深度",
                Arrays.asList("depth", "hierarchy"),
                Arrays.asList("hierarchy"),
                () -> {
                    ImGui.text("深度:");
                    ImGui.sameLine();
                    ImGui.textDisabled(String.valueOf(container.getDepth()));
                });

        groupRenderer.renderProperty("总方块数",
                Arrays.asList("total", "blocks", "hierarchy"),
                Arrays.asList("hierarchy"),
                () -> {
                    ImGui.text("总方块:");
                    ImGui.sameLine();
                    ImGui.textDisabled(String.valueOf(container.getTotalBlockCount()));
                });
    }

    /**
     * 渲染几何属性
     */
    private void renderGeometryProperties(Container container) {
        groupRenderer.renderProperty("中心点",
                Arrays.asList("center", "geometry"),
                Arrays.asList("geometry"),
                () -> {
                    if (container.getCenter() != null) {
                        ImGui.text("中心:");
                        ImGui.sameLine();
                        ImGui.textDisabled(String.format("(%d, %d, %d)",
                                container.getCenter().getX(),
                                container.getCenter().getY(),
                                container.getCenter().getZ()));
                    }
                });

        var bbox = container.getBoundingBox();
        if (bbox != null) {
            groupRenderer.renderProperty("尺寸",
                    Arrays.asList("size", "dimensions", "geometry"),
                    Arrays.asList("geometry"),
                    () -> {
                        ImGui.text("尺寸:");
                        ImGui.sameLine();
                        ImGui.textDisabled(String.format("%d × %d × %d",
                                bbox.getWidth(), bbox.getHeight(), bbox.getDepth()));
                    });

            groupRenderer.renderProperty("体积",
                    Arrays.asList("volume", "geometry"),
                    Arrays.asList("geometry"),
                    () -> {
                        ImGui.text("体积:");
                        ImGui.sameLine();
                        ImGui.textDisabled(String.valueOf(bbox.getVolume()));
                    });
        }
    }

    // ==================== 辅助方法 ====================

    private int getCurrentTypeIndex(ContainerType current, ContainerType[] types) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private int getCurrentModeIndex(AnimationMode current, AnimationMode[] modes) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private float[] intColorToFloat(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f, // R
                ((color >> 8) & 0xFF) / 255.0f,  // G
                (color & 0xFF) / 255.0f,         // B
                ((color >> 24) & 0xFF) / 255.0f  // A
        };
    }

    private int floatColorToInt(float[] color) {
        int a = Math.round(color[3] * 255) & 0xFF;
        int r = Math.round(color[0] * 255) & 0xFF;
        int g = Math.round(color[1] * 255) & 0xFF;
        int b = Math.round(color[2] * 255) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 设置搜索过滤器
     *
     * @param filter 搜索过滤器
     */
    public void setSearchFilter(PropertySearchFilter filter) {
        this.searchFilter = filter;
    }

    @Override
    public void onPropertyChanged(Container container) {
        super.onPropertyChanged(container);
        logger.debug("[GroupedContainerAdapter] 容器属性已更新: {}", container.getName());
    }

    @Override
    public boolean validate(Container container) {
        if (container.getName() == null || container.getName().trim().isEmpty()) {
            logger.warn("[GroupedContainerAdapter] 容器名称为空");
            return false;
        }

        if (container.getOpacity() < 0 || container.getOpacity() > 1) {
            logger.warn("[GroupedContainerAdapter] 不透明度超出范围: {}", container.getOpacity());
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
        return 200; // 超高优先级（高于 ContainerAdapter 的 100）
    }

    @Override
    public String getDisplayName() {
        return "🧱 分组容器编辑器";
    }

    @Override
    public String getDescription() {
        return "支持搜索和分组的容器属性编辑器";
    }
}

