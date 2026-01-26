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
import imgui.type.ImBoolean;
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
    private final ImFloat wetnessFloat = new ImFloat();
    private final ImFloat thunderFloat = new ImFloat();
    private final ImFloat fogFloat = new ImFloat();
    private final ImFloat windFloat = new ImFloat();
    private final ImFloat lightningFloat = new ImFloat();
    private final ImFloat stormDarknessFloat = new ImFloat();
    private final ImFloat cloudDensityFloat = new ImFloat();
    private final ImFloat cloudSpeedFloat = new ImFloat();
    private final ImFloat cloudBrightnessFloat = new ImFloat();
    private final ImFloat windDirectionFloat = new ImFloat();
    private final ImBoolean visualOnlyThunderBool = new ImBoolean();

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
            ImGui.indent();
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
            ImGui.unindent();
        }

        // ==========================================================
        // 🌧 降水 (Precipitation)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.PARTICLE + " 降水 (Precipitation)", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

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
            UITheme.spacingSmall();

            // 地面湿度
            wetnessFloat.set(kf.getWetness());
            if (ImGui.sliderFloat("地面湿度", wetnessFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setPuddleLevel(wetnessFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制地面潮湿感和水滴效果，0 = 干燥，1 = 很湿");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🌫 雾 (Fog) - ⚠️ 已禁用：雾功能无法实现理想效果
        // ==========================================================
        /*
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
        */

        // ==========================================================
        // 云层 (Clouds)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.LAYERS + " 云层 (Clouds)", ImGuiTreeNodeFlags.DefaultOpen)) {

            cloudDensityFloat.set(kf.getCloudDensity());
            if (ImGui.sliderFloat("云量", cloudDensityFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setCloudDensity(cloudDensityFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制云层覆盖度和天空阴影感，0 = 晴朗，1 = 厚云");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            cloudSpeedFloat.set(kf.getCloudSpeed());
            if (ImGui.sliderFloat("云速", cloudSpeedFloat.getData(), 0.0f, 3.0f, "%.2f")) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setCloudSpeed(cloudSpeedFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制云层移动速度倍数");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // ✅ 新增：云亮度参数
            cloudBrightnessFloat.set(kf.getCloudBrightness());
            if (ImGui.sliderFloat("云亮度", cloudBrightnessFloat.getData(), 0.3f, 1.2f, "%.2f")) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setCloudBrightness(cloudBrightnessFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("控制云的颜色亮度，0.3 = 很暗，1.0 = 正常，1.2 = 很亮");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        // ==========================================================
        // 🌬 风 (Wind)
        // ==========================================================
        if (ImGui.collapsingHeader("\uD83C\uDF2C 风 (Wind)", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

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

            // ✅ 新增：风方向可视化控件（方向盘）
            windDirectionFloat.set(kf.getWindDirectionDegrees());
            float[] windDirArr = { windDirectionFloat.get() };
            drawWindDirectionDial("风方向", windDirArr);
            if (Math.abs(windDirArr[0] - windDirectionFloat.get()) > 0.1f) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setWindDirectionDegrees(windDirArr[0]);
                windDirectionFloat.set(windDirArr[0]);
                parameterChanged.set(true);
            }

            UITheme.spacingSmall();

            // 角度数值滑块（精确调整）
            if (ImGui.sliderFloat("方向角 (°)", windDirectionFloat.getData(), 0.0f, 360.0f, "%.0f°")) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setWindDirectionDegrees(windDirectionFloat.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("0° = 东（+X），90° = 南（+Z），180° = 西（-X），270° = 北（-Z）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        // ==========================================================
        // ⛈ 雷暴 (Thunderstorm)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.LIGHTNING + " 雷暴 (Thunderstorm)")) {
            ImGui.indent();

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
            ImGui.separator();
            UITheme.spacingSmall();

            // ✅ 新增：只视觉雷暴（导演安全模式）开关
            visualOnlyThunderBool.set(kf.isVisualOnlyThunder());
            if (ImGui.checkbox("只视觉雷暴（导演安全模式）", visualOnlyThunderBool)) {
                if (kf.getWeatherType() != WeatherKeyframe.WeatherType.CUSTOM) {
                    kf.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                }
                kf.setVisualOnlyThunder(visualOnlyThunderBool.get());
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.textWrapped("开启：只播放闪白和雷声，不会生成真实闪电或破坏世界（推荐）\n" +
                        "关闭：生成真实 LightningEntity，可能点火、影响世界（慎用）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        // ==========================================================
        // 🌌 天空/光照 (Sky / Lighting)
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.LIGHT + " 天空/光照 (Sky / Lighting)")) {
            ImGui.indent();

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
            ImGui.unindent();
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

    /**
     * ✅ 绘制风方向可视化控件（方向盘）。
     * 
     * <p>使用 ImGui 的绘制 API 创建一个可交互的方向盘：
     * - 圆形表示方向环
     * - 指针表示当前风向
     * - 鼠标拖拽可调整方向
     * 
     * @param label 控件标签
     * @param angleDeg 角度值（度），会被修改
     */
    private void drawWindDirectionDial(String label, float[] angleDeg) {
        final float radius = 40.0f;
        final float diameter = radius * 2.0f;

        ImGui.text(label);

        ImGui.pushID(label);
        
        // 创建一个不可见按钮作为交互区域
        ImGui.invisibleButton("##wind_dir", diameter, diameter);

        // 获取绘制位置中心点
        float cx = ImGui.getItemRectMinX() + radius;
        float cy = ImGui.getItemRectMinY() + radius;

        var drawList = ImGui.getWindowDrawList();

        // 绘制圆盘背景
        drawList.addCircle(cx, cy, radius, 0xFFAAAAAA, 32, 2.0f);

        // 绘制方向标记（东、南、西、北）
        float markerLength = 8.0f;
        // 东（0°）
        drawList.addLine(cx + radius - markerLength, cy, cx + radius, cy, 0xFF888888, 1.5f);
        // 南（90°）
        drawList.addLine(cx, cy + radius - markerLength, cx, cy + radius, 0xFF888888, 1.5f);
        // 西（180°）
        drawList.addLine(cx - radius + markerLength, cy, cx - radius, cy, 0xFF888888, 1.5f);
        // 北（270°）
        drawList.addLine(cx, cy - radius + markerLength, cx, cy - radius, 0xFF888888, 1.5f);

        // 当前角度 → 方向向量
        // 注意：ImGui 的 Y 轴向下，所以需要调整
        double rad = Math.toRadians(angleDeg[0] - 90.0); // -90° 让 0° 指向右（东）
        float dx = (float) Math.cos(rad);
        float dy = (float) Math.sin(rad);

        // 绘制指针（从中心指向方向）
        drawList.addLine(
                cx,
                cy,
                cx + dx * (radius - 5.0f),
                cy + dy * (radius - 5.0f),
                0xFF00CCFF,
                3.0f
        );

        // 绘制中心点
        drawList.addCircleFilled(cx, cy, 3.0f, 0xFF00CCFF, 8);

        // 鼠标交互：拖拽调整方向
        if (ImGui.isItemActive()) {
            float mx = ImGui.getIO().getMousePosX();
            float my = ImGui.getIO().getMousePosY();

            float vx = mx - cx;
            float vy = my - cy;

            // 计算角度（注意：ImGui Y 轴向下，所以 atan2 的参数顺序）
            float newAngle = (float) Math.toDegrees(Math.atan2(vy, vx));
            // 转换为我们的坐标系（0° = 东，顺时针为正）
            newAngle = newAngle + 90.0f;
            // 归一化到 [0, 360)
            while (newAngle < 0f) newAngle += 360f;
            while (newAngle >= 360f) newAngle -= 360f;

            angleDeg[0] = newAngle;
        }

        ImGui.popID();
    }

    @Override
    public Class<WeatherKeyframe> getTargetType() {
        return WeatherKeyframe.class;
    }
}
