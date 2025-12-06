package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TimeKeyframePropertyAdapter v1.0
 * 
 * <p>时间关键帧属性适配器
 * 
 * <p>为时间线上的时间关键帧提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>时间位置</li>
 *   <li>世界时间模式（自然时间 vs 自定义太阳）</li>
 *   <li>世界时间（tick）</li>
 *   <li>太阳高度角和方位角</li>
 *   <li>插值类型</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimeKeyframePropertyAdapter implements IPropertyAdapter<TimeKeyframe> {
    
    private static final String TITLE = "时间关键帧";
    
    // 复用的临时对象，减少 GC 压力
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat sunElevationFloat = new ImFloat();
    private final ImFloat sunAzimuthFloat = new ImFloat();
    private final ImFloat worldTimeFloat = new ImFloat();
    private final ImInt interpolationIndex = new ImInt();
    private final ImBoolean useNaturalTimeBool = new ImBoolean();
    
    private static final String[] SEGMENT_INTERPOLATION_LABELS = buildSegmentInterpolationLabels();
    private static final String[] ANGLE_INTERPOLATION_LABELS = buildAngleInterpolationLabels();
    
    private static String[] buildSegmentInterpolationLabels() {
        TimeKeyframe.SegmentInterpolation[] types = TimeKeyframe.SegmentInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = switch (types[i]) {
                case DEFAULT -> "默认（跟随轨道）";
                case LINEAR -> "线性";
                case CATMULL_ROM -> "平滑（Catmull-Rom）";
                case BEZIER -> "贝塞尔曲线";
                case STEP -> "阶梯";
                case HOLD -> "保持";
            };
        }
        return labels;
    }
    
    private static String[] buildAngleInterpolationLabels() {
        TimeKeyframe.AngleInterpolation[] types = TimeKeyframe.AngleInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = switch (types[i]) {
                case DEFAULT -> "默认（跟随位置插值）";
                case LINEAR -> "线性";
                case CATMULL_ROM -> "平滑（Catmull-Rom）";
                case BEZIER -> "贝塞尔曲线";
                case STEP -> "阶梯";
            };
        }
        return labels;
    }
    
    @Override
    public boolean renderProperties(TimeKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("（没有选中关键帧）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        AtomicBoolean changed = new AtomicBoolean(false);
        
        // 标题
        UITheme.title(Icons.CLOCK + " " + TITLE);
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间", timeFloat.getData(), 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                changed.set(true);
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 世界时间设置 ==========
        if (ImGui.collapsingHeader("⏰ 世界时间设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 模式选择：自然时间 vs 自定义太阳
            useNaturalTimeBool.set(keyframe.isUseNaturalTime());
            if (ImGui.radioButton("使用世界时间（dayTime）", useNaturalTimeBool.get())) {
                keyframe.setUseNaturalTime(true);
                changed.set(true);
            }
            
            if (ImGui.radioButton("自定义太阳高度 / 方位", !useNaturalTimeBool.get())) {
                keyframe.setUseNaturalTime(false);
                changed.set(true);
            }
            
            UITheme.spacingSmall();
            
            // 世界时间（tick）
            long worldTime = keyframe.getWorldTimeTicks();
            worldTimeFloat.set(worldTime);
            if (ImGui.dragFloat("世界时间（tick）", worldTimeFloat.getData(), 10f, 0f, 24000f, "%.0f")) {
                keyframe.setWorldTimeTicks((long)worldTimeFloat.get());
                changed.set(true);
            }
            
            // 显示对应的时间描述
            long normalizedTime = worldTime % 24000L;
            String timeDesc = describeWorldTime(normalizedTime);
            ImGui.textDisabled(timeDesc);
            
            // 同步当前世界时间按钮
            if (ImGui.button("同步当前世界时间")) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null) {
                    long currentTime = client.world.getTimeOfDay();
                    keyframe.setWorldTimeTicks(currentTime % 24000L);
                    changed.set(true);
                }
            }
            
            UITheme.spacingSmall();
            
            // ⭐ 无论模式如何，都同步 ImFloat 对象的值（确保切换模式时值正确）
            // 这样可以避免在切换到自定义模式时显示错误的值
            sunElevationFloat.set(keyframe.getSunElevation());
            sunAzimuthFloat.set(keyframe.getSunAzimuth());
            
            // 如果使用自定义太阳模式，显示太阳角度设置
            if (!keyframe.isUseNaturalTime()) {
                ImGui.separator();
                
                // ⭐ 注意：即使控件正在被拖动，ImGui 也会智能处理，不会覆盖拖动中的值
                boolean elevationChanged = ImGui.dragFloat("太阳高度角（Elevation）", sunElevationFloat.getData(), 1.0f, 0.0f, 90.0f, "%.1f°");
                if (elevationChanged) {
                    // ⭐ 立即更新 keyframe，确保值实时反映
                    float newElevation = sunElevationFloat.get();
                    keyframe.setSunElevation(newElevation);
                    changed.set(true);
                }
                ImGui.textDisabled("0° = 地平线，90° = 正头顶");
                
                UITheme.spacingSmall();
                
                boolean azimuthChanged = ImGui.dragFloat("太阳方位角（Azimuth）", sunAzimuthFloat.getData(), 1.0f, 0.0f, 360.0f, "%.1f°");
                if (azimuthChanged) {
                    // ⭐ 立即更新 keyframe，确保值实时反映
                    float newAzimuth = sunAzimuthFloat.get();
                    keyframe.setSunAzimuth(newAzimuth);
                    changed.set(true);
                }
                ImGui.textDisabled("0° = 正北，90° = 正东，180° = 正南，270° = 正西");
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 插值设置 ==========
        if (ImGui.collapsingHeader("📈 插值设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 位置/时间插值类型
            TimeKeyframe.SegmentInterpolation[] segmentTypes = TimeKeyframe.SegmentInterpolation.values();
            interpolationIndex.set(keyframe.getPathInterpolation().ordinal());
            if (ImGui.combo("位置插值", interpolationIndex, SEGMENT_INTERPOLATION_LABELS)) {
                keyframe.setPathInterpolation(segmentTypes[interpolationIndex.get()]);
                changed.set(true);
            }
            
            UITheme.spacingSmall();
            
            // 角度插值类型
            TimeKeyframe.AngleInterpolation[] angleTypes = TimeKeyframe.AngleInterpolation.values();
            interpolationIndex.set(keyframe.getAngleInterpolation().ordinal());
            if (ImGui.combo("方位插值", interpolationIndex, ANGLE_INTERPOLATION_LABELS)) {
                keyframe.setAngleInterpolation(angleTypes[interpolationIndex.get()]);
                changed.set(true);
            }
            
            UITheme.spacingSmall();
        }
        
        // 通知控制器刷新（如果有修改）
        if (changed.get()) {
            TimelineWorldController controller = TimelineWorldController.getInstance();
            controller.rebuildFromActiveTrack();
            // ⭐ 立刻用当前时间重新采样并应用到世界，实现实时预览
            controller.previewAtCurrentTime();
        }
        
        return changed.get();
    }
    
    /**
     * 描述世界时间（tick）对应的时间段
     */
    private String describeWorldTime(long timeTicks) {
        long normalized = timeTicks % 24000L;
        if (normalized < 6000) {
            return "凌晨 / 日出前";
        } else if (normalized < 12000) {
            return "正午";
        } else if (normalized < 18000) {
            return "日落";
        } else {
            return "午夜";
        }
    }
    
    @Override
    public Class<TimeKeyframe> getTargetType() {
        return TimeKeyframe.class;
    }
}
