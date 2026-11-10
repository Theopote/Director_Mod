package com.director.ui.properties.adapters;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.Section;
import com.director.ui.properties.PropertyRow;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.CameraViewportProfile;
import imgui.ImGui;
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

        if (Section.header("camera.track.section_aspect", CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_ASPECT))) {
            renderAspectRatioSection(profile, modifiedRef);
        }

        if (Section.header("camera.track.section_safe_area", CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_SAFE_AREA))) {
            renderSafeAreaSection(profile, modifiedRef);
        }

        if (Section.header("camera.track.section_preview_res", CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_PREVIEW_RES))) {
            renderPreviewResolutionSection(profile, modifiedRef);
        }

        if (Section.header("camera.track.section_preview_opts", CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_PREVIEW_OPTS), false)) {
            renderPreviewOptionsSection(profile, modifiedRef);
        }

        if (Section.header("camera.track.section_guides", CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SECTION_GUIDES), false)) {
            renderGuideLinesSection(profile, modifiedRef);
        }

        return modifiedRef[0];
    }

    private void renderAspectRatioSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        CameraViewportProfile.AspectRatioMode[] ratioModes = CameraViewportProfile.AspectRatioMode.values();
        String[] ratioLabels = CameraPropertyAdapterI18n.aspectRatioModeLabels();

        ImInt ratioIdx = new ImInt(profile.getAspectMode().ordinal());
        PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ASPECT_RATIO));
        if (ImGui.combo("##aspect_ratio",
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
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_16_9);
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_9_16),
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_9_16);
            profile.setPreviewResolution(1080, 1920);
            modifiedRef[0] = true;
        }
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_1_1),
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_1_1);
            profile.setPreviewResolution(1080, 1080);
            modifiedRef[0] = true;
        }

        if (profile.getAspectMode() == CameraViewportProfile.AspectRatioMode.CUSTOM) {
            UITheme.spacingSmall();
            int[] customRatio = {profile.getCustomAspectW(), profile.getCustomAspectH()};
            PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.CUSTOM_RATIO));
            if (ImGui.inputInt2("##custom_ratio",
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
        PropertyRow.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_SAFE_AREAS));
        if (ImGui.checkbox("##show_safe_areas",
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
            PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ACTION_SAFE));
            if (ImGui.dragFloat("##action_safe",
                    actionSafe, 0.5f, 50.0f, 100.0f, "%.1f%%")) {
                profile.setActionSafe(actionSafe[0] / 100.0f);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.ACTION_SAFE_TOOLTIP));
            }

            UITheme.spacingSmall();

            float[] titleSafe = {profile.getTitleSafe() * 100.0f};
            PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.TITLE_SAFE));
            if (ImGui.dragFloat("##title_safe",
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
        PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PREVIEW_RES));
        if (ImGui.inputInt2("##preview_res",
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
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_720P),
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1280, 720);
            modifiedRef[0] = true;
        }
        if (ImGui.button(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.PRESET_4K),
                0, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(3840, 2160);
            modifiedRef[0] = true;
        }

        UITheme.spacingSmall();
        String[] scalePolicyLabels = CameraPropertyAdapterI18n.previewScalePolicyLabels();
        ImInt scalePolicyIdx = new ImInt(profile.getPreviewScalePolicy().ordinal());
        PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SCALE_POLICY));
        if (ImGui.combo("##scale_policy",
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
        PropertyRow.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_LETTERBOX));
        if (ImGui.checkbox("##show_letterbox",
                showLetterbox)) {
            profile.setShowLetterboxMask(showLetterbox.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_LETTERBOX_TOOLTIP));
        }

        if (profile.isShowLetterboxMask()) {
            float[] alpha = {profile.getLetterboxAlpha()};
            PropertyRow.field(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.MASK_ALPHA));
            if (ImGui.sliderFloat("##mask_alpha",
                    alpha, 0.0f, 1.0f, "%.2f")) {
                profile.setLetterboxAlpha(alpha[0]);
                modifiedRef[0] = true;
            }
        }

        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " "
                        + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.LETTERBOX_NOTE),
                UITheme.DarkTheme.TEXT_DISABLED);
    }

    private void renderGuideLinesSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showCenterCross = new ImBoolean(profile.isShowCenterCross());
        PropertyRow.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_CENTER_CROSS));
        if (ImGui.checkbox("##show_center_cross",
                showCenterCross)) {
            profile.setShowCenterCross(showCenterCross.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_CENTER_CROSS_TOOLTIP));
        }

        UITheme.spacingSmall();

        ImBoolean showRuleOfThirds = new ImBoolean(profile.isShowRuleOfThirds());
        PropertyRow.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Track.SHOW_RULE_OF_THIRDS));
        if (ImGui.checkbox("##show_rule_of_thirds",
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
