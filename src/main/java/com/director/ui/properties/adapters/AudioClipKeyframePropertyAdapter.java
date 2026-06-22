package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.audio.AudioClipKeyframe;
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
            // ⭐ 正确移动：需要通过所属 clip 进行 moveKeyframe()，否则 clipKeyframes 列表不会重新排序
            TimelineClip owner = findOwnerClip(keyframe);
            if (owner != null) {
                owner.moveKeyframe(keyframe, time[0]);
                TimelineModel model = DirectorServices.get().containerTimeline()
                        .getTimelineModel();
                if (model != null) {
                    model.markDirty();
                }
            } else {
                // 回退：仍允许修改，但可能影响排序/采样（极少数情况下 Selection 不在时间线模型内）
                keyframe.setTime(time[0]);
            }
            modified = true;
        }
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("Volume");
        float[] volume = {keyframe.getVolume()};
        if (ImGui.sliderFloat("Volume", volume, 0.0f, 1.0f, "%.2f")) {
            keyframe.setVolume(volume[0]);
            TimelineModel model = DirectorServices.get().containerTimeline()
                    .getTimelineModel();
            if (model != null) {
                model.markDirty();
            }
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
            TimelineModel model = DirectorServices.get().containerTimeline()
                    .getTimelineModel();
            if (model != null) {
                model.markDirty();
            }
            modified = true;
        }
        UITheme.endGroup();

        return modified;
    }

    @Override
    public Class<AudioClipKeyframe> getTargetType() {
        return AudioClipKeyframe.class;
    }

    private TimelineClip findOwnerClip(AudioClipKeyframe keyframe) {
        if (keyframe == null) {
            return null;
        }
        TimelineModel model = DirectorServices.get().containerTimeline()
                .getTimelineModel();
        if (model == null) {
            return null;
        }
        for (TimelineTrack track : model.getAllTracks()) {
            if (track == null) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip == null) {
                    continue;
                }
                // 按 id 匹配，避免对象引用变化导致 contains 失败
                for (var kf : clip.getClipKeyframes()) {
                    if (kf != null && kf.getId().equals(keyframe.getId())) {
                        return clip;
                    }
                }
            }
        }
        return null;
    }
}
