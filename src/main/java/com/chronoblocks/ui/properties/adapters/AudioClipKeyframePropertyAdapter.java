package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.audio.AudioClipKeyframe;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Property adapter for audio clip volume keyframes.
 */
public class AudioClipKeyframePropertyAdapter implements IPropertyAdapter<AudioClipKeyframe> {
    @Override
    public boolean renderProperties(AudioClipKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("No keyframe selected", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.beginGroup("Time");
        float[] time = {keyframe.getTime()};
        if (ImGui.dragFloat("Time (s)", time, 0.01f, 0.0f, Float.MAX_VALUE, "%.3f")) {
            keyframe.setTime(time[0]);
            modified = true;
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("Volume");
        float[] volume = {keyframe.getVolume()};
        if (ImGui.sliderFloat("Volume", volume, 0.0f, 1.0f, "%.2f")) {
            keyframe.setVolume(volume[0]);
            modified = true;
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("Interpolation");
        TimelineInterpolation[] values = TimelineInterpolation.values();
        String[] labels = new String[values.length];
        int current = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].getDisplayName();
            if (values[i] == keyframe.getInterpolation()) {
                current = i;
            }
        }
        ImInt selected = new ImInt(current);
        if (ImGui.combo("Mode", selected, labels)) {
            keyframe.setInterpolation(values[selected.get()]);
            modified = true;
        }
        UITheme.endGroup();

        return modified;
    }

    @Override
    public Class<AudioClipKeyframe> getTargetType() {
        return AudioClipKeyframe.class;
    }
}
