package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.time.TimeKeyframe;
import com.director.timeline.playback.time.TimelineWorldController;
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
 * @author Director Team
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
    
    private static final String[] TIME_INTERPOLATION_LABELS = buildTimeInterpolationLabels();
    
    private static String[] buildTimeInterpolationLabels() {
        TimeKeyframe.TimeInterpolation[] types = TimeKeyframe.TimeInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = switch (types[i]) {
                case DEFAULT -> "默认（跟随轨道）";
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
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间", timeFloat.getData(), 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                timeChanged.set(true);
                // ⭐ 关键：时间变化不触发世界预览
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 世界时间设置 ==========
        if (ImGui.collapsingHeader(Icons.TIME + " 世界时间设置", ImGuiTreeNodeFlags.DefaultOpen)) {
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
            
            // ⭐ v3.3：显示模式说明和使用指导
            if (keyframe.isUseNaturalTime()) {
                ImGui.textDisabled("• 使用 Minecraft 原版世界时间");
                ImGui.textDisabled("• 播放/拖动时：原版时间推进会被冻结（避免日夜闪烁）");
                ImGui.textDisabled("• 太阳由 Minecraft 自动控制");
                ImGui.textDisabled("• 推荐：创建自然时间流逝动画（如正午到日落）");
            } else {
                ImGui.textDisabled("• 完全由时间轨道控制太阳轨迹");
                ImGui.textDisabled("• 世界时间会被冻结");
                ImGui.textDisabled("• 可以自定义太阳高度和方位角");
                ImGui.textDisabled("• 推荐：创建自定义太阳路径（如倒放、加速）");
            }
            
            UITheme.spacingSmall();
            UITheme.spacingSmall();
            
            // ⭐ v3.4：只在自然时间模式下显示世界时间输入框
            // 自定义太阳模式下不需要这个参数，因为时间会被冻结
            if (keyframe.isUseNaturalTime()) {
                // 世界时间（tick）
                long worldTime = keyframe.getWorldTimeTicks();
                worldTimeFloat.set(worldTime);
                if (ImGui.sliderFloat("世界时间（tick）", worldTimeFloat.getData(), 0.0f, 24000.0f, "%.0f")) {
                    long newWorldTime = (long)worldTimeFloat.get();
                    keyframe.setWorldTimeTicks(newWorldTime);
                    parameterChanged.set(true);
                }
                
                // 显示对应的时间描述
                long normalizedTime = worldTime % 24000L;
                String timeDesc = describeWorldTime(normalizedTime);
                ImGui.textDisabled(timeDesc);
                
                UITheme.spacingSmall();
                
                // ⭐ v3.2：预设时间按钮 - 快速设置常见时间点
                ImGui.text("预设时间：");
                ImGui.sameLine();
                if (ImGui.smallButton(Icons.LIGHT + " 日出")) {
                    keyframe.setWorldTimeTicks(0L);
                    parameterChanged.set(true);
                }
                ImGui.sameLine();
                if (ImGui.smallButton(Icons.LIGHT + " 正午")) {
                    keyframe.setWorldTimeTicks(6000L);
                    parameterChanged.set(true);
                }
                ImGui.sameLine();
                if (ImGui.smallButton(Icons.TIME + " 日落")) {
                    keyframe.setWorldTimeTicks(12000L);
                    parameterChanged.set(true);
                }
                ImGui.sameLine();
                if (ImGui.smallButton(Icons.TIME + " 午夜")) {
                    keyframe.setWorldTimeTicks(18000L);
                    parameterChanged.set(true);
                }
                
                UITheme.spacingSmall();
                
                // 同步当前世界时间按钮（仅在自然时间模式下显示）
                if (ImGui.button("同步当前世界时间")) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.world != null) {
                        long currentTime = client.world.getTimeOfDay();
                        keyframe.setWorldTimeTicks(currentTime % 24000L);
                        parameterChanged.set(true);
                    }
                }
                
                UITheme.spacingSmall();
            } else {
                // 自定义太阳模式下显示提示信息
                ImGui.textDisabled("（时间被冻结，由轨道控制太阳位置）");
                UITheme.spacingSmall();
            }
            
            // ⭐ 无论模式如何，都同步 ImFloat 对象的值（确保切换模式时值正确）
            sunElevationFloat.set(keyframe.getSunElevation());
            
            // 如果使用自定义太阳模式，显示太阳角度设置
            if (!keyframe.isUseNaturalTime()) {
                ImGui.separator();
                
                boolean elevationChanged = ImGui.sliderFloat("太阳高度角（Elevation）", sunElevationFloat.getData(), -60.0f, 60.0f, "%.1f°");
                if (elevationChanged) {
                    float newElevation = sunElevationFloat.get();
                    keyframe.setSunElevation(newElevation);
                    parameterChanged.set(true);
                }
                ImGui.textDisabled("0° = 地平线，60° = 最高点（正午），负值表示地平线以下（夜晚）");
                
                UITheme.spacingSmall();
                
                // ⭐ v3.2：太阳方位角编辑
                sunAzimuthFloat.set(keyframe.getSunAzimuth());
                boolean azimuthChanged = ImGui.sliderFloat("太阳方位角（Azimuth）", sunAzimuthFloat.getData(), 0.0f, 360.0f, "%.1f°");
                if (azimuthChanged) {
                    float newAzimuth = sunAzimuthFloat.get();
                    keyframe.setSunAzimuth(newAzimuth);
                    parameterChanged.set(true);
                }
                ImGui.textDisabled("0° = 正北，90° = 正东，180° = 正南，270° = 正西");
                
                UITheme.spacingSmall();
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 插值设置 ==========
        if (ImGui.collapsingHeader(Icons.INTERPOLATION + " 插值设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 位置/时间插值类型
            TimeKeyframe.TimeInterpolation[] timeTypes = TimeKeyframe.TimeInterpolation.values();
            interpolationIndex.set(keyframe.getPathInterpolation().ordinal());
            if (ImGui.combo("位置插值", interpolationIndex, TIME_INTERPOLATION_LABELS)) {
                keyframe.setPathInterpolation(timeTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
            }
            
            UITheme.spacingSmall();
            
            // 角度插值类型
            interpolationIndex.set(keyframe.getAngleInterpolation().ordinal());
            if (ImGui.combo("方位插值", interpolationIndex, TIME_INTERPOLATION_LABELS)) {
                keyframe.setAngleInterpolation(timeTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
            }
            
            UITheme.spacingSmall();
        }
        
        // ⭐ v3.3：修复问题1 - 参数变化时立即更新冻结状态并预览关键帧所在位置
        // 这样可以确保设置的时间能够持久显示，不会被播放头位置的采样结果覆盖
        // 同时确保自定义太阳模式时立即冻结时间，防止原版时间推进覆盖设置
        if (parameterChanged.get()) {
            TimelineWorldController controller = TimelineWorldController.getInstance();
            controller.rebuildFromActiveTrack();
            
            // ⭐ v3.3 关键修复：
            // 1. previewAtKeyframeTime 内部会立即更新冻结状态（如果切换为自定义模式）
            // 2. 预览关键帧所在位置的时间，而不是当前播放头位置
            // 3. 应用采样后立即同步冻结时间快照，防止原版时间推进覆盖
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
     * 
     * <p>Minecraft 时间映射：
     * <ul>
     *   <li>0 = 日出 (6:00 AM)</li>
     *   <li>1000 = 早晨 (7:00 AM)</li>
     *   <li>6000 = 正午 (12:00 PM)</li>
     *   <li>12000 = 日落 (18:00 PM)</li>
     *   <li>13000 = 傍晚 (19:00 PM)</li>
     *   <li>18000 = 午夜 (0:00 AM)</li>
     *   <li>23000 = 凌晨 (5:00 AM)</li>
     * </ul>
     */
    private String describeWorldTime(long timeTicks) {
        long normalized = timeTicks % 24000L;
        // Minecraft 时间 tick 0 = 6:00 AM（日出），每 1000 tick = 1 小时
        int hour = (int) ((normalized / 1000 + 6) % 24);
        int minute = (int) ((normalized % 1000) * 60 / 1000);
        String clockTime = String.format("%02d:%02d", hour, minute);
        
        String period;
        if (normalized < 1000) {
            period = "日出";
        } else if (normalized < 6000) {
            period = "上午";
        } else if (normalized < 6500) {
            period = "正午";
        } else if (normalized < 11500) {
            period = "下午";
        } else if (normalized < 13000) {
            period = "日落";
        } else if (normalized < 17500) {
            period = "夜晚";
        } else if (normalized < 18500) {
            period = "午夜";
        } else if (normalized < 23000) {
            period = "深夜";
        } else {
            period = "凌晨";
        }
        return period + " (" + clockTime + ")";
    }
    
    @Override
    public Class<TimeKeyframe> getTargetType() {
        return TimeKeyframe.class;
    }
}
