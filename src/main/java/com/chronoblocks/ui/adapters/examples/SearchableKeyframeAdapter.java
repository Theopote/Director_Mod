package com.chronoblocks.ui.adapters.examples;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.adapters.util.PropertySearchFilter;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 支持简单搜索的关键帧适配器示例。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SearchableKeyframeAdapter extends BasePropertyAdapter<Keyframe> {

    private PropertySearchFilter searchFilter;

    @Override
    protected boolean doRenderProperties(Keyframe keyframe) {
        boolean changed = false;

        if (shouldShow("time", "basic")) {
            renderSectionHeader("⏱ 时间");
            changed |= renderFloatProperty("时间",
                () -> (float) keyframe.time(),
                v -> keyframe.setTime(Math.max(0.0, v)),
                0.01f, 0.0f, Float.MAX_VALUE);
        }

        if (shouldShow("value", "transform")) {
            renderSectionHeader("🔢 数值");
            Object value = keyframe.value();
            if (value instanceof Vec3d vec) {
                float[] components = {(float) vec.x, (float) vec.y, (float) vec.z};
                if (ImGui.dragFloat3("向量", components, 0.01f)) {
                    keyframe.setValue(new Vec3d(components[0], components[1], components[2]));
                    changed = true;
                }
            } else if (value instanceof Number number) {
                changed |= renderFloatProperty("数值",
                    number::floatValue,
                    keyframe::setValue,
                    0.01f, -Float.MAX_VALUE, Float.MAX_VALUE);
            } else {
                renderReadOnlyText("类型", value != null ? value.getClass().getSimpleName() : "null");
            }
        }

        if (shouldShow("easing", "animation")) {
            renderSectionHeader("📈 插值");
            var easings = Keyframe.Easing.values();
            String[] labels = new String[easings.length];
            for (int i = 0; i < easings.length; i++) {
                labels[i] = easings[i].name();
            }
            changed |= renderComboProperty("模式",
                () -> indexOf(easings, keyframe.easing()),
                idx -> keyframe.setEasing(easings[idx]),
                labels);
        }

        return changed;
    }

    private boolean shouldShow(String tag, String category) {
        return searchFilter == null
            || searchFilter.matches(tag, List.of(tag), List.of(category));
    }

    private int indexOf(Keyframe.Easing[] easings, Keyframe.Easing target) {
        for (int i = 0; i < easings.length; i++) {
            if (easings[i] == target) {
                return i;
            }
        }
        return 0;
    }

    public void setSearchFilter(PropertySearchFilter filter) {
        this.searchFilter = filter;
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
}


