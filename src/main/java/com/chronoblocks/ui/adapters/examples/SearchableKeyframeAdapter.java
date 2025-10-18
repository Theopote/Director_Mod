package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.adapters.util.PropertySearchFilter;

import java.util.Arrays;

/**
 * 🔍 SearchableKeyframeAdapter - 支持搜索的关键帧属性适配器
 *
 * <p>展示如何在适配器中使用 {@link PropertySearchFilter} 实现属性搜索和过滤。
 *
 * <h3>✨ 搜索特性：</h3>
 * <ul>
 *     <li>🔍 属性名称搜索：输入 "position" 只显示位置相关属性</li>
 *     <li>🏷 标签过滤：输入 "#transform" 只显示变换属性</li>
 *     <li>📂 类别过滤：输入 "@animation" 只显示动画属性</li>
 *     <li>✨ 高亮显示：匹配的文本会高亮显示</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 注册适配器
 * PropertyAdapterRegistry.get().register(new SearchableKeyframeAdapter());
 *
 * // 2. 在属性面板中使用
 * PropertyContext context = new PropertyContext();
 * context.setSelectedObject(keyframe);
 *
 * // 3. 搜索
 * // 用户输入 "pos" → 只显示位置相关属性
 * // 用户输入 "#transform" → 只显示变换属性
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class SearchableKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    /** 当前面板的搜索过滤器（从面板获取） */
    private PropertySearchFilter searchFilter;

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;

        // ========== 时间属性 ==========
        if (shouldShowSection("时间", "time", "basic")) {
            renderSectionHeader("⏱ 时间");

            if (shouldShowProperty("时间点", "time", "basic")) {
                changed |= renderFloatProperty("时间点 (秒)",
                        keyframe::getTime,
                        keyframe::setTime,
                        0.01f, 0.0f, Float.MAX_VALUE);
            }
        }

        // ========== 变换属性 ==========
        if (shouldShowSection("变换", "transform", "animation")) {
            renderSectionHeader("🎯 变换");

            // 位置
            float[] position = keyframe.getPosition();

            if (shouldShowProperty("位置 X", "position", "transform")) {
                changed |= renderFloatProperty("位置 X",
                        () -> position[0],
                        v -> keyframe.setPosition(v, position[1], position[2]),
                        0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
            }

            if (shouldShowProperty("位置 Y", "position", "transform")) {
                changed |= renderFloatProperty("位置 Y",
                        () -> position[1],
                        v -> keyframe.setPosition(position[0], v, position[2]),
                        0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
            }

            if (shouldShowProperty("位置 Z", "position", "transform")) {
                changed |= renderFloatProperty("位置 Z",
                        () -> position[2],
                        v -> keyframe.setPosition(position[0], position[1], v),
                        0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
            }

            // 旋转
            float[] rotation = keyframe.getRotation();

            if (shouldShowProperty("旋转 X", "rotation", "transform")) {
                changed |= renderFloatProperty("旋转 X (度)",
                        () -> rotation[0],
                        v -> keyframe.setRotation(v, rotation[1], rotation[2]),
                        1.0f, -180.0f, 180.0f);
            }

            if (shouldShowProperty("旋转 Y", "rotation", "transform")) {
                changed |= renderFloatProperty("旋转 Y (度)",
                        () -> rotation[1],
                        v -> keyframe.setRotation(rotation[0], v, rotation[2]),
                        1.0f, -180.0f, 180.0f);
            }

            if (shouldShowProperty("旋转 Z", "rotation", "transform")) {
                changed |= renderFloatProperty("旋转 Z (度)",
                        () -> rotation[2],
                        v -> keyframe.setRotation(rotation[0], rotation[1], v),
                        1.0f, -180.0f, 180.0f);
            }

            // 缩放
            float[] scale = keyframe.getScale();

            if (shouldShowProperty("缩放 X", "scale", "transform")) {
                changed |= renderFloatProperty("缩放 X",
                        () -> scale[0],
                        v -> keyframe.setScale(v, scale[1], scale[2]),
                        0.01f, 0.001f, 10.0f);
            }

            if (shouldShowProperty("缩放 Y", "scale", "transform")) {
                changed |= renderFloatProperty("缩放 Y",
                        () -> scale[1],
                        v -> keyframe.setScale(scale[0], v, scale[2]),
                        0.01f, 0.001f, 10.0f);
            }

            if (shouldShowProperty("缩放 Z", "scale", "transform")) {
                changed |= renderFloatProperty("缩放 Z",
                        () -> scale[2],
                        v -> keyframe.setScale(scale[0], scale[1], v),
                        0.01f, 0.001f, 10.0f);
            }
        }

        // ========== 视觉属性 ==========
        if (shouldShowSection("视觉", "visual", "appearance")) {
            renderSectionHeader("👁 视觉");

            if (shouldShowProperty("不透明度", "opacity", "visual")) {
                changed |= renderFloatProperty("不透明度",
                        keyframe::getOpacity,
                        keyframe::setOpacity,
                        0.01f, 0.0f, 1.0f);
            }
        }

        // ========== 插值属性 ==========
        if (shouldShowSection("插值", "interpolation", "animation")) {
            renderSectionHeader("📈 插值");

            if (shouldShowProperty("插值类型", "interpolation", "animation")) {
                InterpolationType[] types = InterpolationType.values();
                String[] typeNames = new String[types.length];
                for (int i = 0; i < types.length; i++) {
                    typeNames[i] = types[i].getDisplayName();
                }

                int currentIndex = getCurrentInterpolationIndex(keyframe.getInterpolationType(), types);
                changed |= renderComboProperty("插值类型",
                        () -> currentIndex,
                        index -> keyframe.setInterpolationType(types[index]),
                        typeNames);
            }
        }

        return changed;
    }

    /**
     * 检查是否应该显示某个节
     *
     * @param sectionName 节名称
     * @param tag         节标签
     * @param category    节类别
     * @return true 如果应该显示
     */
    private boolean shouldShowSection(String sectionName, String tag, String category) {
        if (searchFilter == null || searchFilter.isEmpty()) {
            return true;
        }

        return searchFilter.matches(sectionName,
                Arrays.asList(tag),
                Arrays.asList(category));
    }

    /**
     * 检查是否应该显示某个属性
     *
     * @param propertyName 属性名称
     * @param tag          属性标签
     * @param category     属性类别
     * @return true 如果应该显示
     */
    private boolean shouldShowProperty(String propertyName, String tag, String category) {
        if (searchFilter == null || searchFilter.isEmpty()) {
            return true;
        }

        return searchFilter.matches(propertyName,
                Arrays.asList(tag),
                Arrays.asList(category));
    }

    /**
     * 获取当前插值类型的索引
     */
    private int getCurrentInterpolationIndex(InterpolationType current, InterpolationType[] types) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == current) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 设置搜索过滤器
     * <p>由 PropertyContext 或面板调用。
     *
     * @param filter 搜索过滤器
     */
    public void setSearchFilter(PropertySearchFilter filter) {
        this.searchFilter = filter;
    }

    @Override
    public void onPropertyChanged(Keyframe keyframe) {
        super.onPropertyChanged(keyframe);
        logger.debug("[SearchableKeyframeAdapter] 关键帧属性已更新: time={}", keyframe.getTime());
    }

    @Override
    public boolean validate(Keyframe keyframe) {
        // 验证时间非负
        if (keyframe.getTime() < 0) {
            logger.warn("[SearchableKeyframeAdapter] 关键帧时间为负: {}", keyframe.getTime());
            return false;
        }

        // 验证透明度范围
        if (keyframe.getOpacity() < 0 || keyframe.getOpacity() > 1) {
            logger.warn("[SearchableKeyframeAdapter] 不透明度超出范围: {}", keyframe.getOpacity());
            return false;
        }

        return true;
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }

    @Override
    public int getPriority() {
        return 150; // 比 SimpleKeyframeAdapter 更高优先级
    }

    @Override
    public String getDisplayName() {
        return "🔍 可搜索关键帧编辑器";
    }

    @Override
    public String getDescription() {
        return "支持搜索和过滤的关键帧属性编辑器";
    }
}

