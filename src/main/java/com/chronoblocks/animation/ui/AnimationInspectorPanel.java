package com.chronoblocks.animation.ui;

import com.chronoblocks.animation.timeline.AnimationTimelineService;
import com.chronoblocks.animation.timeline.AnimationTimelineService.ClipBinding;
import com.chronoblocks.ui.panels.AbstractPanel;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AnimationInspectorPanel - 动画属性占位检查器。
 */
public class AnimationInspectorPanel extends AbstractPanel {

    private UUID focusedClipId;

    @Override
    public String getPanelName() {
        return "AnimationInspector";
    }

    @Override
    public String getWindowName() {
        return "Animation Inspector###AnimationInspectorPanel";
    }

    @Override
    protected void doRender() {
        UITheme.title("🎬 动画检查器");
        UITheme.separator();

        if (focusedClipId != null) {
            ImGui.text("当前聚焦 Clip: " + focusedClipId);
        } else {
            ImGui.text("尚未选中任何时间线片段。");
        }

        UITheme.spacingSmall();
        UITheme.label("绑定概览", UITheme.DarkTheme.TEXT_SELECTED);

        AnimationTimelineService service = AnimationTimelineService.get();
        List<UUID> containerIds = service.getControlledContainers();
        if (containerIds.isEmpty()) {
            ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "暂无容器绑定时间线片段。");
        } else {
            ImGui.beginChild("BindingList", 0, ImGui.getTextLineHeightWithSpacing() * 8, true);
            containerIds.stream()
                .map(service::getBinding)
                .flatMap(Optional::stream)
                .forEach(this::renderBindingRow);
            ImGui.endChild();
        }

        UITheme.spacingSmall();
        ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f,
            "占位版本：后续将在此面板编辑入场方式、曲线与关键帧。");
    }

    private void renderBindingRow(ClipBinding binding) {
        ImGui.pushID(binding.clip().getId().toString());
        ImGui.text(String.format("Clip %s", binding.clipName()));
        ImGui.sameLine();
        ImGui.textDisabled(String.format("(%.1fs - %.1fs)", binding.startSeconds(), binding.endSeconds()));
        ImGui.sameLine();
        if (ImGui.button("定位")) {
            AnimationTimelineService.get().focusClip(binding.clip().getId());
            focusedClipId = binding.clip().getId();
        }
        ImGui.popID();
    }

    public void setFocusedClip(UUID clipId) {
        this.focusedClipId = clipId;
    }
}


