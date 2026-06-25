package com.director.ui.properties.adapters;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
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
            ImGui.textDisabled(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.NO_CLIP));
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
        ImGui.text(Icons.CAMERA + " " + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.PATH_HEADER));
        ImGui.sameLine();
        ImGui.text("· " + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.TYPE_LABEL));
        ImGui.textDisabled(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.SUBTITLE));
    }

    private boolean renderBasicSection(CustomPathClip clip) {
        boolean modified = false;
        if (ImGui.collapsingHeader(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.BASIC_SECTION), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            if (ImGui.inputText(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.NAME), nameBuffer)) {
                clip.setName(nameBuffer.get());
                modified = true;
            }
            if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.ENABLED), enabled)) {
                clip.setEnabled(enabled.get());
                modified = true;
            }
            ImGui.unindent();
        }
        return modified;
    }

    private void renderTimingSection(CustomPathClip clip) {
        if (ImGui.collapsingHeader(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.TIMING_SECTION), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            ImGui.beginDisabled();
            ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.TIMING_START, clip.getStartTime()));
            ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.TIMING_END, clip.getEndTime()));
            ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.TIMING_DURATION, clip.getDuration()));
            ImGui.endDisabled();
            ImGui.textDisabled(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.TIMING_HINT));
            ImGui.unindent();
        }
    }

    private void renderKeyframeSection(CustomPathClip clip) {
        if (ImGui.collapsingHeader(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.KEYFRAMES_SECTION), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            int count = clip.getKeyframes().size();
            ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.KEYFRAME_COUNT, count));
            if (count == 0) {
                ImGui.textDisabled(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.KEYFRAME_EMPTY_HINT));
            } else {
                ImGui.textDisabled(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Custom.KEYFRAME_SELECT_HINT));
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
