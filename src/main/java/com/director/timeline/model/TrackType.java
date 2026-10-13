package com.director.timeline.model;

import com.director.i18n.TrackI18n;
import com.director.ui.theme.Icons;

import java.util.EnumSet;

/**
 * 时间线轨道类型枚举（参考专业动画软件）。
 *
 * <p>Director 支持以下轨道类型：
 * <ul>
 *   <li>📦 容器轨道 - 控制方块容器的显隐和动画</li>
 *   <li>📷 摄像机轨道 - 控制观察视角和镜头运动</li>
 *   <li>🌤️ 天气轨道 - 控制 Minecraft 天气和光照</li>
 *   <li>🕐 时间轨道 - 控制完整一天的时间变化</li>
 *   <li>🔊 音频轨道 - 事件驱动型音频，自动同步动画声音</li>
 *   <li>🎭 演员轨道 - 在时间线上"导演"会动、会演戏的角色（NPC/玩家/动物/模型）</li>
 * </ul>
 */
public enum TrackType {
    /**
     * 容器轨道 - 控制方块容器的显隐和动画。
     * 
     * <p>支持普通容器和拆除容器（通过 ContainerType.DEMOLITION 区分）。
     * 拆除容器在播放时按片段配置逐步将方块置为 AIR（反向序列：从全量到空）。
     */
    CONTAINER(
        Icons.CONTAINER,
        rgba(80, 140, 200),
        rgba(60, 110, 170),
        EnumSet.of(TrackFeature.CLIPS, TrackFeature.CONTAINER_PAYLOAD, TrackFeature.MARKERS)
    ),

    /**
     * 摄像机轨道 - 控制观察视角和镜头运动。
     */
    CAMERA(
        Icons.CAMERA,
        rgba(220, 180, 80),
        rgba(160, 130, 50),
        EnumSet.of(TrackFeature.KEYFRAMES, TrackFeature.CAMERA_AUTOMATION)
    ),

    /**
     * 天气轨道 - 控制 Minecraft 天气和光照。
     */
    WEATHER(
        Icons.TIME,
        rgba(120, 180, 220),
        rgba(80, 140, 180),
        EnumSet.of(TrackFeature.KEYFRAMES, TrackFeature.WEATHER_AUTOMATION)
    ),

    /**
     * 时间轨道 - 控制完整一天的时间变化。
     */
    TIME(
        Icons.CLOCK,
        rgba(240, 160, 60),
        rgba(200, 120, 40),
        EnumSet.of(TrackFeature.KEYFRAMES, TrackFeature.TIME_AUTOMATION)
    ),

    /**
     * 季节轨道 - 控制长期气候趋势和颜色变化。
     */
    SEASON(
        Icons.CALENDAR,
        rgba(100, 200, 120),
        rgba(60, 160, 80),
        EnumSet.of(TrackFeature.KEYFRAMES, TrackFeature.SEASON_AUTOMATION)
    ),

    /**
     * 音频轨道 - 事件驱动型音频轨道。
     * 
     * <p>自动收集容器动画中的声音事件（方块放置/移除等），
     * 并自动同步到时间线上。导出动画时自动合成声音。
     */
    AUDIO(
        Icons.VOLUME,
        rgba(180, 100, 220),
        rgba(140, 70, 180),
        EnumSet.of(TrackFeature.AUDIO_EVENTS)
    ),

    /**
     * 演员轨道 - 在时间线上"导演"一个会动、会演戏的角色。
     * 
     * <p>演员轨道用于持续调度、表演和控制实体（NPC/玩家/动物/模型）的状态与动作。
     * 与容器轨道不同，演员轨道不是"被生成一次"，而是被持续调度、被表演、被控制状态与动作。
     * 
     * <p>支持的功能：
     * <ul>
     *   <li>关键帧控制 - 设置实体的位置、旋转、动作等关键帧</li>
     *   <li>动作片段 - 定义实体的动作序列和表演片段</li>
     *   <li>状态控制 - 控制实体的状态、动画、行为等</li>
     * </ul>
     */
    LIGHT(Icons.COLOR, rgba(240,220,100), rgba(150,130,60), EnumSet.of(TrackFeature.CLIPS)),

    ACTOR(
        Icons.SMILE,
        rgba(220, 120, 180),
        rgba(180, 80, 140),
        EnumSet.of(TrackFeature.CLIPS, TrackFeature.KEYFRAMES, TrackFeature.ACTOR_AUTOMATION)
    );

    private final String icon;
    private final int clipColor;        // 片段默认颜色
    private final int backgroundColor;  // 轨道背景色
    private final EnumSet<TrackFeature> features;

    TrackType(String icon,
              int clipColor,
              int backgroundColor,
              EnumSet<TrackFeature> features) {
        this.icon = icon;
        this.clipColor = clipColor;
        this.backgroundColor = backgroundColor;
        this.features = features != null ? features : EnumSet.noneOf(TrackFeature.class);
    }

    public String getDisplayName() {
        return TrackI18n.name(this);
    }

    public String getIcon() {
        return icon;
    }

    public int getClipColor() {
        return clipColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public String getDescription() {
        return TrackI18n.description(this);
    }

    public boolean supportsClips() {
        return features.contains(TrackFeature.CLIPS);
    }

    public boolean supportsKeyframes() {
        return features.contains(TrackFeature.KEYFRAMES)
            || features.contains(TrackFeature.CAMERA_AUTOMATION)
            || features.contains(TrackFeature.WEATHER_AUTOMATION)
            || features.contains(TrackFeature.TIME_AUTOMATION)
            || features.contains(TrackFeature.SEASON_AUTOMATION)
            || features.contains(TrackFeature.ACTOR_AUTOMATION)
            || features.contains(TrackFeature.MARKERS);
    }

    public boolean acceptsContainerPayload() {
        return features.contains(TrackFeature.CONTAINER_PAYLOAD);
    }

    public boolean supportsCameraPath() {
        return features.contains(TrackFeature.CAMERA_AUTOMATION);
    }

    public boolean supportsWeatherControl() {
        return features.contains(TrackFeature.WEATHER_AUTOMATION);
    }

    public boolean supportsTimeControl() {
        return features.contains(TrackFeature.TIME_AUTOMATION);
    }

    public boolean supportsSeasonControl() {
        return features.contains(TrackFeature.SEASON_AUTOMATION);
    }

    public boolean supportsMarkers() {
        return features.contains(TrackFeature.MARKERS);
    }

    public boolean supportsAudioEvents() {
        return features.contains(TrackFeature.AUDIO_EVENTS);
    }

    public boolean supportsEntityControl() {
        return features.contains(TrackFeature.ACTOR_AUTOMATION);
    }

    /**
     * 获取带图标的显示名称。
     */
    public String getIconAndName() {
        return TrackI18n.iconAndName(this);
    }

    /**
     * 根据名称查找轨道类型。
     */
    public static TrackType fromName(String name) {
        for (TrackType type : values()) {
            if (TrackI18n.name(type).equals(name)) {
                return type;
            }
        }
        if ("Actor Track".equalsIgnoreCase(name)) {
            return ACTOR;
        }
        return CONTAINER;  // 默认返回容器轨道
    }

    private static int rgba(int r, int g, int b) {
        return ((0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }

    /**
     * 轨道特性，用于声明该轨道能做什么。
     */
    public enum TrackFeature {
        CLIPS,
        KEYFRAMES,
        CONTAINER_PAYLOAD,
        CAMERA_AUTOMATION,
        WEATHER_AUTOMATION,
        TIME_AUTOMATION,
        SEASON_AUTOMATION,
        ACTOR_AUTOMATION,
        MARKERS,
        AUDIO_EVENTS
    }
}
