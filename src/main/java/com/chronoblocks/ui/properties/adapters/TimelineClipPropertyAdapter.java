package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.animation.timeline.ClipAnimationConfig;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * {@link TimelineClip} 属性适配器。
 * 提供时间线片段的基础信息与动画配置编辑。
 */
public class TimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private final ImString nameBuffer = new ImString(128);
    private final ImString easingBuffer = new ImString(64);
    private final ImString orderingBuffer = new ImString(64);
    private final ImInt modeIndex = new ImInt();
    private static final String[] MODE_LABELS = buildModeLabels();

    private static String[] buildModeLabels() {
        AnimationMode[] modes = AnimationMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].getDisplayName();
        }
        return labels;
    }

    @Override
    public boolean renderProperties(TimelineClip clip) {
        boolean modified = false;

        UITheme.title(Icons.CLIPBOARD + " 时间线片段");

        nameBuffer.set(clip.getLabel());
        if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            clip.setLabel(nameBuffer.get().trim().isEmpty() ? clip.getLabel() : nameBuffer.get());
            modified = true;
        }

        float[] start = {clip.getStartTime()};
        if (ImGui.dragFloat("起始时间", start, 0.05f, 0.0f, 10_000f, "%.2fs")) {
            clip.setStartTime(Math.max(0.0f, start[0]));
            modified = true;
        }

        float[] length = {clip.getLength()};
        if (ImGui.dragFloat("持续时长", length, 0.05f, 0.05f, 10_000f, "%.2fs")) {
            clip.setLength(length[0]);
            modified = true;
        }

        UITheme.separator();
        UITheme.label(Icons.ANIMATION + " 动画配置", UITheme.DarkTheme.TEXT_SELECTED);

        ClipAnimationConfig config = clip.getAnimationConfig();
        AnimationMode[] modes = AnimationMode.values();
        modeIndex.set(config.getMode().ordinal());
        if (ImGui.combo("动画模式", modeIndex, MODE_LABELS)) {
            config.setMode(modes[modeIndex.get()]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制片段出现时的基础动画方式");
        }

        UITheme.spacingSmall();
        UITheme.label(config.getMode().getDescription(), UITheme.DarkTheme.TEXT_DISABLED);

        float[] animDuration = {config.getDuration()};
        if (ImGui.dragFloat("动画时长", animDuration, 0.05f, 0.05f, 10_000f, "%.2fs")) {
            config.setDuration(animDuration[0]);
            modified = true;
        }

        float[] delay = {config.getDelay()};
        if (ImGui.dragFloat("延迟", delay, 0.05f, 0.0f, 600f, "%.2fs")) {
            config.setDelay(delay[0]);
            modified = true;
        }

        easingBuffer.set(config.getEasing());
        if (ImGui.inputText("Easing 曲线", easingBuffer)) {
            config.setEasing(easingBuffer.get());
            modified = true;
        }

        orderingBuffer.set(config.getOrdering());
        if (ImGui.inputText("播放顺序", orderingBuffer)) {
            config.setOrdering(orderingBuffer.get());
            modified = true;
        }

        if (modified) {
            clip.getParentTrack(); // 访问以确保引用存在，未来可触发刷新
        }

        UITheme.spacingSmall();
        UITheme.label("提示：这些参数仅作用于当前片段，可与容器默认预设不同。", UITheme.DarkTheme.TEXT_DISABLED);
        return modified;
    }

    @Override
    public Class<TimelineClip> getTargetType() {
        return TimelineClip.class;
    }
}

