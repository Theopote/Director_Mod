package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.RotationTrack;
import com.chronoblocks.animation.core.Track;
import com.chronoblocks.animation.core.TransformTrack;
import com.chronoblocks.animation.core.VisibilityTrack;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

/**
 * 新动画系统轨道属性适配器。
 */
@SuppressWarnings("rawtypes")
public class TrackAdapter extends BasePropertyAdapter<Track> {

    @Override
    protected boolean doRenderProperties(Track track) {
        UITheme.title("🎵 轨道信息");

        UITheme.beginGroup("基础信息");
        ImGui.text("属性路径: " + track.getPropertyPath());
        ImGui.text("轨道类型: " + resolveType(track));
        ImGui.text("关键帧数量: " + track.getKeyframes().size());
        UITheme.endGroup();

        UITheme.separator();

        UITheme.beginGroup("时间范围");
        var keyframes = track.getKeyframes();
        if (keyframes.isEmpty()) {
            ImGui.textDisabled("暂无关键帧");
        } else {
            Object firstObj = keyframes.getFirst();
            Object lastObj = keyframes.getLast();
            double first = firstObj instanceof Keyframe<?> kf ? kf.time() : 0.0;
            double last = lastObj instanceof Keyframe<?> kf ? kf.time() : first;
            ImGui.text(String.format("起始: %.3fs", first));
            ImGui.text(String.format("结束: %.3fs", last));
            ImGui.text(String.format("跨度: %.3fs", Math.max(0.0, last - first)));
        }
        UITheme.endGroup();

        UITheme.separator();

        if (!keyframes.isEmpty()) {
            UITheme.beginGroup("关键帧预览");
            ImGui.beginChild("##track_keyframes", 0, 180, true);
            for (int i = 0; i < keyframes.size(); i++) {
                Object entry = keyframes.get(i);
                if (entry instanceof Keyframe<?> kf) {
                    ImGui.text(String.format("[%d] t=%.3fs %s", i + 1, kf.time(), describeValue(kf.value())));
                } else {
                    ImGui.text(String.format("[%d] %s", i + 1, entry));
                }
            }
            ImGui.endChild();
            UITheme.endGroup();
        }

        return false;
    }

    private String resolveType(Track track) {
        if (track instanceof TransformTrack transformTrack) {
            return transformTrack.isScaleTrack() ? "ScaleTrack" : "PositionTrack";
        }
        if (track instanceof RotationTrack) {
            return "RotationTrack";
        }
        if (track instanceof VisibilityTrack) {
            return "VisibilityTrack";
        }
        return track.getClass().getSimpleName();
    }

    private String describeValue(Object value) {
        if (value instanceof Vec3d vec) {
            return String.format("(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
        }
        if (value instanceof Number number) {
            return String.format("%.3f", number.doubleValue());
        }
        return String.valueOf(value);
    }

    @Override
    public Class<Track> getTargetType() {
        return Track.class;
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public String getDisplayName() {
        return "🎵 轨道查看器";
    }

    @Override
    public String getDescription() {
        return "查看轨道属性与关键帧分布。";
    }
}


