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
 * TimeKeyframePropertyAdapter v3.1 - 重新梳理后的专业版本
 * 
 * <p>核心改进：
 * <ul>
 *   <li>✅ 区分时间变化和其他参数变化，只有参数变化才触发预览</li>
 *   <li>✅ 明确两种模式的互斥性（使用世界时间 vs 自定义太阳）</li>
 *   <li>✅ 选择自定义太阳模式时自动启用时间冻结</li>
 *   <li>✅ 改进 UI 逻辑，确保模式切换时正确设置相关字段</li>
 *   <li>✅ v3.1：添加预设时间按钮，快速设置常见时间点</li>
 * </ul>
 * 
 * <p>时间关键帧属性适配器，为时间线上的时间关键帧提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>时间位置（关键帧在时间线上的位置）</li>
 *   <li>世界时间模式（自然时间 vs 自定义太阳）</li>
 *   <li>世界时间（tick）- 支持预设按钮快速设置</li>
 *   <li>太阳高度角和方位角</li>
 *   <li>插值类型</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 3.1 - 添加预设时间按钮
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
        
        // ⭐ v3.0：分别跟踪时间变化和其他参数变化
        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean parameterChanged = new AtomicBoolean(false);
        
        // 标题
        UITheme.title(Icons.CLOCK + " " + TITLE);
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间", timeFloat.getData(), 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                timeChanged.set(true);
                // ⭐ 关键：时间变化不触发世界预览
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 世界时间设置 ==========
        if (ImGui.collapsingHeader("⏰ 世界时间设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            // ⭐ v3.0：模式选择 - 明确两种模式的互斥性
            useNaturalTimeBool.set(keyframe.isUseNaturalTime());
            
            if (ImGui.radioButton("使用世界时间（Minecraft 原版）", useNaturalTimeBool.get())) {
                keyframe.setUseNaturalTime(true);
                parameterChanged.set(true);
            }
            
            if (ImGui.radioButton("自定义太阳高度 / 方位（由时间轨道控制）", !useNaturalTimeBool.get())) {
                keyframe.setUseNaturalTime(false);
                parameterChanged.set(true);
                // ⭐ v3.0：选择自定义太阳模式时，会自动启用时间冻结（通过 TimeSample.fromCustomSun）
                // 这由 TimeTrackSampler.toSample() 处理，这里只需要设置模式
            }
            
            // ⭐ v3.0：显示模式说明
            if (keyframe.isUseNaturalTime()) {
                ImGui.textDisabled("• 使用 Minecraft 原版世界时间");
                ImGui.textDisabled("• 世界时间会自动流逝");
                ImGui.textDisabled("• 太阳由 Minecraft 自动控制");
            } else {
                ImGui.textDisabled("• 完全由时间轨道控制太阳轨迹");
                ImGui.textDisabled("• 世界时间会被冻结");
                ImGui.textDisabled("• 可以自定义太阳高度和方位角");
            }
            
            UITheme.spacingSmall();
            
            // 世界时间（tick）
            long worldTime = keyframe.getWorldTimeTicks();
            worldTimeFloat.set(worldTime);
            if (ImGui.dragFloat("世界时间（tick）", worldTimeFloat.getData(), 10f, 0f, 24000f, "%.0f")) {
                keyframe.setWorldTimeTicks((long)worldTimeFloat.get());
                parameterChanged.set(true);
            }
            
            // 显示对应的时间描述
            long normalizedTime = worldTime % 24000L;
            String timeDesc = describeWorldTime(normalizedTime);
            ImGui.textDisabled(timeDesc);
            
            UITheme.spacingSmall();
            
            // ⭐ v3.1：预设时间按钮 - 快速设置常见时间点
            // 点击按钮会自动将世界时间设置为对应值，并触发预览
            ImGui.textDisabled("快速设置：");
            
            // 第一行：日出前和正午
            if (ImGui.button("日出前")) {
                keyframe.setWorldTimeTicks(0L);
                worldTimeFloat.set(0L); // 同步UI显示
                parameterChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("正午")) {
                keyframe.setWorldTimeTicks(6000L);
                worldTimeFloat.set(6000L); // 同步UI显示
                parameterChanged.set(true);
            }
            
            // 第二行：日落和午夜
            if (ImGui.button("日落")) {
                keyframe.setWorldTimeTicks(12000L);
                worldTimeFloat.set(12000L); // 同步UI显示
                parameterChanged.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("午夜")) {
                keyframe.setWorldTimeTicks(18000L);
                worldTimeFloat.set(18000L); // 同步UI显示
                parameterChanged.set(true);
            }
            
            UITheme.spacingSmall();
            
            // 同步当前世界时间按钮
            if (ImGui.button("同步当前世界时间")) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null) {
                    long currentTime = client.world.getTimeOfDay();
                    keyframe.setWorldTimeTicks(currentTime % 24000L);
                    parameterChanged.set(true);
                }
            }
            
            UITheme.spacingSmall();
            
            // ⭐ 无论模式如何，都同步 ImFloat 对象的值（确保切换模式时值正确）
            sunElevationFloat.set(keyframe.getSunElevation());
            sunAzimuthFloat.set(keyframe.getSunAzimuth());
            
            // 如果使用自定义太阳模式，显示太阳角度设置
            if (!keyframe.isUseNaturalTime()) {
                ImGui.separator();
                
                boolean elevationChanged = ImGui.dragFloat("太阳高度角（Elevation）", sunElevationFloat.getData(), 1.0f, 0.0f, 90.0f, "%.1f°");
                if (elevationChanged) {
                    float newElevation = sunElevationFloat.get();
                    keyframe.setSunElevation(newElevation);
                    parameterChanged.set(true);
                }
                ImGui.textDisabled("0° = 地平线，90° = 正头顶");
                
                UITheme.spacingSmall();
                
                boolean azimuthChanged = ImGui.dragFloat("太阳方位角（Azimuth）", sunAzimuthFloat.getData(), 1.0f, 0.0f, 360.0f, "%.1f°");
                if (azimuthChanged) {
                    float newAzimuth = sunAzimuthFloat.get();
                    keyframe.setSunAzimuth(newAzimuth);
                    parameterChanged.set(true);
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
                parameterChanged.set(true);
            }
            
            UITheme.spacingSmall();
            
            // 角度插值类型
            TimeKeyframe.AngleInterpolation[] angleTypes = TimeKeyframe.AngleInterpolation.values();
            interpolationIndex.set(keyframe.getAngleInterpolation().ordinal());
            if (ImGui.combo("方位插值", interpolationIndex, ANGLE_INTERPOLATION_LABELS)) {
                keyframe.setAngleInterpolation(angleTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
            }
            
            UITheme.spacingSmall();
        }
        
        // ⭐ v3.2：修复问题2 - 参数变化时预览关键帧所在位置，而不是播放头位置
        // 这样可以确保设置的时间能够持久显示，不会被播放头位置的采样结果覆盖
        if (parameterChanged.get()) {
            TimelineWorldController controller = TimelineWorldController.getInstance();
            controller.rebuildFromActiveTrack();
            // ⭐ 关键修复：预览关键帧所在位置的时间，而不是当前播放头位置
            // 这样修改世界时间、太阳高度、方位角等参数时，会立即看到关键帧所在位置的预览效果
            controller.previewAtKeyframeTime(keyframe.getTime());
        }
        
        // ⭐ v3.0：时间变化只通知重建采样器，不触发预览
        // 这样拖动关键帧时间时，世界不会错误地跟随变化
        if (timeChanged.get()) {
            TimelineWorldController controller = TimelineWorldController.getInstance();
            controller.rebuildFromActiveTrack();
            // 不调用预览，因为播放头位置没有改变
        }
        
        return timeChanged.get() || parameterChanged.get();
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
