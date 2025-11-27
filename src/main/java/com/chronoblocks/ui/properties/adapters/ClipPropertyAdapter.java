package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.animation.core.AnimationClip;
import com.chronoblocks.animation.core.RotationTrack;
import com.chronoblocks.animation.core.TransformTrack;
import com.chronoblocks.animation.core.VisibilityTrack;
import com.chronoblocks.animation.core.Track;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;

import java.util.List;

/**
 * 动画片段属性适配器（基于新动画架构）
 */
public class ClipPropertyAdapter implements IPropertyAdapter<AnimationClip> {

    @Override
    public boolean renderProperties(AnimationClip clip) {
        UITheme.title("🎬 动画片段属性");

        UITheme.beginGroup("基本信息");
        ImGui.text("名称: " + clip.getName());
        double start = clip.getStart();
        double end = clip.getEnd();
        ImGui.text(String.format("时间范围: %.2fs → %.2fs (%.2fs)", start, end, Math.max(0.0, end - start)));
        ImGui.text("绑定容器: " + clip.getBoundContainerId());
        UITheme.endGroup();

        UITheme.separator();

        List<Track<?>> tracks = clip.getTracks();
        UITheme.beginGroup("轨道概览");
        ImGui.text("轨道数量: " + tracks.size());
        for (int i = 0; i < tracks.size(); i++) {
            Track<?> track = tracks.get(i);
            String typeName = resolveTrackType(track);
            ImGui.text(String.format("[%d] %s (%s) - 关键帧: %d",
                i + 1,
                track.getPropertyPath(),
                typeName,
                track.getKeyframes().size()));
        }
        UITheme.endGroup();

        return false;
    }

    private String resolveTrackType(Track<?> track) {
        if (track instanceof TransformTrack transformTrack) {
            return transformTrack.isScaleTrack() ? "Scale" : "Position";
        }
        if (track instanceof RotationTrack) {
            return "Rotation";
        }
        if (track instanceof VisibilityTrack) {
            return "Visibility";
        }
        return track.getClass().getSimpleName();
    }

    @Override
    public Class<AnimationClip> getTargetType() {
        return AnimationClip.class;
    }
}