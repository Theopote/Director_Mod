package com.chronoblocks.ui.timeline.model.weather;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;

import java.util.UUID;

/**
 * 天气关键帧
 * 
 * <p>表示时间线上的一个天气关键帧，包含天气类型、强度等信息。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>支持天气动画（晴天、雨天、雪天等）</li>
 *   <li>可以在时间线上选择和编辑</li>
 *   <li>属性面板可以显示天气相关属性</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WeatherKeyframe implements TimelineSelectable {
    
    public enum WeatherType {
        CLEAR("晴天"),
        RAIN("雨天"),
        SNOW("雪天"),
        THUNDER("雷雨"),
        FOG("雾天");
        
        private final String displayName;
        
        WeatherType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final UUID id;
    private String name;
    private float time;
    private WeatherType weatherType;
    private float intensity; // 强度（0.0 - 1.0）
    private float duration; // 持续时间（秒）
    
    // 天气特效参数
    private float windSpeed;
    private float windDirection; // 风向（度）
    private float cloudDensity;
    private float precipitationAmount;
    
    public WeatherKeyframe(String name, float time) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.time = time;
        this.weatherType = WeatherType.CLEAR;
        this.intensity = 1.0f;
        this.duration = 10.0f;
        this.windSpeed = 0.0f;
        this.windDirection = 0.0f;
        this.cloudDensity = 0.5f;
        this.precipitationAmount = 0.0f;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public float getStartTime() {
        return time;
    }
    
    @Override
    public float getEndTime() {
        return time + duration; // 天气关键帧有持续时间
    }
    
    @Override
    public String getType() {
        return "WeatherKeyframe";
    }
    
    // Getters and Setters
    public float getTime() { return time; }
    public void setTime(float time) { this.time = time; }
    
    public WeatherType getWeatherType() { return weatherType; }
    public void setWeatherType(WeatherType type) { this.weatherType = type; }
    
    public float getIntensity() { return intensity; }
    public void setIntensity(float intensity) { this.intensity = Math.max(0.0f, Math.min(1.0f, intensity)); }
    
    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = Math.max(0.0f, duration); }
    
    public float getWindSpeed() { return windSpeed; }
    public void setWindSpeed(float speed) { this.windSpeed = Math.max(0.0f, speed); }
    
    public float getWindDirection() { return windDirection; }
    public void setWindDirection(float direction) { this.windDirection = direction; }
    
    public float getCloudDensity() { return cloudDensity; }
    public void setCloudDensity(float density) { this.cloudDensity = Math.max(0.0f, Math.min(1.0f, density)); }
    
    public float getPrecipitationAmount() { return precipitationAmount; }
    public void setPrecipitationAmount(float amount) { this.precipitationAmount = Math.max(0.0f, amount); }
}

