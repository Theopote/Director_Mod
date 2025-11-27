package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.Keyframe.Easing;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.util.math.Vec3d;

/**
 * 新动画系统关键帧属性适配器。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class KeyframePropertyAdapter implements IPropertyAdapter<Keyframe> {

    @Override
    public boolean renderProperties(Keyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("未选择关键帧", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.beginGroup("时间");
        float[] time = {(float) keyframe.time()};
        if (ImGui.dragFloat("时间 (秒)", time, 0.01f, 0.0f, Float.MAX_VALUE, "%.3f")) {
            keyframe.setTime(time[0]);
            modified = true;
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("值");
        Object value = keyframe.value();
        if (value instanceof Vec3d vec) {
            float[] components = {(float) vec.x, (float) vec.y, (float) vec.z};
            if (ImGui.dragFloat3("向量", components, 0.01f)) {
                keyframe.setValue(new Vec3d(components[0], components[1], components[2]));
                modified = true;
            }
        } else if (value instanceof Number number) {
            float[] scalar = {number.floatValue()};
            if (ImGui.dragFloat("数值", scalar, 0.01f, -Float.MAX_VALUE, Float.MAX_VALUE, "%.3f")) {
                keyframe.setValue(scalar[0]);
                modified = true;
            }
        } else {
            ImGui.text("值: " + value);
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("插值");
        Easing[] easings = Easing.values();
        String[] labels = new String[easings.length];
        int currentIndex = 0;
        for (int i = 0; i < easings.length; i++) {
            labels[i] = easings[i].name();
            if (easings[i] == keyframe.easing()) {
                currentIndex = i;
            }
        }
        ImInt selectedIndex = new ImInt(currentIndex);
        if (ImGui.combo("模式", selectedIndex, labels)) {
            keyframe.setEasing(easings[selectedIndex.get()]);
            modified = true;
        }
        UITheme.endGroup();

        return modified;
    }

    @Override
    public Class<Keyframe> getTargetType() {
        return Keyframe.class;
    }
}


