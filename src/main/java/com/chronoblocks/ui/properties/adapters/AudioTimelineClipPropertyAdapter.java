package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.audio.TimelineAudioService;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioClipKeyframe;
import com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;

/**
 * 音频片段属性适配器
 *
 * <p>用于 AUDIO 轨道上的 {@link AudioTimelineClip}。</p>
 */
public class AudioTimelineClipPropertyAdapter implements IPropertyAdapter<AudioTimelineClip> {

    @Override
    public boolean renderProperties(AudioTimelineClip clip) {
        if (clip == null) {
            UITheme.label("未选中音频片段", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.title(TrackType.AUDIO.getIcon() + " 音频片段");

        if (ImGui.collapsingHeader("基础", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] start = { clip.getStartTime() };
            if (ImGui.dragFloat("开始时间(秒)", start, 0.05f, 0.0f, 100000f, "%.2fs")) {
                clip.setStartTime(Math.max(0.0f, start[0]));
                modified = true;
            }

            float[] len = { clip.getLength() };
            if (ImGui.dragFloat("时长(秒)", len, 0.05f, 0.05f, 100000f, "%.2fs")) {
                clip.setLength(len[0]);
                modified = true;
            }

            ImBoolean muted = new ImBoolean(clip.isMuted());
            if (ImGui.checkbox("片段静音", muted)) {
                clip.setMuted(muted.get());
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("静音后：该片段的音量自动化对播放无效（事件仍可存在）。");
            }
        }

        if (ImGui.collapsingHeader("链接", ImGuiTreeNodeFlags.DefaultOpen)) {
            String boundName = clip.getBoundContainerName();
            if (boundName == null || boundName.isBlank()) {
                boundName = "（未绑定）";
            }
            ImGui.textDisabled("绑定容器: " + boundName);
            if (clip.getBoundContainerId() != null) {
                ImGui.textDisabled("容器ID: " + clip.getBoundContainerId());
            }

            float t;
            try {
                // 这里不依赖 TimelineModel；仅用于给用户一个“当前时间点的音量倍率”参考。
                float now = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance()
                        .getTimelineModel() != null
                        ? com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance()
                        .getTimelineModel().getCurrentTime()
                        : -1f;
                if (now >= 0f) {
                    t = TimelineAudioService.getInstance().getVolumeAtTimeForClip(clip, now);
                    ImGui.textDisabled(String.format("当前时间音量倍率: %.2f", t));
                }
            } catch (Exception ignored) {
            }
        }

        if (ImGui.collapsingHeader("音量自动化", ImGuiTreeNodeFlags.DefaultOpen)) {
            int count = 0;
            for (var kf : clip.getClipKeyframes()) {
                if (kf instanceof AudioClipKeyframe) {
                    count++;
                }
            }
            ImGui.textDisabled("音量关键帧数量: " + count);
            ImGui.textDisabled("提示：在时间线上双击该音频片段可添加音量关键帧。");
        }

        return modified;
    }

    @Override
    public Class<AudioTimelineClip> getTargetType() {
        return AudioTimelineClip.class;
    }

    @Override
    public String getAdapterName() {
        return TrackType.AUDIO.getIcon() + " 音频片段";
    }
}


