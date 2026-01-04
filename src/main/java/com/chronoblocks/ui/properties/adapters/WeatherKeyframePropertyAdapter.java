package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WeatherKeyframePropertyAdapter v1.0
 * <p>
 * 提供天气关键帧属性编辑界面，包括：
 *  - 雨量
 *  - 雪量
 *  - 雷暴强度
 *  - 云量
 *  - 雾浓度
 *  - 湿度（地面积水）
 *  - 风力
 *  - 雾颜色 Tint
 *  - 天气预设（晴天/雨/雪/雷暴/雾）
 *  - 插值方式（步进/线性/平滑）
 * <p>
 * 改动后自动触发 TimelineWeatherController 刷新采样器 + 预览
 *
 * @author ChronoBlocks
 */
public class WeatherKeyframePropertyAdapter implements IPropertyAdapter<WeatherKeyframe> {

    private static final String TITLE = Icons.LIGHTNING + " 天气关键帧";

    // ImGui 临时对象
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat rainFloat = new ImFloat();
    private final ImFloat snowFloat = new ImFloat();
    private final ImFloat thunderFloat = new ImFloat();
    private final ImFloat cloudFloat = new ImFloat();
    private final ImFloat fogFloat = new ImFloat();
    private final ImFloat wetFloat = new ImFloat();
    private final ImFloat windFloat = new ImFloat();

    private final float[] fogTintArr = new float[3];

    private final ImInt weatherPresetIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();

    private static final String[] PRESET_LABELS = buildPresetLabels();
    private static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();

    private static String[] buildPresetLabels() {
        WeatherKeyframe.WeatherType[] types = WeatherKeyframe.WeatherType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    private static String[] buildInterpolationLabels() {
        com.chronoblocks.ui.timeline.model.TimelineInterpolation[] types = 
            com.chronoblocks.ui.timeline.model.TimelineInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    @Override
    public boolean renderProperties(WeatherKeyframe kf) {
        if (kf == null) {
            UITheme.label("(未选中天气关键帧)", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        // ⭐ v1.1：提取公共对象，减少重复获取
        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        
        // ⭐ v1.0：检查关键帧是否属于激活的天气轨道
        TimelineTrack keyframeTrack = null;
        if (timelineModel != null) {
            keyframeTrack = timelineModel.findTrackForKeyframe(kf);
        }
        
        TimelineTrack activeTrack = weatherController.getActiveWeatherTrack();
        boolean isTrackActive = (keyframeTrack != null && keyframeTrack == activeTrack);
        
        if (!isTrackActive) {
            UITheme.title(TITLE);
            UITheme.separator();
            UITheme.label(Icons.WARNING + " 此关键帧不属于激活的天气轨道", UITheme.DarkTheme.TEXT_WARNING);
            ImGui.textDisabled("当前激活轨道: " + (activeTrack != null ? activeTrack.getName() : "<无>"));
            if (keyframeTrack != null) {
                ImGui.textDisabled("关键帧所在轨道: " + keyframeTrack.getName());
            }
            UITheme.spacingMedium();
            UITheme.label("提示：只有激活轨道上的关键帧才能被编辑和影响世界中的天气。", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("请点击轨道头部的激活按钮来切换激活轨道。", UITheme.DarkTheme.TEXT_DISABLED);
            return false; // 禁用编辑
        }

        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean parameterChanged = new AtomicBoolean(false);

        UITheme.title(TITLE);

        // ==========================================================
        // 基础信息
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(kf.getTime());
            if (ImGui.dragFloat("时间 (秒)", timeFloat.getData(), 0.02f, 0, 10_000f, "%.2f")) {
                kf.setTime(timeFloat.get());
                timeChanged.set(true);
            }
            UITheme.spacingSmall();
        }

        // ==========================================================
        // 天气预设
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 天气预设", ImGuiTreeNodeFlags.DefaultOpen)) {

            weatherPresetIndex.set(kf.getWeatherType().ordinal());
            WeatherKeyframe.WeatherType[] types = WeatherKeyframe.WeatherType.values();

            if (ImGui.combo("模式", weatherPresetIndex, PRESET_LABELS)) {
                WeatherKeyframe.WeatherType newType = types[weatherPresetIndex.get()];
                kf.applyWeatherPreset(newType); // 自动写入默认雨量/云量等
                parameterChanged.set(true);
            }

            ImGui.textDisabled("• 选择一个天气模式并自动应用参数");
            ImGui.textDisabled("• 可在下方手动微调");
            UITheme.spacingSmall();
        }

        // ==========================================================
        // 天气强度参数
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.PARTICLE + " 天气强度", ImGuiTreeNodeFlags.DefaultOpen)) {

            // 雨（通过 rainStrength + rainType 控制）
            rainFloat.set(kf.getRainIntensity());
            if (ImGui.sliderFloat("雨量", rainFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                float value = rainFloat.get();
                if (value > 0.01f) {
                    kf.setRainType(WeatherKeyframe.RainType.RAIN);
                    kf.setRainStrength(value);
                } else {
                    kf.setRainStrength(0f);
                }
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 0 = 无雨，1 = 暴雨");

            // 雪（通过 rainStrength + rainType 控制）
            snowFloat.set(kf.getSnowIntensity());
            if (ImGui.sliderFloat("雪量", snowFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                float value = snowFloat.get();
                if (value > 0.01f) {
                    kf.setRainType(WeatherKeyframe.RainType.SNOW);
                    kf.setRainStrength(value);
                } else {
                    kf.setRainStrength(0f);
                }
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 0 = 无雪，1 = 暴雪");

            // 雷暴（通过 thunderStrength 控制）
            thunderFloat.set(kf.getThunderIntensity());
            if (ImGui.sliderFloat("雷暴", thunderFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                kf.setThunderStrength(thunderFloat.get());
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 0 = 无雷，1 = 频繁闪电");

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 云 / 雾 / 湿度 / 风力
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.FILTER + " 云雾与环境", ImGuiTreeNodeFlags.DefaultOpen)) {

            // 云（通过 cloudDensity 控制）
            cloudFloat.set(kf.getCloudCoverage());
            if (ImGui.sliderFloat("云量", cloudFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式，避免预设覆盖手动调整
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setCloudDensity(cloudFloat.get());
                parameterChanged.set(true);
            }

            // 雾
            fogFloat.set(kf.getFogDensity());
            if (ImGui.sliderFloat("雾浓度", fogFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setFogDensity(fogFloat.get());
                parameterChanged.set(true);
            }

            // 湿度（通过 puddleLevel 控制）
            wetFloat.set(kf.getWetness());
            if (ImGui.sliderFloat("地面湿度", wetFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setPuddleLevel(wetFloat.get());
                parameterChanged.set(true);
            }

            // 风
            windFloat.set(kf.getWindStrength());
            if (ImGui.sliderFloat("风力", windFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setWindStrength(windFloat.get());
                parameterChanged.set(true);
            }

            UITheme.spacingSmall();

            // 雾颜色（使用 fogColor，不是 fogTint）
            Vector3f fogColor = kf.getFogColor();
            fogTintArr[0] = fogColor.x;
            fogTintArr[1] = fogColor.y;
            fogTintArr[2] = fogColor.z;

            int flags = ImGuiColorEditFlags.DisplayRGB | ImGuiColorEditFlags.InputRGB | ImGuiColorEditFlags.NoAlpha;
            if (ImGui.colorEdit3("雾颜色", fogTintArr, flags)) {
                kf.setFogColor(fogTintArr[0], fogTintArr[1], fogTintArr[2]);
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 雾的绝对颜色值");

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 插值方式
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.INTERPOLATION + " 插值方式", ImGuiTreeNodeFlags.DefaultOpen)) {

            interpolationIndex.set(kf.getInterpolation().ordinal());
            com.chronoblocks.ui.timeline.model.TimelineInterpolation[] types = 
                com.chronoblocks.ui.timeline.model.TimelineInterpolation.values();

            if (ImGui.combo("插值模式", interpolationIndex, INTERPOLATION_LABELS)) {
                kf.setInterpolation(types[interpolationIndex.get()]);
                parameterChanged.set(true);
            }

            ImGui.textDisabled("• 线性：匀速变化");
            ImGui.textDisabled("• 平滑：缓入缓出");
            ImGui.textDisabled("• 步进：瞬间切换");
            UITheme.spacingSmall();
        }

        // ==========================================================
        // 触发天气控制器刷新
        // ==========================================================

        // ⭐ v1.1：参数变化时预览关键帧所在位置（与其他适配器保持一致）
        // 这样可以确保设置的值能够立即显示，不会被播放头位置的采样结果覆盖
        if (parameterChanged.get()) {
            weatherController.rebuildFromActiveTrack();
            // 预览关键帧所在位置，而不是当前播放头位置
            weatherController.previewAt(kf.getTime());
        }
        
        // ⭐ v1.1：时间变化只重建采样器，不触发预览
        // 这样拖动关键帧时间时，世界不会错误地跟随变化（与 TimeKeyframePropertyAdapter 保持一致）
        if (timeChanged.get()) {
            weatherController.rebuildFromActiveTrack();
            // 不调用预览，因为播放头位置没有改变
        }

        return parameterChanged.get() || timeChanged.get();
    }

    @Override
    public Class<WeatherKeyframe> getTargetType() {
        return WeatherKeyframe.class;
    }
}
