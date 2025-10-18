package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;

/**
 * 🎬 SimpleKeyframeAdapter - 关键帧属性适配器示例
 *
 * <p>展示如何使用 {@link BasePropertyAdapter} 快速创建属性适配器。
 *
 * <h3>✨ 支持的属性：</h3>
 * <ul>
 *     <li>⏱ 时间点（Time）</li>
 *     <li>📍 位置（Position XYZ）</li>
 *     <li>🔄 旋转（Rotation XYZ）</li>
 *     <li>📏 缩放（Scale XYZ）</li>
 *     <li>👁 不透明度（Opacity）</li>
 *     <li>📈 插值类型（Interpolation）</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 注册适配器
 * PropertyAdapterRegistry.get().register(new SimpleKeyframeAdapter());
 *
 * // 在 PropertyContext 中使用
 * PropertyContext context = new PropertyContext();
 * context.setSelectedObject(keyframe);
 * context.render(); // 自动使用 SimpleKeyframeAdapter 渲染
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class SimpleKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;

        // ========== 时间属性 ==========
        renderSectionHeader("⏱ 时间");
        changed |= renderFloatProperty("时间点 (秒)",
                keyframe::getTime,
                keyframe::setTime,
                0.01f, 0.0f, Float.MAX_VALUE);

        // ========== 变换属性 ==========
        renderSectionHeader("🎯 变换");

        // 位置
        float[] position = keyframe.getPosition();
        changed |= renderFloatProperty("位置 X",
                () -> position[0],
                v -> keyframe.setPosition(v, position[1], position[2]),
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        changed |= renderFloatProperty("位置 Y",
                () -> position[1],
                v -> keyframe.setPosition(position[0], v, position[2]),
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        changed |= renderFloatProperty("位置 Z",
                () -> position[2],
                v -> keyframe.setPosition(position[0], position[1], v),
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        // 旋转
        float[] rotation = keyframe.getRotation();
        changed |= renderFloatProperty("旋转 X (度)",
                () -> rotation[0],
                v -> keyframe.setRotation(v, rotation[1], rotation[2]),
                1.0f, -180.0f, 180.0f);

        changed |= renderFloatProperty("旋转 Y (度)",
                () -> rotation[1],
                v -> keyframe.setRotation(rotation[0], v, rotation[2]),
                1.0f, -180.0f, 180.0f);

        changed |= renderFloatProperty("旋转 Z (度)",
                () -> rotation[2],
                v -> keyframe.setRotation(rotation[0], rotation[1], v),
                1.0f, -180.0f, 180.0f);

        // 缩放
        float[] scale = keyframe.getScale();
        changed |= renderFloatProperty("缩放 X",
                () -> scale[0],
                v -> keyframe.setScale(v, scale[1], scale[2]),
                0.01f, 0.001f, 10.0f);

        changed |= renderFloatProperty("缩放 Y",
                () -> scale[1],
                v -> keyframe.setScale(scale[0], v, scale[2]),
                0.01f, 0.001f, 10.0f);

        changed |= renderFloatProperty("缩放 Z",
                () -> scale[2],
                v -> keyframe.setScale(scale[0], scale[1], v),
                0.01f, 0.001f, 10.0f);

        // ========== 视觉属性 ==========
        renderSectionHeader("👁 视觉");

        changed |= renderFloatProperty("不透明度",
                keyframe::getOpacity,
                keyframe::setOpacity,
                0.01f, 0.0f, 1.0f);

        // ========== 插值属性 ==========
        renderSectionHeader("📈 插值");

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

        return changed;
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

    @Override
    public void onPropertyChanged(Keyframe keyframe) {
        super.onPropertyChanged(keyframe);
        // 可以在这里发布事件或记录撤销历史
        logger.debug("[SimpleKeyframeAdapter] 关键帧属性已更新: time={}", keyframe.getTime());
    }

    @Override
    public boolean validate(Keyframe keyframe) {
        // 验证时间非负
        if (keyframe.getTime() < 0) {
            logger.warn("[SimpleKeyframeAdapter] 关键帧时间为负: {}", keyframe.getTime());
            return false;
        }

        // 验证透明度范围
        if (keyframe.getOpacity() < 0 || keyframe.getOpacity() > 1) {
            logger.warn("[SimpleKeyframeAdapter] 不透明度超出范围: {}", keyframe.getOpacity());
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
        return 100; // 专用适配器，高优先级
    }

    @Override
    public String getDisplayName() {
        return "🎬 关键帧编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑动画关键帧的时间、变换和视觉属性";
    }
}

