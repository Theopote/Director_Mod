package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.adapters.events.PropertyChangeNotifier;

/**
 * ⚙️ EventAwareKeyframeAdapter - 事件通知关键帧适配器
 *
 * <p>展示如何使用 {@link PropertyChangeNotifier} 发送详细的属性变更事件。
 *
 * <h3>✨ 特色功能：</h3>
 * <ul>
 *     <li>🔔 详细事件：每个属性变更都发送独立事件</li>
 *     <li>📊 精确追踪：包含旧值和新值</li>
 *     <li>🎯 命名规范：统一的属性命名（如 "position.x"）</li>
 *     <li>🔄 同步机制：其他模块可以监听并同步</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 注册适配器
 * PropertyAdapterRegistry.get().register(new EventAwareKeyframeAdapter());
 *
 * // 2. 在其他模块中监听
 * PropertyChangeNotifier.get().addTypeListener(Keyframe.class, event -> {
 *     if (event.getPropertyName().equals("time")) {
 *         timeline.updateKeyframePosition(event.getTargetAs(Keyframe.class));
 *     }
 * });
 *
 * // 3. 用户编辑属性
 * // → 自动发送事件
 * // → 时间线自动更新
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class EventAwareKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;

        // ========== 时间属性 ==========
        renderSectionHeader("⏱ 时间");

        float oldTime = keyframe.getTime();
        changed |= renderFloatProperty("时间点 (秒)",
                keyframe::getTime,
                value -> {
                    keyframe.setTime(value);
                    // 通知时间变更
                    notifyPropertyChange(keyframe, "time", oldTime, value);
                },
                0.01f, 0.0f, Float.MAX_VALUE);

        // ========== 位置属性 ==========
        renderSectionHeader("📍 位置");

        float[] position = keyframe.getPosition();
        
        float oldPosX = position[0];
        changed |= renderFloatProperty("位置 X",
                () -> position[0],
                value -> {
                    keyframe.setPosition(value, position[1], position[2]);
                    notifyPropertyChange(keyframe, "position.x", oldPosX, value);
                },
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        float oldPosY = position[1];
        changed |= renderFloatProperty("位置 Y",
                () -> position[1],
                value -> {
                    keyframe.setPosition(position[0], value, position[2]);
                    notifyPropertyChange(keyframe, "position.y", oldPosY, value);
                },
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        float oldPosZ = position[2];
        changed |= renderFloatProperty("位置 Z",
                () -> position[2],
                value -> {
                    keyframe.setPosition(position[0], position[1], value);
                    notifyPropertyChange(keyframe, "position.z", oldPosZ, value);
                },
                0.1f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        // ========== 不透明度属性 ==========
        renderSectionHeader("👁 视觉");

        float oldOpacity = keyframe.getOpacity();
        changed |= renderFloatProperty("不透明度",
                keyframe::getOpacity,
                value -> {
                    keyframe.setOpacity(value);
                    notifyPropertyChange(keyframe, "opacity", oldOpacity, value);
                },
                0.01f, 0.0f, 1.0f);

        // ========== 插值类型 ==========
        renderSectionHeader("📈 插值");

        InterpolationType[] types = InterpolationType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].getDisplayName();
        }

        InterpolationType oldType = keyframe.getInterpolationType();
        int currentIndex = getCurrentInterpolationIndex(oldType, types);
        
        changed |= renderComboProperty("插值类型",
                () -> currentIndex,
                index -> {
                    keyframe.setInterpolationType(types[index]);
                    notifyPropertyChange(keyframe, "interpolationType", oldType, types[index]);
                },
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
        logger.debug("[EventAwareKeyframeAdapter] 关键帧属性已更新: time={}", keyframe.getTime());
    }

    @Override
    public boolean validate(Keyframe keyframe) {
        if (keyframe.getTime() < 0) {
            logger.warn("[EventAwareKeyframeAdapter] 关键帧时间为负: {}", keyframe.getTime());
            return false;
        }

        if (keyframe.getOpacity() < 0 || keyframe.getOpacity() > 1) {
            logger.warn("[EventAwareKeyframeAdapter] 不透明度超出范围: {}", keyframe.getOpacity());
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
        return 250; // 超高优先级（带事件通知）
    }

    @Override
    public String getDisplayName() {
        return "⚙️ 事件通知关键帧编辑器";
    }

    @Override
    public String getDescription() {
        return "支持详细事件通知的关键帧属性编辑器";
    }
}

