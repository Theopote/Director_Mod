package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.Keyframe.Easing;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.adapters.events.PropertyChangeNotifier;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

/**
 * 示例：在属性修改时发布事件的新关键帧适配器。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class EventAwareKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;

        float oldTime = (float) keyframe.time();
        changed |= renderFloatProperty("时间 (秒)",
            () -> (float) keyframe.time(),
            v -> {
                keyframe.setTime(Math.max(0.0, v));
                emitChange(keyframe, "time", oldTime, keyframe.time());
            },
            0.01f, 0.0f, Float.MAX_VALUE);

        Object value = keyframe.value();
        if (value instanceof Vec3d vec) {
            float[] components = {(float) vec.x, (float) vec.y, (float) vec.z};
            float[] old = components.clone();
            if (ImGui.dragFloat3("向量值", components, 0.01f)) {
                keyframe.setValue(new Vec3d(components[0], components[1], components[2]));
                notifyAxisChanges(keyframe, old, components);
                changed = true;
            }
        } else if (value instanceof Number number) {
            float oldScalar = number.floatValue();
            changed |= renderFloatProperty("数值",
                number::floatValue,
                v -> {
                    keyframe.setValue(v);
                emitChange(keyframe, "value", oldScalar, v);
                },
                0.01f, -Float.MAX_VALUE, Float.MAX_VALUE);
        }

        Easing oldEasing = keyframe.easing();
        Easing[] easings = Easing.values();
        String[] labels = new String[easings.length];
        for (int i = 0; i < easings.length; i++) {
            labels[i] = easings[i].name();
        }
        changed |= renderComboProperty("插值模式",
            () -> indexOf(easings, keyframe.easing()),
            idx -> {
                keyframe.setEasing(easings[idx]);
                emitChange(keyframe, "easing", oldEasing, easings[idx]);
            },
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

    private void notifyAxisChanges(Keyframe keyframe, float[] oldValues, float[] newValues) {
        if (oldValues[0] != newValues[0]) {
            emitChange(keyframe, "value.x", oldValues[0], newValues[0]);
        }
        if (oldValues[1] != newValues[1]) {
            emitChange(keyframe, "value.y", oldValues[1], newValues[1]);
        }
        if (oldValues[2] != newValues[2]) {
            emitChange(keyframe, "value.z", oldValues[2], newValues[2]);
        }
    }

    private void emitChange(Keyframe keyframe, String property, Object oldValue, Object newValue) {
        PropertyChangeNotifier.get().notifyChange(
            keyframe,
            property,
            oldValue,
            newValue
        );
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
}


