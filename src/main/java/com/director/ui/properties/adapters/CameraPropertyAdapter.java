package com.director.ui.properties.adapters;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 相机属性适配器 v4.9 - Director Properties System
 */
public class CameraPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPropertyAdapter.class);

    private static final float[] FOV_PRESETS = {60.0f, 70.0f, 90.0f, 110.0f};

    @Override
    public boolean renderProperties(Object camera) {
        if (camera == null) {
            UITheme.spacingLarge();
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.NULL_CAMERA),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        final boolean[] modifiedRef = {false};

        UITheme.title(Icons.CAMERA + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.TITLE));

        try {
            if (ImGui.collapsingHeader(Icons.PIN + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SECTION_TRANSFORM),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                Object position = PropertyReflection.readProperty(camera, "position", "getPosition");
                if (position != null) {
                    var posAdapter = PropertyAdapterRegistry.getAdapterFor(position);
                    if (posAdapter != null) {
                        UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.POSITION),
                                UITheme.DarkTheme.TEXT);
                        if (posAdapter.renderProperties(position)) {
                            modifiedRef[0] = true;
                        }
                    }
                }

                UITheme.spacingSmall();

                Object rotation = PropertyReflection.readProperty(camera, "rotation", "getRotation");
                if (rotation != null) {
                    var rotAdapter = PropertyAdapterRegistry.getAdapterFor(rotation);
                    if (rotAdapter != null) {
                        UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ROTATION),
                                UITheme.DarkTheme.TEXT);
                        if (rotAdapter.renderProperties(rotation)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                ImGui.unindent();
            }

            if (ImGui.collapsingHeader(Icons.CAMERA + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SECTION_PROJECTION),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                float currentFOV = PropertyReflection.getFloat(camera, "fov", "getFov", 70.0f);
                float[] fov = {currentFOV};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FOV),
                        fov, 0.5f, 10.0f, 150.0f, "%.1f°")) {
                    if (PropertyReflection.setFloat(camera, "fov", "setFov", fov[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] FOV已更新: {}", fov[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FOV_TOOLTIP));
                }

                UITheme.spacingSmall();
                UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FOV_PRESETS),
                        UITheme.DarkTheme.TEXT);
                String[] fovPresetNames = CameraPropertyAdapterI18n.fovPresetLabels();
                for (int i = 0; i < FOV_PRESETS.length; i++) {
                    ImGui.pushID("fov_" + i);
                    if (ImGui.button(fovPresetNames[i], 90, UITheme.Spacing.BUTTON_HEIGHT)) {
                        if (PropertyReflection.setFloat(camera, "fov", "setFov", FOV_PRESETS[i])) {
                            modifiedRef[0] = true;
                            LOGGER.debug("[CameraPropertyAdapter] 应用FOV预设: {}", FOV_PRESETS[i]);
                        }
                    }
                    if ((i + 1) % 2 != 0 && i < FOV_PRESETS.length - 1) {
                        ImGui.sameLine();
                    }
                    ImGui.popID();
                }

                UITheme.spacingSmall();

                float currentNear = PropertyReflection.getFloat(camera, "nearPlane", "getNearPlane", 0.1f);
                float[] nearPlane = {currentNear};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.NEAR),
                        nearPlane, 0.01f, 0.01f, 10.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(camera, "nearPlane", "setNearPlane", nearPlane[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 近裁剪面已更新: {}", nearPlane[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.NEAR_TOOLTIP));
                }

                float currentFar = PropertyReflection.getFloat(camera, "farPlane", "getFarPlane", 1000.0f);
                float[] farPlane = {currentFar};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FAR),
                        farPlane, 10.0f, 10.0f, 10000.0f, "%.0f")) {
                    if (PropertyReflection.setFloat(camera, "farPlane", "setFarPlane", farPlane[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 远裁剪面已更新: {}", farPlane[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FAR_TOOLTIP));
                }
                ImGui.unindent();
            }

            if (ImGui.collapsingHeader(Icons.IMAGE + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SECTION_LENS))) {
                ImGui.indent();
                boolean currentDOF = PropertyReflection.getBoolean(camera, "depthOfFieldEnabled", "isDepthOfFieldEnabled", false);
                ImBoolean enableDOF = new ImBoolean(currentDOF);
                if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.DOF_ENABLED), enableDOF)) {
                    if (PropertyReflection.setBoolean(camera, "depthOfFieldEnabled", "setDepthOfFieldEnabled", enableDOF.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 景深已更新: {}", enableDOF.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.DOF_TOOLTIP));
                }

                float currentFocus = PropertyReflection.getFloat(camera, "focusDistance", "getFocusDistance", 10.0f);
                float[] focusDistance = {currentFocus};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FOCUS),
                        focusDistance, 0.5f, 0.1f, 1000.0f, "%.1f")) {
                    if (PropertyReflection.setFloat(camera, "focusDistance", "setFocusDistance", focusDistance[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 焦距已更新: {}", focusDistance[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.FOCUS_TOOLTIP));
                }

                float currentAperture = PropertyReflection.getFloat(camera, "aperture", "getAperture", 2.8f);
                float[] aperture = {currentAperture};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.APERTURE),
                        aperture, 0.1f, 1.4f, 22.0f, "f/%.1f")) {
                    if (PropertyReflection.setFloat(camera, "aperture", "setAperture", aperture[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 光圈已更新: {}", aperture[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.APERTURE_TOOLTIP));
                }

                int currentBokeh = PropertyReflection.getInt(camera, "bokehQuality", "getBokehQuality", 2);
                int[] bokehQuality = {currentBokeh};
                if (ImGui.dragInt(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.BOKEH),
                        bokehQuality, 1.0f, 0, 5)) {
                    if (PropertyReflection.setInt(camera, "bokehQuality", "setBokehQuality", bokehQuality[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 散景质量已更新: {}", bokehQuality[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.BOKEH_TOOLTIP));
                }
                ImGui.unindent();
            }

            if (ImGui.collapsingHeader(Icons.FILM + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SECTION_ANIMATION))) {
                ImGui.indent();
                String currentName = PropertyReflection.getString(camera, "animationName", "getAnimationName", "");
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ANIMATION_NAME),
                        nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(camera, "animationName", "setAnimationName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                    }
                }

                float currentDuration = PropertyReflection.getFloat(camera, "animationDuration", "getAnimationDuration", 5.0f);
                float[] duration = {currentDuration};
                if (ImGui.dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ANIMATION_DURATION),
                        duration, 0.1f, 0.1f, 300.0f)) {
                    if (PropertyReflection.setFloat(camera, "animationDuration", "setAnimationDuration", duration[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 动画持续时间已更新: {}", duration[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterCamera.Object.ANIMATION_DURATION_TOOLTIP));
                }

                boolean currentLoop = PropertyReflection.getBoolean(camera, "loop", "isLoop", false);
                ImBoolean loop = new ImBoolean(currentLoop);
                if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.LOOP), loop)) {
                    if (PropertyReflection.setBoolean(camera, "loop", "setLoop", loop.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 循环播放已更新: {}", loop.get());
                    }
                }

                boolean currentSmooth = PropertyReflection.getBoolean(camera, "smoothInterpolation", "isSmoothInterpolation", true);
                ImBoolean smooth = new ImBoolean(currentSmooth);
                if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SMOOTH), smooth)) {
                    if (PropertyReflection.setBoolean(camera, "smoothInterpolation", "setSmoothInterpolation", smooth.get())) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.SMOOTH_TOOLTIP));
                }
                ImGui.unindent();
            }

            UITheme.separator();

            UITheme.beginGroup(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.QUICK_ACTIONS));

            if (ImGui.button(Icons.REFRESH + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.RESET),
                    120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(camera, "reset") || PropertyReflection.invokeVoid(camera, "resetToDefault")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[CameraPropertyAdapter] 相机已重置");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.RESET_TOOLTIP));
            }

            ImGui.sameLine();

            if (ImGui.button(Icons.CLIPBOARD + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.COPY),
                    120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[CameraPropertyAdapter] 复制相机设置");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.COPY_TOOLTIP));
            }

            ImGui.sameLine();

            if (ImGui.button(Icons.HAND + " "
                    + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ALIGN_PLAYER),
                    120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(camera, "alignToPlayer")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[CameraPropertyAdapter] 相机已对齐到玩家");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ALIGN_PLAYER_TOOLTIP));
            }

            UITheme.endGroup();

        } catch (Exception e) {
            LOGGER.error("[CameraPropertyAdapter] 渲染相机属性时出错", e);
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.RENDER_ERROR, e.getMessage()),
                    UITheme.DarkTheme.TEXT_DISABLED);
        }

        return modifiedRef[0];
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public String getAdapterName() {
        return CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Object.ADAPTER_NAME);
    }

    @Override
    public boolean supports(Object target) {
        if (target == null) {
            return false;
        }

        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("camera");
    }
}
