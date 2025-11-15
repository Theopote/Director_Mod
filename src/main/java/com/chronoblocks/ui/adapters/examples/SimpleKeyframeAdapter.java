package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.Keyframe.Easing;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

/**
 * 简洁示例：展示如何渲染新动画系统的关键帧。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;
        renderSectionHeader("时间");
        changed |= renderFloatProperty("时间 (秒)",
            () -> (float) keyframe.time(),
            v -> keyframe.setTime(Math.max(0.0, v)),
            0.01f, 0.0f, Float.MAX_VALUE);

        renderSectionHeader("值");
        Object value = keyframe.value();
        if (value instanceof Vec3d vec) {
            float[] data = {(float) vec.x, (float) vec.y, (float) vec.z};
            if (ImGui.dragFloat3("向量", data, 0.01f)) {
                keyframe.setValue(new Vec3d(data[0], data[1], data[2]));
                changed = true;
            }
        } else if (value instanceof Number number) {
            changed |= renderFloatProperty("数值",
                number::floatValue,
                v -> keyframe.setValue(v),
                0.01f, -Float.MAX_VALUE, Float.MAX_VALUE);
        } else {
            renderReadOnlyText("类型", value != null ? value.getClass().getSimpleName() : "null");
        }

        renderSectionHeader("插值");
        Easing[] easings = Easing.values();
        String[] labels = new String[easings.length];
        for (int i = 0; i < easings.length; i++) {
            labels[i] = easings[i].name();
        }
        changed |= renderComboProperty("模式",
            () -> indexOf(easings, keyframe.easing()),
            idx -> keyframe.setEasing(easings[idx]),
            labels);

        return changed;
    }

    private int indexOf(Easing[] easings, Easing target) {
        for (int i = 0; i < easings.length; i++) {
            if (easings[i] == target) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
}

