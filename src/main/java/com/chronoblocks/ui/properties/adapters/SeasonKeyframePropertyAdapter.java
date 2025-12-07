package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SeasonKeyframePropertyAdapter v4.0
 *
 * <p>新增功能：
 * <ul>
 *   <li>✔ 完整积雪控制（雪量/坡度影响/雪线高度/融化速度）</li>
 *   <li>✔ 冰冻控制（冰冻强度/阈值/融化速度）</li>
 *   <li>✔ 颜色 Tint 完整编辑（草、树叶、天空、雾、光照）</li>
 *   <li>✔ 气候参数（温度/湿度）</li>
 *   <li>✔ 插值方式（线性/平滑/曲线）</li>
 *   <li>✔ 季节预设按钮（春/夏/秋/冬/电影效果）</li>
 * </ul>
 *
 * <p>结构优化：
 * <ul>
 *   <li>✔ 颜色模块拆分</li>
 *   <li>✔ 气候模块拆分</li>
 *   <li>✔ 雪模块独立</li>
 *   <li>✔ 冰冻模块独立</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class SeasonKeyframePropertyAdapter implements IPropertyAdapter<SeasonKeyframe> {

    private static final String TITLE = "季节关键帧";

    // 临时变量对象（减少 GC）
    private final ImFloat timeFloat = new ImFloat();
    private final ImInt seasonTypeIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();
    
    // 气候参数
    private final ImFloat temperatureFloat = new ImFloat();
    private final ImFloat humidityFloat = new ImFloat();
    
    // 积雪参数
    private final ImFloat snowAmountFloat = new ImFloat();
    private final ImFloat snowSlopeFactorFloat = new ImFloat();
    private final ImFloat snowlineFloat = new ImFloat();
    private final ImFloat snowMeltFloat = new ImFloat();
    
    // 冰冻参数
    private final ImFloat freezeStrengthFloat = new ImFloat();
    private final ImFloat freezeThresholdFloat = new ImFloat();
    private final ImFloat freezeMeltSpeedFloat = new ImFloat();

    // Tint 颜色缓存 [0..1]
    private final float[] grassTintArr = new float[3];
    private final float[] foliageTintArr = new float[3];
    private final float[] skyTintArr = new float[3];
    private final float[] fogTintArr = new float[3];
    private final float[] lightTintArr = new float[3];

    private static final String[] SEASON_TYPE_LABELS = buildSeasonTypeLabels();
    private static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();

    private static String[] buildSeasonTypeLabels() {
        SeasonKeyframe.SeasonType[] t = SeasonKeyframe.SeasonType.values();
        String[] arr = new String[t.length];
        for (int i = 0; i < t.length; i++) arr[i] = t[i].getDisplayName();
        return arr;
    }

    private static String[] buildInterpolationLabels() {
        SeasonKeyframe.Interpolation[] t = SeasonKeyframe.Interpolation.values();
        String[] arr = new String[t.length];
        for (int i = 0; i < t.length; i++) arr[i] = t[i].getDisplayName();
        return arr;
    }

    @Override
    public boolean renderProperties(SeasonKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("（没有选中关键帧）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        // ⭐ v1.1：检查关键帧是否属于激活的季节轨道
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        TimelineTrack keyframeTrack = null;
        if (timelineModel != null) {
            keyframeTrack = timelineModel.findTrackForKeyframe(keyframe);
        }
        
        TimelineSeasonController seasonController = TimelineSeasonController.getInstance();
        TimelineTrack activeTrack = seasonController.getActiveSeasonTrack();
        
        boolean isTrackActive = (keyframeTrack != null && keyframeTrack == activeTrack);
        
        if (!isTrackActive) {
            UITheme.title(Icons.CALENDAR + " " + TITLE);
            UITheme.separator();
            UITheme.label(Icons.WARNING + " 此关键帧不属于激活的季节轨道", UITheme.DarkTheme.TEXT_WARNING);
            ImGui.textDisabled("当前激活轨道: " + (activeTrack != null ? activeTrack.getName() : "<无>"));
            if (keyframeTrack != null) {
                ImGui.textDisabled("关键帧所在轨道: " + keyframeTrack.getName());
            }
            UITheme.spacingMedium();
            UITheme.label("提示：只有激活轨道上的关键帧才能被编辑和影响世界中的季节。", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("请点击轨道头部的激活按钮来切换激活轨道。", UITheme.DarkTheme.TEXT_DISABLED);
            return false; // 禁用编辑
        }

        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean paramChanged = new AtomicBoolean(false);

        UITheme.title(Icons.CALENDAR + " " + TITLE);

        // ===========================================================
        // 1. 基础信息
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间位置", timeFloat.getData(), 0.05f, 0f, 10000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                timeChanged.set(true);
            }
            ImGui.textDisabled("关键帧在时间线上的位置");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 2. 季节类型（预设）
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.COLOR + " 季节类型", ImGuiTreeNodeFlags.DefaultOpen)) {
            seasonTypeIndex.set(keyframe.getSeasonType().ordinal());
            SeasonKeyframe.SeasonType[] types = SeasonKeyframe.SeasonType.values();

            if (ImGui.combo("季节类型", seasonTypeIndex, SEASON_TYPE_LABELS)) {
                SeasonKeyframe.SeasonType newType = types[seasonTypeIndex.get()];
                keyframe.applySeasonPreset(newType);
                paramChanged.set(true);
            }

            ImGui.textDisabled("• 选择季节会自动设置对应的温度/湿度/颜色趋势");
            UITheme.spacingSmall();

            // ---------- 一键预设按钮 ----------
            if (ImGui.button("🌸 春季预设")) {
                keyframe.applySeasonPreset(SeasonKeyframe.SeasonType.SPRING);
                paramChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("☀ 夏季预设")) {
                keyframe.applySeasonPreset(SeasonKeyframe.SeasonType.SUMMER);
                paramChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("🍁 秋季预设")) {
                keyframe.applySeasonPreset(SeasonKeyframe.SeasonType.AUTUMN);
                paramChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("❄ 冬季预设")) {
                keyframe.applySeasonPreset(SeasonKeyframe.SeasonType.WINTER);
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            if (ImGui.button("🎬 电影冷色")) {
                keyframe.applyFilmPresetCold();
                paramChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("🎬 电影暖色")) {
                keyframe.applyFilmPresetWarm();
                paramChanged.set(true);
            }
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 3. 气候参数（温度、湿度）
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.CHART + " 气候参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            temperatureFloat.set(keyframe.getTemperature());
            if (ImGui.sliderFloat("温度", temperatureFloat.getData(), -1.0f, 1.0f, "%.2f")) {
                keyframe.setTemperature(temperatureFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• -1.0 极冷（更易结冰/积雪），0 正常，+1.0 极热");
            UITheme.spacingSmall();

            humidityFloat.set(keyframe.getHumidity());
            if (ImGui.sliderFloat("湿度", humidityFloat.getData(), 0f, 1f, "%.2f")) {
                keyframe.setHumidity(humidityFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 0 = 干燥，1 = 湿润");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 4. 积雪控制（v4.0 新增）
        // ===========================================================
        if (ImGui.collapsingHeader("❄ 积雪控制", ImGuiTreeNodeFlags.DefaultOpen)) {
            snowAmountFloat.set(keyframe.getSnowAmount());
            if (ImGui.sliderFloat("积雪量", snowAmountFloat.getData(), 0f, 1f, "%.2f")) {
                keyframe.setSnowAmount(snowAmountFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 0.0 = 无雪，1.0 = 最大积雪（7层）");
            UITheme.spacingSmall();

            snowSlopeFactorFloat.set(keyframe.getSnowSlopeFactor());
            if (ImGui.sliderFloat("坡度影响", snowSlopeFactorFloat.getData(), 0f, 1f, "%.2f")) {
                keyframe.setSnowSlopeFactor(snowSlopeFactorFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 0.0 = 坡度不影响积雪，1.0 = 陡坡完全不积雪");
            UITheme.spacingSmall();

            snowlineFloat.set(keyframe.getSnowlineHeight());
            if (ImGui.dragFloat("雪线高度 (Y)", snowlineFloat.getData(), 1f, -64f, 320f, "%.0f")) {
                keyframe.setSnowlineHeight(snowlineFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 控制雪覆盖的最大高度");
            UITheme.spacingSmall();

            snowMeltFloat.set(keyframe.getSnowMeltSpeed());
            if (ImGui.sliderFloat("融化速度", snowMeltFloat.getData(), 0.1f, 5f, "%.2f")) {
                keyframe.setSnowMeltSpeed(snowMeltFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 1.0 = 正常速度，> 1.0 = 融化更快，< 1.0 = 融化更慢");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 5. 冰冻控制（v4.0 新增）
        // ===========================================================
        if (ImGui.collapsingHeader("🧊 冰冻控制", ImGuiTreeNodeFlags.DefaultOpen)) {
            freezeStrengthFloat.set(keyframe.getFreezeStrength());
            if (ImGui.sliderFloat("冰冻强度", freezeStrengthFloat.getData(), 0f, 1f, "%.2f")) {
                keyframe.setFreezeStrength(freezeStrengthFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 0.0 = 不冻结，1.0 = 最大冻结强度");
            UITheme.spacingSmall();

            freezeThresholdFloat.set(keyframe.getFreezeThreshold());
            if (ImGui.sliderFloat("冻结阈值（温度）", freezeThresholdFloat.getData(), -1f, 1f, "%.2f")) {
                keyframe.setFreezeThreshold(freezeThresholdFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 温度低于此值开始冻结（默认 -0.2）");
            UITheme.spacingSmall();

            freezeMeltSpeedFloat.set(keyframe.getFreezeMeltSpeed());
            if (ImGui.sliderFloat("融冰速度", freezeMeltSpeedFloat.getData(), 0.1f, 5f, "%.2f")) {
                keyframe.setFreezeMeltSpeed(freezeMeltSpeedFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 1.0 = 正常速度，> 1.0 = 融化更快，< 1.0 = 融化更慢");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 6. 颜色 Tint 控制
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.PALETTE + " 颜色与光照", ImGuiTreeNodeFlags.DefaultOpen)) {
            int flags = ImGuiColorEditFlags.DisplayRGB
                    | ImGuiColorEditFlags.InputRGB
                    | ImGuiColorEditFlags.PickerHueWheel
                    | ImGuiColorEditFlags.NoAlpha;

            // 将 tint 转换为 colorEdit3 可显示的颜色值
            tintToColor(keyframe.getGrassTint(), grassTintArr);
            tintToColor(keyframe.getFoliageTint(), foliageTintArr);
            tintToColor(keyframe.getSkyTint(), skyTintArr);
            tintToColor(keyframe.getFogTint(), fogTintArr);
            tintToColor(keyframe.getLightTint(), lightTintArr);

            // 草地
            if (ImGui.colorEdit3("草地颜色趋势", grassTintArr, flags)) {
                keyframe.setGrassTint(colorToTint(grassTintArr));
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 颜色偏移趋势：0.5=无变化，<0.5=减弱，>0.5=增强");
            UITheme.spacingSmall();

            // 树叶
            if (ImGui.colorEdit3("树叶颜色趋势", foliageTintArr, flags)) {
                keyframe.setFoliageTint(colorToTint(foliageTintArr));
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 与原始生物群系颜色相乘：finalColor = original × (1 + tint)");
            UITheme.spacingSmall();

            // 天空
            if (ImGui.colorEdit3("天空颜色趋势", skyTintArr, flags)) {
                keyframe.setSkyTint(colorToTint(skyTintArr));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            // 雾
            if (ImGui.colorEdit3("雾颜色趋势", fogTintArr, flags)) {
                keyframe.setFogTint(colorToTint(fogTintArr));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            // 光照
            if (ImGui.colorEdit3("光照颜色趋势", lightTintArr, flags)) {
                keyframe.setLightTint(colorToTint(lightTintArr));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 7. 插值方式
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.INTERPOLATION + " 插值设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            SeasonKeyframe.Interpolation[] types = SeasonKeyframe.Interpolation.values();
            interpolationIndex.set(keyframe.getInterpolation().ordinal());

            if (ImGui.combo("插值类型", interpolationIndex, INTERPOLATION_LABELS)) {
                keyframe.setInterpolation(types[interpolationIndex.get()]);
                paramChanged.set(true);
            }

            ImGui.textDisabled("• 线性：匀速变化");
            ImGui.textDisabled("• 平滑：缓入缓出");
            ImGui.textDisabled("• 曲线：Catmull-Rom 样条");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 参数更新触发 Season 系统
        // ===========================================================
        if (paramChanged.get()) {
            TimelineSeasonController c = TimelineSeasonController.getInstance();
            c.rebuildFromActiveTrack();
            // 预览被编辑的关键帧所在时间点的季节状态，而不是当前时间线时间
            c.previewAtKeyframeTime(keyframe.getTime());
        }

        if (timeChanged.get()) {
            TimelineSeasonController c = TimelineSeasonController.getInstance();
            c.rebuildFromActiveTrack(); // 时间变化不需要 preview
        }

        return paramChanged.get() || timeChanged.get();
    }

    @Override
    public Class<SeasonKeyframe> getTargetType() {
        return SeasonKeyframe.class;
    }

    // ===========================================================
    // 工具函数：Tint ↔ ColorEdit3 显示值
    // ===========================================================
    
    /**
     * Tint 到显示颜色的映射范围
     * 
     * <p>tint 的有效范围是 [-0.99, +3.0]，但大部分实际值在 [-0.5, +0.5] 之间。
     * 为了在 UI 上更好地显示和编辑，我们将显示范围限制为 [-0.5, +0.5]，
     * 映射到颜色选择器的 [0, 1] 范围。
     * 
     * <p>映射公式：
     * <ul>
     *   <li>显示值 = (tint + 0.5) / 1.0 → [0, 1]</li>
     *   <li>tint = 显示值 * 1.0 - 0.5 → [-0.5, +0.5]</li>
     * </ul>
     * 
     * <p>这样：
     * <ul>
     *   <li>tint = -0.5 → 显示 = 0.0（黑色，表示 -50%）</li>
     *   <li>tint = 0.0 → 显示 = 0.5（中性灰，表示无变化）</li>
     *   <li>tint = +0.5 → 显示 = 1.0（白色，表示 +50%）</li>
     * </ul>
     */
    private static final float TINT_DISPLAY_MIN = -0.5f;  // 显示范围最小值
    private static final float TINT_DISPLAY_MAX = +0.5f;  // 显示范围最大值
    private static final float TINT_DISPLAY_RANGE = TINT_DISPLAY_MAX - TINT_DISPLAY_MIN; // 1.0

    /** 将 tint（偏移值）转换为颜色显示值（0~1） */
    private void tintToColor(Vector3f tint, float[] outColor) {
        // 将 tint 从 [-0.5, +0.5] 映射到 [0, 1]
        // 超出范围的值会被 clamp
        outColor[0] = clamp01((tint.x - TINT_DISPLAY_MIN) / TINT_DISPLAY_RANGE);
        outColor[1] = clamp01((tint.y - TINT_DISPLAY_MIN) / TINT_DISPLAY_RANGE);
        outColor[2] = clamp01((tint.z - TINT_DISPLAY_MIN) / TINT_DISPLAY_RANGE);
    }

    /** 将 colorEdit3 的输出转换回 tint（偏移值） */
    private Vector3f colorToTint(float[] color) {
        // 将 [0, 1] 映射回 [-0.5, +0.5]
        return new Vector3f(
                color[0] * TINT_DISPLAY_RANGE + TINT_DISPLAY_MIN,
                color[1] * TINT_DISPLAY_RANGE + TINT_DISPLAY_MIN,
                color[2] * TINT_DISPLAY_RANGE + TINT_DISPLAY_MIN
        );
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
