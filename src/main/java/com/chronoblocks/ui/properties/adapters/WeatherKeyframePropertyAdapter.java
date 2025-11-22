package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

/**
 * 天气关键帧属性适配器
 * 
 * <p>为时间线上的天气关键帧提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>天气类型（晴天、雨天、雪天等）</li>
 *   <li>强度和时间</li>
 *   <li>风速和风向</li>
 *   <li>云密度和降水量</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WeatherKeyframePropertyAdapter implements IPropertyAdapter<WeatherKeyframe> {
    
    private final ImInt weatherTypeIndex = new ImInt();
    private static final String[] WEATHER_TYPE_LABELS = buildWeatherTypeLabels();
    
    private static String[] buildWeatherTypeLabels() {
        WeatherKeyframe.WeatherType[] types = WeatherKeyframe.WeatherType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }
    
    @Override
    public boolean renderProperties(WeatherKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("天气关键帧为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 标题
        UITheme.title(Icons.CLIPBOARD + " 天气关键帧");
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] time = {keyframe.getTime()};
            if (ImGui.dragFloat("时间", time, 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(time[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            WeatherKeyframe.WeatherType[] types = WeatherKeyframe.WeatherType.values();
            weatherTypeIndex.set(keyframe.getWeatherType().ordinal());
            if (ImGui.combo("天气类型", weatherTypeIndex, WEATHER_TYPE_LABELS)) {
                keyframe.setWeatherType(types[weatherTypeIndex.get()]);
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            float[] intensity = {keyframe.getIntensity()};
            if (ImGui.dragFloat("强度", intensity, 0.01f, 0.0f, 1.0f, "%.2f")) {
                keyframe.setIntensity(intensity[0]);
                modified = true;
            }
            
            float[] duration = {keyframe.getDuration()};
            if (ImGui.dragFloat("持续时间", duration, 0.1f, 0.0f, 600f, "%.1fs")) {
                keyframe.setDuration(duration[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 天气特效 ==========
        if (ImGui.collapsingHeader("🌦️ 天气特效", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] windSpeed = {keyframe.getWindSpeed()};
            if (ImGui.dragFloat("风速", windSpeed, 0.1f, 0.0f, 100f, "%.1f m/s")) {
                keyframe.setWindSpeed(windSpeed[0]);
                modified = true;
            }
            
            float[] windDirection = {keyframe.getWindDirection()};
            if (ImGui.dragFloat("风向", windDirection, 1.0f, 0.0f, 360f, "%.1f°")) {
                keyframe.setWindDirection(windDirection[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            float[] cloudDensity = {keyframe.getCloudDensity()};
            if (ImGui.dragFloat("云密度", cloudDensity, 0.01f, 0.0f, 1.0f, "%.2f")) {
                keyframe.setCloudDensity(cloudDensity[0]);
                modified = true;
            }
            
            float[] precipitation = {keyframe.getPrecipitationAmount()};
            if (ImGui.dragFloat("降水量", precipitation, 0.01f, 0.0f, 10f, "%.2f mm/h")) {
                keyframe.setPrecipitationAmount(precipitation[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
        }
        
        return modified;
    }
    
    @Override
    public Class<WeatherKeyframe> getTargetType() {
        return WeatherKeyframe.class;
    }
}

