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
 * WeatherKeyframePropertyAdapter v2.0
 * 
 * <p>重构目标：导演级可用性
 * <ul>
 *   <li>✅ 参数完整：所有真正生效的天气参数都展示出来</li>
 *   <li>✅ 分组清晰：按导演思维进行参数分组 + 折叠</li>
 *   <li>✅ UI 语义清晰：知道每个参数"在画面里干什么"</li>
 *   <li>✅ 为后续扩展（风方向、云、特效）留好结构</li>
 * </ul>
 * 
 * <p>UI 结构：
 * <ul>
 *   <li>⏱ 基础信息（时间、预设、插值）</li>
 *   <li>🌧 降水（雨强度、雪强度）</li>
 *   <li>🌫 雾（雾密度、雾颜色）</li>
 *   <li>🌬 风（风强度）</li>
 *   <li>⛈ 雷暴（雷暴强度、闪电频率）</li>
 *   <li>🌌 天空/光照（天空变暗、暴风雨变暗）</li>
 * </ul>
 * 
 * <p>改动后自动触发 TimelineWeatherController 刷新采样器 + 预览
 *
 * @author ChronoBlocks
 * @version 2.0 - 导演级可用性重构
 */
public class WeatherKeyframePropertyAdapter implements IPropertyAdapter<WeatherKeyframe> {

    private static final String TITLE = Icons.LIGHTNING + " 天气关键帧";

    // ImGui 临时对象
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat rainFloat = new ImFloat();
    private final ImFloat snowFloat = new ImFloat();
    private final ImFloat thunderFloat = new ImFloat();
    private final ImFloat fogFloat = new ImFloat();
    private final ImFloat windFloat = new ImFloat();
    private final ImFloat lightningFloat = new ImFloat();
    private final ImFloat skyDarknessFloat = new ImFloat();
    private final ImFloat stormDarknessFloat = new ImFloat();

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
        // ⏱ 基础信息
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(kf.getTime());
            if (ImGui.dragFloat("时间 (秒)", timeFloat.getData(), 0.02f, 0, 10_000f, "%.2f")) {
                kf.setTime(timeFloat.get());
                timeChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("关键帧在时间线上的位置");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 天气预设
            weatherPresetIndex.set(kf.getWeatherType().ordinal());
            WeatherKeyframe.WeatherType[] types = WeatherKeyframe.WeatherType.values();

            if (ImGui.combo("天气预设", weatherPresetIndex, PRESET_LABELS)) {
                WeatherKeyframe.WeatherType newType = types[weatherPresetIndex.get()];
                kf.applyWeatherPreset(newType); // 自动写入默认雨量/云量等
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.textWrapped("选择一个天气模式并自动应用参数，可在下方手动微调");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 插值方式
            interpolationIndex.set(kf.getInterpolation().ordinal());
            com.chronoblocks.ui.timeline.model.TimelineInterpolation[] interpolationTypes = 
                com.chronoblocks.ui.timeline.model.TimelineInterpolation.values();

            if (ImGui.combo("插值模式", interpolationIndex, INTERPOLATION_LABELS)) {
                kf.setInterpolation(interpolationTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("线性：匀速变化 | 平滑：缓入缓出 | 步进：瞬间切换");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🌧 降水 (Precipitation)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.PARTICLE + " 降水 (Precipitation)", ImGuiTreeNodeFlags.DefaultOpen)) {

            // 雨强度
            rainFloat.set(kf.getRainIntensity());
            if (ImGui.sliderFloat("雨强度", rainFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                float value = rainFloat.get();
                if (value > 0.01f) {
                    kf.setRainType(WeatherKeyframe.RainType.RAIN);
                    kf.setRainStrength(value);
                } else {
                    kf.setRainStrength(0f);
                }
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制雨量密度和视觉厚度，0 = 无雨，1 = 暴雨");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 雪强度
            snowFloat.set(kf.getSnowIntensity());
            if (ImGui.sliderFloat("雪强度", snowFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                float value = snowFloat.get();
                if (value > 0.01f) {
                    kf.setRainType(WeatherKeyframe.RainType.SNOW);
                    kf.setRainStrength(value);
                } else {
                    kf.setRainStrength(0f);
                }
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制雪花数量与覆盖感，0 = 无雪，1 = 暴雪");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🌫 雾 (Fog)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.FILTER + " 雾 (Fog)", ImGuiTreeNodeFlags.DefaultOpen)) {

            // 雾密度
            fogFloat.set(kf.getFogDensity());
            if (ImGui.sliderFloat("雾密度", fogFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setFogDensity(fogFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制可视距离与雾浓度，0 = 无雾，1 = 浓雾");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 雾颜色
            Vector3f fogColor = kf.getFogColor();
            fogTintArr[0] = fogColor.x;
            fogTintArr[1] = fogColor.y;
            fogTintArr[2] = fogColor.z;

            int flags = ImGuiColorEditFlags.DisplayRGB | ImGuiColorEditFlags.InputRGB | ImGuiColorEditFlags.NoAlpha;
            if (ImGui.colorEdit3("雾颜色", fogTintArr, flags)) {
                kf.setFogColor(fogTintArr[0], fogTintArr[1], fogTintArr[2]);
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("雾的绝对颜色值，影响场景的整体色调");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🌬 风 (Wind)
        // ==========================================================
        if (ImGui.collapsingHeader("风 (Wind)")) {

            // 风强度
            windFloat.set(kf.getWindStrength());
            if (ImGui.sliderFloat("风强度", windFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                // ⭐ 如果当前不是自定义模式，切换到自定义模式
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setWindStrength(windFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("影响雨雪的偏斜程度与飘动感，0 = 无风，1 = 狂风");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // ⛈ 雷暴 (Thunderstorm)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.LIGHTNING + " 雷暴 (Thunderstorm)")) {

            // 雷暴强度
            thunderFloat.set(kf.getThunderIntensity());
            if (ImGui.sliderFloat("雷暴强度", thunderFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                kf.setThunderStrength(thunderFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制闪电、雷声、整体压暗效果，0 = 无雷，1 = 频繁闪电");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 闪电频率
            lightningFloat.set(kf.getLightningFrequency());
            if (ImGui.sliderFloat("闪电频率", lightningFloat.getData(), 0.0f, 5.0f, "%.2f")) {
                kf.setLightningFrequency(lightningFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("每秒可能触发闪电的概率因子，值越高闪电越频繁");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🌌 天空/光照 (Sky / Lighting)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.LIGHT + " 天空/光照 (Sky / Lighting)")) {

            // 天空变暗（使用 stormDarkness，这是现有的字段）
            stormDarknessFloat.set(kf.getStormDarkness());
            if (ImGui.sliderFloat("天空变暗", stormDarknessFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                kf.setStormDarkness(stormDarknessFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("整体天空亮度降低（阴天效果），0 = 正常，1 = 完全黑");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 注意：WeatherKeyframe 中只有 stormDarkness，没有单独的 skyDarkness
            // 如果未来需要区分，可以在这里添加

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
