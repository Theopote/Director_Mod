package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.CameraViewportProfile;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎥 摄像机轨道属性适配器 - Director Properties System
 * 
 * <p>专门用于编辑摄像机轨道（TimelineTrack with TrackType.CAMERA）的属性，
 * 包括画幅比例、安全区、预览分辨率等。
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📐 画幅比例（Aspect Ratio）- 16:9 / 9:16 / 1:1 / 自定义</li>
 *   <li>🟢 构图安全区（Safe Area）- Title Safe / Action Safe</li>
 *   <li>🖼️ 预览分辨率（Preview Resolution）</li>
 *   <li>📺 Letterbox / Mask 预览</li>
 * </ul>
 * 
 * <h3>设计理念</h3>
 * <p>摄像机属性绑定在摄像机上（构图意图），而最终导出分辨率在导出设置中（像素尺寸）。
 * 这样同一台摄像机可以导出不同分辨率的视频，适应多平台分发需求。
 * 
 * @author Director Team
 * @version 1.0
 * @since 2025-01-XX
 */
public class CameraTrackPropertyAdapter implements IPropertyAdapter<TimelineTrack> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraTrackPropertyAdapter.class);
    
    @Override
    public Class<TimelineTrack> getTargetType() {
        return TimelineTrack.class;
    }

    @Override
    public int getPriority() { return 60; }
    
    @Override
    public boolean supports(Object target) {
        // 只支持 TimelineTrack 且类型为 CAMERA
        if (target instanceof TimelineTrack track) {
            return track.getType() == TrackType.CAMERA;
        }
        return false;
    }
    
    @Override
    public boolean renderProperties(TimelineTrack track) {
        if (track == null) {
            UITheme.label("（没有选中摄像机轨道）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        if (track.getType() != TrackType.CAMERA) {
            UITheme.label("（选中的不是摄像机轨道）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        final boolean[] modifiedRef = {false};
        CameraViewportProfile profile = track.getCameraViewportProfile();
        
        // 标题
        UITheme.title(Icons.CAMERA + " 摄像机轨道属性");
        UITheme.spacingSmall();
        
        // ========== 画幅比例 ==========
        if (ImGui.collapsingHeader(Icons.SCALE + " 画幅比例 (Aspect Ratio)", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderAspectRatioSection(profile, modifiedRef);
            ImGui.unindent();
        }
        
        // ========== 安全区 ==========
        if (ImGui.collapsingHeader(Icons.SHIELD + " 构图安全区 (Safe Area)", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderSafeAreaSection(profile, modifiedRef);
            ImGui.unindent();
        }
        
        // ========== 预览分辨率 ==========
        if (ImGui.collapsingHeader(Icons.PREVIEW + " 预览分辨率 (Preview Resolution)", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            renderPreviewResolutionSection(profile, modifiedRef);
            ImGui.unindent();
        }
        
        // ========== 预览选项 ==========
        if (ImGui.collapsingHeader(Icons.FILM + " 预览选项")) {
            ImGui.indent();
            renderPreviewOptionsSection(profile, modifiedRef);
            ImGui.unindent();
        }
        
        // ========== 参考线选项 ==========
        if (ImGui.collapsingHeader(Icons.GRID + " 参考线与网格")) {
            ImGui.indent();
            renderGuideLinesSection(profile, modifiedRef);
            ImGui.unindent();
        }
        
        return modifiedRef[0];
    }
    
    // ========== 画幅比例部分 ==========
    private void renderAspectRatioSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        CameraViewportProfile.AspectRatioMode[] ratioModes = 
            CameraViewportProfile.AspectRatioMode.values();
        String[] ratioLabels = new String[ratioModes.length];
        for (int i = 0; i < ratioModes.length; i++) {
            ratioLabels[i] = ratioModes[i].getDisplayName();
        }
        
        ImInt ratioIdx = new ImInt(profile.getAspectMode().ordinal());
        if (ImGui.combo("画幅比例", ratioIdx, ratioLabels)) {
            profile.setAspectMode(ratioModes[ratioIdx.get()]);
            modifiedRef[0] = true;
            LOGGER.debug("[CameraTrackPropertyAdapter] 画幅比例已更新: {}", ratioLabels[ratioIdx.get()]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("决定画面的宽高比，影响构图和预览显示");
        }
        
        // 快捷预设按钮
        UITheme.spacingSmall();
        if (ImGui.button("16:9 横屏", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_16_9);
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button("9:16 竖屏", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_9_16);
            profile.setPreviewResolution(1080, 1920);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button("1:1 方形", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setAspectMode(CameraViewportProfile.AspectRatioMode.RATIO_1_1);
            profile.setPreviewResolution(1080, 1080);
            modifiedRef[0] = true;
        }
        
        // 自定义画幅比例
        if (profile.getAspectMode() == CameraViewportProfile.AspectRatioMode.CUSTOM) {
            UITheme.spacingSmall();
            int[] customRatio = {profile.getCustomAspectW(), profile.getCustomAspectH()};
            if (ImGui.inputInt2("自定义比例 (宽:高)", customRatio)) {
                profile.setCustomAspectW(customRatio[0]);
                profile.setCustomAspectH(customRatio[1]);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("自定义画幅比例的宽度和高度");
            }
        }
        
        // 显示当前比例
        float ratio = profile.getAspectRatio();
        int w = profile.getEffectiveAspectW();
        int h = profile.getEffectiveAspectH();
        UITheme.spacingSmall();
        UITheme.label(String.format("当前比例: %d:%d (%.2f:1)", w, h, ratio), UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " 注意：最终导出分辨率在导出设置中配置", UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    // ========== 安全区部分 ==========
    private void renderSafeAreaSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showSafeAreas = new ImBoolean(profile.isShowSafeAreas());
        if (ImGui.checkbox("显示安全区", showSafeAreas)) {
            profile.setShowSafeAreas(showSafeAreas.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("显示动作安全区和标题安全区");
        }
        
        if (profile.isShowSafeAreas()) {
            UITheme.spacingSmall();
            
            // 动作安全区（保留比例，0~1）
            float[] actionSafe = {profile.getActionSafe() * 100.0f};
            if (ImGui.dragFloat("动作安全区 (%)", actionSafe, 0.5f, 50.0f, 100.0f, "%.1f%%")) {
                profile.setActionSafe(actionSafe[0] / 100.0f);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("动作安全区：确保重要动作不会被裁切（通常为 90%）");
            }
            
            UITheme.spacingSmall();
            
            // 标题安全区（保留比例，0~1）
            float[] titleSafe = {profile.getTitleSafe() * 100.0f};
            if (ImGui.dragFloat("标题安全区 (%)", titleSafe, 0.5f, 50.0f, 100.0f, "%.1f%%")) {
                profile.setTitleSafe(titleSafe[0] / 100.0f);
                modifiedRef[0] = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标题安全区：确保文字不会被裁切（通常为 80%，应 ≤ 动作安全区）");
            }
        }
        
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " 安全区用于确保内容在不同设备上显示时不会被裁切", UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    // ========== 预览分辨率部分 ==========
    private void renderPreviewResolutionSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        int[] previewRes = {profile.getPreviewWidth(), profile.getPreviewHeight()};
        if (ImGui.inputInt2("预览分辨率 (宽×高)", previewRes)) {
            profile.setPreviewResolution(previewRes[0], previewRes[1]);
            modifiedRef[0] = true;
            LOGGER.debug("[CameraTrackPropertyAdapter] 预览分辨率已更新: {}×{}", previewRes[0], previewRes[1]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("用于预览时的分辨率，不影响最终导出");
        }
        
        // 快捷预设
        UITheme.spacingSmall();
        if (ImGui.button("1080p (1920×1080)", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1920, 1080);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button("720p (1280×720)", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(1280, 720);
            modifiedRef[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button("4K (3840×2160)", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            profile.setPreviewResolution(3840, 2160);
            modifiedRef[0] = true;
        }
        
        // 预览缩放策略
        UITheme.spacingSmall();
        String[] scalePolicyLabels = {"FIT (留黑边)", "FILL (填满)"};
        ImInt scalePolicyIdx = new ImInt(profile.getPreviewScalePolicy().ordinal());
        if (ImGui.combo("预览缩放策略", scalePolicyIdx, scalePolicyLabels)) {
            profile.setPreviewScalePolicy(CameraViewportProfile.PreviewScalePolicy.values()[scalePolicyIdx.get()]);
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("FIT: 保持画幅完整呈现（留黑边）\nFILL: 填满窗口（可能裁切）");
        }
        
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " 预览分辨率仅用于提高预览真实感，不影响最终导出", UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    // ========== 预览选项部分 ==========
    private void renderPreviewOptionsSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showLetterbox = new ImBoolean(profile.isShowLetterboxMask());
        if (ImGui.checkbox("显示 Letterbox / Mask 预览", showLetterbox)) {
            profile.setShowLetterboxMask(showLetterbox.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("当世界窗口与摄像机画幅不匹配时，显示黑边预览");
        }
        
        if (profile.isShowLetterboxMask()) {
            ImGui.indent();
            float[] alpha = {profile.getLetterboxAlpha()};
            if (ImGui.sliderFloat("遮罩透明度", alpha, 0.0f, 1.0f, "%.2f")) {
                profile.setLetterboxAlpha(alpha[0]);
                modifiedRef[0] = true;
            }
            ImGui.unindent();
        }
        
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " Letterbox 预览帮助你在构图时直观看到最终画面效果", UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    // ========== 参考线部分 ==========
    private void renderGuideLinesSection(CameraViewportProfile profile, final boolean[] modifiedRef) {
        ImBoolean showCenterCross = new ImBoolean(profile.isShowCenterCross());
        if (ImGui.checkbox("显示中心十字参考线", showCenterCross)) {
            profile.setShowCenterCross(showCenterCross.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在画面中心显示十字参考线，帮助对齐构图");
        }
        
        UITheme.spacingSmall();
        
        ImBoolean showRuleOfThirds = new ImBoolean(profile.isShowRuleOfThirds());
        if (ImGui.checkbox("显示三分法网格", showRuleOfThirds)) {
            profile.setShowRuleOfThirds(showRuleOfThirds.get());
            modifiedRef[0] = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("显示三分法网格线，帮助构图和主体定位");
        }
        
        UITheme.spacingSmall();
        UITheme.label(Icons.LIGHTBULB + " 参考线仅用于构图辅助，不会出现在最终导出中", UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    @Override
    public String getAdapterName() {
        return "摄像机轨道属性编辑器";
    }
}
