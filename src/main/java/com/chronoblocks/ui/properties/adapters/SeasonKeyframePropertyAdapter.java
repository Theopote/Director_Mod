package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
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
 * SeasonKeyframePropertyAdapter v3.5
 *
 * 支持：
 *  - 时间位置
 *  - 季节类型（春/夏/秋/冬/自定义）
 *  - 温度、湿度、雪线
 *  - 所有颜色 Tint（草/树叶/天空/雾/光照）
 *  - 插值方式
 *
 * Tint 输入采用 UI 显示颜色 = (1 + tint)
 * 存储 tint = (ui_color - 1)
 */
public class SeasonKeyframePropertyAdapter implements IPropertyAdapter<SeasonKeyframe> {

    private static final String TITLE = "季节关键帧";

    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat temperatureFloat = new ImFloat();
    private final ImFloat humidityFloat = new ImFloat();
    private final ImFloat snowlineFloat = new ImFloat();

    private final ImInt seasonTypeIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();

    // Tint 转换后的颜色缓存 [0..1]
    private final float[] grassColor = new float[3];
    private final float[] foliageColor = new float[3];
    private final float[] skyColor = new float[3];
    private final float[] fogColor = new float[3];
    private final float[] lightColor = new float[3];

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

        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean paramChanged = new AtomicBoolean(false);

        UITheme.title(Icons.CALENDAR + " " + TITLE);

        // ===========================================================
        // 基础信息
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
        // 季节类型
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
            ImGui.textDisabled("• 自定义模式允许编辑所有参数");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 气候参数
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.CHART + " 气候参数", ImGuiTreeNodeFlags.DefaultOpen)) {

            temperatureFloat.set(keyframe.getTemperature());
            if (ImGui.sliderFloat("温度", temperatureFloat.getData(), -1.0f, 1.0f, "%.2f")) {
                keyframe.setTemperature(temperatureFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• -1.0 极冷, 0 正常, +1.0 极热");
            UITheme.spacingSmall();

            humidityFloat.set(keyframe.getHumidity());
            if (ImGui.sliderFloat("湿度", humidityFloat.getData(), 0f, 1f, "%.2f")) {
                keyframe.setHumidity(humidityFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 0 = 干, 1 = 湿");
            UITheme.spacingSmall();

            snowlineFloat.set(keyframe.getSnowlineHeight());
            if (ImGui.sliderFloat("雪线高度 (Y)", snowlineFloat.getData(), -64f, 320f, "%.0f")) {
                keyframe.setSnowlineHeight(snowlineFloat.get());
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 控制雪覆盖的最大高度");
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 颜色 Tint（UI 显示颜色 = 1 + tint）
        // ===========================================================
        if (ImGui.collapsingHeader(Icons.PALETTE + " 颜色趋势", ImGuiTreeNodeFlags.DefaultOpen)) {

            int flags = ImGuiColorEditFlags.DisplayRGB
                    | ImGuiColorEditFlags.InputRGB
                    | ImGuiColorEditFlags.PickerHueWheel
                    | ImGuiColorEditFlags.NoAlpha;

            // 将 tint 转换为 colorEdit3 可显示的颜色值
            tintToColor(keyframe.getGrassTint(), grassColor);
            tintToColor(keyframe.getFoliageTint(), foliageColor);
            tintToColor(keyframe.getSkyTint(), skyColor);
            tintToColor(keyframe.getFogTint(), fogColor);
            tintToColor(keyframe.getLightTint(), lightColor);

            // 草地
            if (ImGui.colorEdit3("草地颜色趋势", grassColor, flags)) {
                keyframe.setGrassTint(colorToTint(grassColor));
                paramChanged.set(true);
            }
            ImGui.textDisabled("• 与原始生物群系颜色相乘的趋势");
            UITheme.spacingSmall();

            // 树叶
            if (ImGui.colorEdit3("树叶颜色趋势", foliageColor, flags)) {
                keyframe.setFoliageTint(colorToTint(foliageColor));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            // 天空
            if (ImGui.colorEdit3("天空颜色趋势", skyColor, flags)) {
                keyframe.setSkyTint(colorToTint(skyColor));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            // 雾
            if (ImGui.colorEdit3("雾颜色趋势", fogColor, flags)) {
                keyframe.setFogTint(colorToTint(fogColor));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();

            // 光照
            if (ImGui.colorEdit3("光照颜色趋势", lightColor, flags)) {
                keyframe.setLightTint(colorToTint(lightColor));
                paramChanged.set(true);
            }
            UITheme.spacingSmall();
        }

        // ===========================================================
        // 插值方式
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
            c.previewAtCurrentTime(); // 实时预览
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

    /** 将 tint（-1~∞）转换为颜色显示值（0~1） */
    private void tintToColor(Vector3f tint, float[] outColor) {
        outColor[0] = clamp01(1f + tint.x);
        outColor[1] = clamp01(1f + tint.y);
        outColor[2] = clamp01(1f + tint.z);
    }

    /** 将 colorEdit3 的输出转换回 tint */
    private Vector3f colorToTint(float[] color) {
        return new Vector3f(
                (color[0] - 1f),
                (color[1] - 1f),
                (color[2] - 1f)
        );
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
