package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.audio.AudioEvent;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 音频事件属性适配器
 *
 * <p>用于编辑音频轨道上的 {@link AudioEvent}（点事件）。</p>
 */
public class AudioEventPropertyAdapter implements IPropertyAdapter<AudioEvent> {

    @Override
    public boolean renderProperties(AudioEvent event) {
        if (event == null) {
            UITheme.label("未选中音频事件", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.title("🔊 音频事件");

        // 基础信息
        ImString label = new ImString(event.getLabel() != null ? event.getLabel() : "", 256);
        if (ImGui.inputText("标签", label, ImGuiInputTextFlags.EnterReturnsTrue)) {
            event.setLabel(label.get());
            modified = true;
        }

        ImString soundId = new ImString(event.getSoundId() != null ? event.getSoundId() : "", 256);
        if (ImGui.inputText("声音ID", soundId, ImGuiInputTextFlags.EnterReturnsTrue)) {
            event.setSoundId(soundId.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("例如：minecraft:block.stone.place");
        }

        float[] time = { event.getTime() };
        boolean isLinked = false;
        try {
            isLinked = TimelineAudioService.getInstance().isEventLinked(event);
        } catch (Exception ignored) {
        }

        if (isLinked) {
            ImGui.textDisabled(String.format("时间(秒): %.2fs", time[0]));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("该事件已与容器片段链接，时间由方块序列决定，不能手动修改。");
            }
        } else {
            if (ImGui.dragFloat("时间(秒)", time, 0.01f, 0.0f, 100000f, "%.2fs")) {
                event.setTime(Math.max(0.0f, time[0]));
                modified = true;
            }
        }

        float[] volume = { event.getVolume() };
        if (ImGui.sliderFloat("音量", volume, 0.0f, 1.0f, "%.2f")) {
            event.setVolume(volume[0]);
            modified = true;
        }

        float[] pitch = { event.getPitch() };
        if (ImGui.sliderFloat("音调", pitch, 0.1f, 3.0f, "%.2f")) {
            event.setPitch(pitch[0]);
            modified = true;
        }

        ImBoolean enabled = new ImBoolean(event.isEnabled());
        if (ImGui.checkbox("启用", enabled)) {
            event.setEnabled(enabled.get());
            modified = true;
        }

        // 链接信息（可选）
        try {
            var linkedClip = TimelineAudioService.getInstance().getLinkedClip(event);
            if (linkedClip.isPresent()) {
                TimelineClip clip = linkedClip.get();
                UITheme.separator();
                UITheme.label("链接", UITheme.DarkTheme.TEXT_SELECTED);
                ImGui.textDisabled(String.format("跟随片段: %s [%.2fs - %.2fs]",
                        clip.getLabel(), clip.getStartTime(), clip.getEndTime()));

                if (ImGui.button("取消与片段链接")) {
                    TimelineAudioService.getInstance().unlinkEvent(event);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("取消后：移动/缩放容器片段不会再自动平移该事件");
                }
            }
        } catch (Exception ignored) {
        }

        return modified;
    }

    @Override
    public Class<AudioEvent> getTargetType() {
        return AudioEvent.class;
    }

    @Override
    public String getAdapterName() {
        return "音频事件";
    }
}


