package com.director.ui.properties.adapters;

import com.director.core.animation.container.Keyframe;
import com.director.core.animation.container.Keyframe.Easing;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.util.math.Vec3d;

/**
 * 新动画系统关键帧属性适配器。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class KeyframePropertyAdapter implements IPropertyAdapter<Keyframe> {

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    @Override
    public boolean renderProperties(Keyframe keyframe) {
        if (keyframe == null) {
            UITheme.label(tr(DirectorLang.Properties.AdapterKeyframe.NO_KEYFRAME), UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.beginGroup(tr(DirectorLang.Properties.AdapterKeyframe.TIME_GROUP));
        float[] time = {(float) keyframe.time()};
        if (ImGui.dragFloat(tr(DirectorLang.Properties.AdapterKeyframe.TIME_SECONDS), time, 0.01f, 0.0f, Float.MAX_VALUE, "%.3f")) {
            keyframe.setTime(time[0]);
            modified = true;
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup(tr(DirectorLang.Properties.AdapterKeyframe.VALUE_GROUP));
        Object value = keyframe.value();
        if (value instanceof Vec3d vec) {
            float[] components = {(float) vec.x, (float) vec.y, (float) vec.z};
            if (ImGui.dragFloat3(tr(DirectorLang.Properties.AdapterKeyframe.VECTOR), components, 0.01f)) {
                keyframe.setValue(new Vec3d(components[0], components[1], components[2]));
                modified = true;
            }
        } else if (value instanceof Number number) {
            float[] scalar = {number.floatValue()};
            if (ImGui.dragFloat(tr(DirectorLang.Properties.AdapterKeyframe.SCALAR), scalar, 0.01f, -Float.MAX_VALUE, Float.MAX_VALUE, "%.3f")) {
                keyframe.setValue(scalar[0]);
                modified = true;
            }
        } else {
            ImGui.text(tr(DirectorLang.Properties.AdapterKeyframe.VALUE_LABEL, value));
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup(tr(DirectorLang.Properties.AdapterKeyframe.INTERPOLATION_GROUP));
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
        if (ImGui.combo(tr(DirectorLang.Properties.AdapterKeyframe.MODE), selectedIndex, labels)) {
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

    @Override
    public int getPriority() { return 70; }
}
