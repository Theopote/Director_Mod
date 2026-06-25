package com.director.ui.properties.adapters;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.CameraViewportProfile;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 摄像机轨道属性适配器 - Director Properties System
 */
public class CameraTrackPropertyAdapter implements IPropertyAdapter<TimelineTrack> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraTrackPropertyAdapter.class);

    @Override
    public Class<TimelineTrack> getTargetType() {
        return TimelineTrack.class;
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean supports(Object target) {
        if (target instanceof TimelineTrack track) {
            return track.getType() == TrackType.CAMERA;
        }
        return false;
    }

    @Override
    public boolean renderProperties(TimelineTrack track) {
        if (track == null) {
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.NULL_TRACK),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        if (track.getType() != TrackType.CAMERA) {
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.NOT_CAMERA_TRACK),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        final boolean[] modifiedRef = {false};
        CameraViewportProfile profile = track.getCameraViewportProfile();

        UITheme.title(Icons.CAMERA + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.TITLE));
        UITheme.spacingSmall();

        if (ImGui.collapsingHeader(Icons.SCALE + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_ASPECT),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderAspectRatioSection(profile, modifiedRef);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.SHIELD + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_SAFE_AREA),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderSafeAreaSection(profile, modifiedRef);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.PREVIEW + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_PREVIEW_RES),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderPreviewResolutionSection(profile, modifiedRef);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.FILM + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_PREVIEW_OPTS))) {
            ImGui.indent();
            renderPreviewOptionsSection(profile, modifiedRef);
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.GRID + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_GUIDES))) {
            ImGui.indent();
            renderGuideLinesSection(profile, modifiedRef);
            ImGui.unindent();
        }

        return modifiedRef[0];
    }

    private void renderAspectRatioSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        CameraViewportProfile.AspectRatioMode[] ratioModes = CameraViewportProfile.AspectRatioMode.values();
        String[] ratioLabels = CameraPropertyAdapterI18n.aspectRatioModeLabels();

        ImInt ratioIdx = new ImInt(profile.getAspectMode().ordinal());
        if (ImGui.combo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ASPECT_RATIO),
                ratioIdx, ratioLabels)) {
            profile.setAspectMode(ratioModes[ratioIdx.get()]);
            modifiedRef[0] = true;
            LOGGER.debug("[CameraTrackPropertyAdapter] 画幅比例已更新: {}", ratioLabels[ratioIdx.get()]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ASPECT_RATIO_TOOLTIP));
        }

        UITheme.spacingSmall();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_16_9),
                100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_16_9);
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_9_16),
                100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_9_16);
            profile.setPreviewResolution(1080, 1920);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_1_1),
                100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_1_1);
            profile.setPreviewResolution(1080, 1080);
            modifiedRef[0] = true;
        }

        if (profile.getAspectMode() == CameraViewportProfile.AspectRatioMode.CUSTOM) {
            UITheme.spacingSmall();
            int[] customRatio = {profile.getCustomAspectW(), profile.getCustomAspectH()};
            if (ImGui.inputInt2(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.CUSTOM_RATIO),
                    customRatio)) {
                profile.setCustomAspectW(customRatio[0]);
                profile.setCustomAspectH(customRatio[1]);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.CUSTOM_RATIO_TOOLTIP));
            }
        }

        float ratio = profile.getAspectRatio();
        int w = profile.getEffectiveAspectW();
        int h = profile.getEffectiveAspectH();
        UITheme.spacingSmall();
        UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.CURRENT_RATIO_FMT, w, h, ratio),
                UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.EXPORT_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderSafeAreaSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showSafeAreas = new ImBoolean(profile.isShowSafeAreas());
        if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_SAFE_AREAS),
                showSafeAreas)) {
            profile.setShowSafeAreas(showSafeAreas.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_SAFE_AREAS_TOOLTIP));
        }

        if (profile.isShowSafeAreas()) {
            UITheme.spacingSmall();

            float[] actionSafe = {profile.getActionSafe() * 100.0f};
            if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ACTION_SAFE),
                    actionSafe, 0.5f, 50.0f, 100.0f, "%.1f%%")) {
                profile.setActionSafe(actionSafe[0] / 100.0f);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ACTION_SAFE_TOOLTIP));
            }

            UITheme.spacingSmall();

            float[] titleSafe = {profile.getTitleSafe() * 100.0f};
            if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.TITLE_SAFE),
                    titleSafe, 0.5f, 50.0f, 100.0f, "%.1f%%")) {
                profile.setTitleSafe(titleSafe[0] / 100.0f);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.TITLE_SAFE_TOOLTIP));
            }
        }

        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SAFE_AREA_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderPreviewResolutionSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        int[] previewRes = {profile.getPreviewWidth(), profile.getPreviewHeight()};
        if (ImGui.inputInt2(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PREVIEW_RES),
                previewRes)) {
            profile.setPreviewResolution(previewRes[0], previewRes[1]);
            modifiedRef[0] = true;
            LOGGER.debug("[CameraTrackPropertyAdapter] 预览分辨率已更新: {}×{}", previewRes[0], previewRes[1]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PREVIEW_RES_TOOLTIP));
        }

        UITheme.spacingSmall();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_1080P),
                120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_720P),
                120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1280, 720);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_4K),
                120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(3840, 2160);
            modifiedRef[0] = true;
        }

        UITheme.spacingSmall();
        String[] scalePolicyLabels = CameraPropertyAdapterI18n.previewScalePolicyLabels();
        ImInt scalePolicyIdx = new ImInt(profile.getPreviewScalePolicy().ordinal());
        if (ImGui.combo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SCALE_POLICY),
                scalePolicyIdx, scalePolicyLabels)) {
            profile.setPreviewScalePolicy(CameraViewportProfile.PreviewScalePolicy.values()[scalePolicyIdx.get()]);
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SCALE_POLICY_TOOLTIP));
        }

        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PREVIEW_RES_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderPreviewOptionsSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showLetterbox = new ImBoolean(profile.isShowLetterboxMask());
        if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_LETTERBOX),
                showLetterbox)) {
            profile.setShowLetterboxMask(showLetterbox.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_LETTERBOX_TOOLTIP));
        }

        if (profile.isShowLetterboxMask()) {
            ImGui.indent();
            float[] alpha = {profile.getLetterboxAlpha()};
            if (ImGui.sliderFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.MASK_ALPHA),
                    alpha, 0.0f, 1.0f, "%.2f")) {
                profile.setLetterboxAlpha(alpha[0]);
                modifiedRef[0] = true;
            }
            ImGui.unindent();
        }

        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.LETTERBOX_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderGuideLinesSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showCenterCross = new ImBoolean(profile.isShowCenterCross());
        if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_CENTER_CROSS),
                showCenterCross)) {
            profile.setShowCenterCross(showCenterCross.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_CENTER_CROSS_TOOLTIP));
        }

        UITheme.spacingSmall();

        ImBoolean showRuleOfThirds = new ImBoolean(profile.isShowRuleOfThirds());
        if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_RULE_OF_THIRDS),
                showRuleOfThirds)) {
            profile.setShowRuleOfThirds(showRuleOfThirds.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_RULE_OF_THIRDS_TOOLTIP));
        }

        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.GUIDES_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    @Override
    public String getAdapterName() {
        return CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ADAPTER_NAME);
    }
}
