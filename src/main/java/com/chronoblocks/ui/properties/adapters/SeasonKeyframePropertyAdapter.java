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
 * SeasonKeyframePropertyAdapter v1.0
 * 
 * <p>季节关键帧属性适配器，为时间线上的季节关键帧提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>时间位置（关键帧在时间线上的位置）</li>
 *   <li>季节类型（春天/夏天/秋天/冬天/自定义）</li>
 *   <li>气候参数（温度、湿度、雪线高度）</li>
 *   <li>颜色趋势（草地、树叶、天空、雾、光照颜色）</li>
 *   <li>插值类型</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SeasonKeyframePropertyAdapter implements IPropertyAdapter<SeasonKeyframe> {
    
    private static final String TITLE = "季节关键帧";
    
    // 复用的临时对象，减少 GC 压力
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat temperatureFloat = new ImFloat();
    private final ImFloat humidityFloat = new ImFloat();
    private final ImFloat snowlineHeightFloat = new ImFloat();
    private final ImInt seasonTypeIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();
    
    // 颜色编辑用的临时数组
    private final float[] grassColor = new float[3];
    private final float[] foliageColor = new float[3];
    private final float[] skyColor = new float[3];
    private final float[] fogColor = new float[3];
    private final float[] lightColor = new float[3];
    
    private static final String[] SEASON_TYPE_LABELS = buildSeasonTypeLabels();
    private static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();
    
    private static String[] buildSeasonTypeLabels() {
        SeasonKeyframe.SeasonType[] types = SeasonKeyframe.SeasonType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }
    
    private static String[] buildInterpolationLabels() {
        SeasonKeyframe.Interpolation[] types = SeasonKeyframe.Interpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }
    
    @Override
    public boolean renderProperties(SeasonKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("（没有选中关键帧）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 分别跟踪时间变化和其他参数变化
        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean parameterChanged = new AtomicBoolean(false);
        
        // 标题
        UITheme.title(Icons.CALENDAR + " " + TITLE);
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间", timeFloat.getData(), 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                timeChanged.set(true);
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 季节类型 ==========
        if (ImGui.collapsingHeader(Icons.COLOR + " 季节类型", ImGuiTreeNodeFlags.DefaultOpen)) {
            seasonTypeIndex.set(keyframe.getSeasonType().ordinal());
            SeasonKeyframe.SeasonType[] seasonTypes = SeasonKeyframe.SeasonType.values();
            
            if (ImGui.combo("季节类型", seasonTypeIndex, SEASON_TYPE_LABELS)) {
                SeasonKeyframe.SeasonType newType = seasonTypes[seasonTypeIndex.get()];
                keyframe.applySeasonPreset(newType); // 应用预设值
                parameterChanged.set(true);
            }
            
            ImGui.textDisabled("• 选择季节类型会自动应用该季节的默认参数");
            ImGui.textDisabled("• 可在\"自定义\"模式下手动调整所有参数");
            
            UITheme.spacingSmall();
        }
        
        // ========== 气候参数 ==========
        if (ImGui.collapsingHeader(Icons.CHART + " 气候参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 温度
            temperatureFloat.set(keyframe.getTemperature());
            if (ImGui.sliderFloat("温度", temperatureFloat.getData(), -1.0f, 1.0f, "%.2f")) {
                keyframe.setTemperature(temperatureFloat.get());
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• -1.0 = 极冷，0.0 = 中性，+1.0 = 极热");
            
            UITheme.spacingSmall();
            
            // 湿度
            humidityFloat.set(keyframe.getHumidity());
            if (ImGui.sliderFloat("湿度", humidityFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                keyframe.setHumidity(humidityFloat.get());
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 0.0 = 干燥，1.0 = 湿润");
            
            UITheme.spacingSmall();
            
            // 雪线高度
            snowlineHeightFloat.set(keyframe.getSnowlineHeight());
            if (ImGui.sliderFloat("雪线高度", snowlineHeightFloat.getData(), -64.0f, 320.0f, "%.0f")) {
                keyframe.setSnowlineHeight(snowlineHeightFloat.get());
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 雪覆盖的最大高度（Y坐标）");
            
            UITheme.spacingSmall();
        }
        
        // ========== 颜色趋势 ==========
        if (ImGui.collapsingHeader(Icons.COLOR + " 颜色趋势", ImGuiTreeNodeFlags.DefaultOpen)) {
            int colorEditFlags = ImGuiColorEditFlags.DisplayRGB
                               | ImGuiColorEditFlags.InputRGB
                               | ImGuiColorEditFlags.PickerHueWheel
                               | ImGuiColorEditFlags.NoAlpha;
            
            // 同步颜色值到临时数组
            Vector3f grassTint = keyframe.getGrassTint();
            grassColor[0] = grassTint.x;
            grassColor[1] = grassTint.y;
            grassColor[2] = grassTint.z;
            
            Vector3f foliageTint = keyframe.getFoliageTint();
            foliageColor[0] = foliageTint.x;
            foliageColor[1] = foliageTint.y;
            foliageColor[2] = foliageTint.z;
            
            Vector3f skyTint = keyframe.getSkyTint();
            skyColor[0] = skyTint.x;
            skyColor[1] = skyTint.y;
            skyColor[2] = skyTint.z;
            
            Vector3f fogTint = keyframe.getFogTint();
            fogColor[0] = fogTint.x;
            fogColor[1] = fogTint.y;
            fogColor[2] = fogTint.z;
            
            Vector3f lightTint = keyframe.getLightTint();
            lightColor[0] = lightTint.x;
            lightColor[1] = lightTint.y;
            lightColor[2] = lightTint.z;
            
            // 草地颜色
            if (ImGui.colorEdit3("草地颜色", grassColor, colorEditFlags)) {
                keyframe.setGrassTint(new Vector3f(grassColor[0], grassColor[1], grassColor[2]));
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 草地颜色偏移（与生物群系颜色相乘）");
            
            UITheme.spacingSmall();
            
            // 树叶颜色
            if (ImGui.colorEdit3("树叶颜色", foliageColor, colorEditFlags)) {
                keyframe.setFoliageTint(new Vector3f(foliageColor[0], foliageColor[1], foliageColor[2]));
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 树叶颜色偏移（与生物群系颜色相乘）");
            
            UITheme.spacingSmall();
            
            // 天空颜色
            if (ImGui.colorEdit3("天空颜色", skyColor, colorEditFlags)) {
                keyframe.setSkyTint(new Vector3f(skyColor[0], skyColor[1], skyColor[2]));
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 天空颜色偏移（影响天空渲染）");
            
            UITheme.spacingSmall();
            
            // 雾颜色
            if (ImGui.colorEdit3("雾颜色", fogColor, colorEditFlags)) {
                keyframe.setFogTint(new Vector3f(fogColor[0], fogColor[1], fogColor[2]));
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 雾颜色偏移（用于电影效果）");
            
            UITheme.spacingSmall();
            
            // 光照颜色
            if (ImGui.colorEdit3("光照颜色", lightColor, colorEditFlags)) {
                keyframe.setLightTint(new Vector3f(lightColor[0], lightColor[1], lightColor[2]));
                parameterChanged.set(true);
            }
            ImGui.textDisabled("• 全局光照颜色（用于电影效果）");
            
            UITheme.spacingSmall();
        }
        
        // ========== 插值设置 ==========
        if (ImGui.collapsingHeader(Icons.INTERPOLATION + " 插值设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            SeasonKeyframe.Interpolation[] interpolationTypes = SeasonKeyframe.Interpolation.values();
            interpolationIndex.set(keyframe.getInterpolation().ordinal());
            
            if (ImGui.combo("插值类型", interpolationIndex, INTERPOLATION_LABELS)) {
                keyframe.setInterpolation(interpolationTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
            }
            
            ImGui.textDisabled("• 线性：匀速过渡");
            ImGui.textDisabled("• 平滑：缓入缓出");
            ImGui.textDisabled("• 曲线：Catmull-Rom 样条过渡");
            
            UITheme.spacingSmall();
        }
        
        // 参数变化时立即更新季节控制器
        if (parameterChanged.get()) {
            TimelineSeasonController controller = TimelineSeasonController.getInstance();
            controller.rebuildFromActiveTrack();
            controller.previewAtCurrentTime();
        }
        
        // 时间变化只通知重建采样器，不触发预览
        if (timeChanged.get()) {
            TimelineSeasonController controller = TimelineSeasonController.getInstance();
            controller.rebuildFromActiveTrack();
        }
        
        return timeChanged.get() || parameterChanged.get();
    }
    
    @Override
    public Class<SeasonKeyframe> getTargetType() {
        return SeasonKeyframe.class;
    }
}

