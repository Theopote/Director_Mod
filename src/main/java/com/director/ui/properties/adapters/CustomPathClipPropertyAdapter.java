package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * CustomPathClipPropertyAdapter - 自定义路径片段属性适配器。
 */
public class CustomPathClipPropertyAdapter implements IPropertyAdapter<CustomPathClip> {

    private final ImString nameBuffer = new ImString("", 256);
    private final ImBoolean enabled = new ImBoolean(true);
    private CustomPathClip lastClip = null;

    @Override
    public Class<CustomPathClip> getTargetType() {
        return CustomPathClip.class;
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean supports(Object target) {
        return target instanceof CustomPathClip;
    }

    @Override
    public boolean renderProperties(CustomPathClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选择自定义路径片段");
            lastClip = null;
            return false;
        }

        if (clip != lastClip) {
            nameBuffer.set(clip.getName());
            enabled.set(clip.isEnabled());
            lastClip = clip;
        }

        boolean modified;

        renderHeader(clip);
        ImGui.separator();

        modified = renderBasicSection(clip);
        ImGui.separator();

        renderTimingSection(clip);
        ImGui.separator();

        renderKeyframeSection(clip);

        if (modified) {
            TimelineCameraController.getInstance().onKeyframeChanged();
        } else {
            syncCacheFromClip(clip);
        }

        return modified;
    }

    private void renderHeader(CustomPathClip clip) {
        ImGui.text(Icons.CAMERA + " Camera Path");
        ImGui.sameLine();
        ImGui.text("· CUSTOM");
        ImGui.textDisabled("Keyframed camera path");
    }

    private boolean renderBasicSection(CustomPathClip clip) {
        boolean modified = false;
        if (ImGui.collapsingHeader("Basic", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            if (ImGui.inputText("Name", nameBuffer)) {
                clip.setName(nameBuffer.get());
                modified = true;
            }
            if (ImGui.checkbox("Enabled", enabled)) {
                clip.setEnabled(enabled.get());
                modified = true;
            }
            ImGui.unindent();
        }
        return modified;
    }

    private void renderTimingSection(CustomPathClip clip) {
        if (ImGui.collapsingHeader("Timing", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            ImGui.beginDisabled();
            ImGui.text(String.format("Start: %.2fs", clip.getStartTime()));
            ImGui.text(String.format("End:   %.2fs", clip.getEndTime()));
            ImGui.text(String.format("Duration: %.2fs", clip.getDuration()));
            ImGui.endDisabled();
            ImGui.textDisabled("Edit timing on the timeline.");
            ImGui.unindent();
        }
    }

    private void renderKeyframeSection(CustomPathClip clip) {
        if (ImGui.collapsingHeader("Keyframes", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            int count = clip.getKeyframes().size();
            ImGui.text(String.format("Camera keyframes: %d", count));
            if (count == 0) {
                ImGui.textDisabled("Add keyframes from the custom path clip or timeline playhead.");
            } else {
                ImGui.textDisabled("Select individual keyframes to edit camera transform and interpolation.");
            }
            ImGui.unindent();
        }
    }

    private void syncCacheFromClip(CustomPathClip clip) {
        if (clip == null || clip != lastClip) {
            return;
        }
        if (!nameBuffer.get().equals(clip.getName())) {
            nameBuffer.set(clip.getName());
        }
        if (enabled.get() != clip.isEnabled()) {
            enabled.set(clip.isEnabled());
        }
    }
}
