package com.chronoblocks.ui.timeline;

import com.chronoblocks.ui.theme.Icons;

/**
 * 时间线轨道类型枚举（参考专业动画软件）
 * 
 * <p>ChronoBlocks 支持多种轨道类型：
 * <ul>
 *   <li>📦 容器轨道 - 控制方块容器的显示/隐藏和动画</li>
 *   <li>📷 摄像机轨道 - 控制观察视角和镜头运动</li>
 *   <li>🌤️ 天气轨道 - 控制 Minecraft 天气和时间</li>
 *   <li>💡 光照轨道 - 控制光源和环境光照</li>
 *   <li>✨ 特效轨道 - 控制粒子特效和视觉效果</li>
 *   <li>🎵 音频轨道 - 控制音效和音乐</li>
 *   <li>📝 标记轨道 - 添加时间标记和注释</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-18
 */
public enum TrackType {
    /**
     * 容器轨道 - 控制方块容器的显示/隐藏和动画
     * <p>这是 ChronoBlocks 的核心功能，用于控制已保存的方块结构
     */
    CONTAINER(
        "容器轨道",
        Icons.CONTAINER,
        rgba(80, 140, 200, 255),   // 蓝色系
        rgba(60, 110, 170, 255),   // 深蓝背景
        "控制方块容器的显示/隐藏和动画效果"
    ),
    
    /**
     * 摄像机轨道 - 控制观察视角和镜头运动
     * <p>可以创建平滑的摄像机运动，实现专业的镜头效果
     */
    CAMERA(
        "摄像机轨道",
        Icons.CAMERA,
        rgba(220, 180, 80, 255),   // 金黄色
        rgba(160, 130, 50, 255),   // 深金色背景
        "控制观察视角和镜头运动"
    ),
    
    /**
     * 天气轨道 - 控制 Minecraft 天气和时间
     * <p>可以控制游戏内的时间流逝、天气变化（晴天/雨天/雷暴）
     */
    WEATHER(
        "天气轨道",
        Icons.TIME,  // 使用时间图标
        rgba(120, 180, 220, 255),  // 天蓝色
        rgba(80, 140, 180, 255),   // 深天蓝背景
        "控制 Minecraft 天气和时间"
    ),
    
    /**
     * 光照轨道 - 控制光源和环境光照
     * <p>可以创建动态光照效果，如闪烁、渐变等
     */
    LIGHTING(
        "光照轨道",
        Icons.LIGHTBULB,  // 修正为 LIGHTBULB
        rgba(255, 220, 100, 255),  // 亮黄色
        rgba(200, 170, 60, 255),   // 深黄背景
        "控制光源和环境光照"
    ),
    
    /**
     * 特效轨道 - 控制粒子特效和视觉效果
     * <p>可以添加粒子效果、爆炸、烟雾等视觉特效
     */
    EFFECT(
        "特效轨道",
        Icons.PARTICLE,  // 使用粒子图标
        rgba(200, 100, 220, 255),  // 紫色
        rgba(150, 70, 180, 255),   // 深紫背景
        "控制粒子特效和视觉效果"
    ),
    
    /**
     * 音频轨道 - 控制音效和音乐
     * <p>可以添加背景音乐、音效，实现音画同步
     */
    AUDIO(
        "音频轨道",
        Icons.MUSIC,
        rgba(100, 200, 150, 255),  // 青绿色
        rgba(70, 150, 110, 255),   // 深青绿背景
        "控制音效和音乐"
    ),
    
    /**
     * 标记轨道 - 添加时间标记和注释
     * <p>用于标记重要时间点、添加注释，方便编辑
     */
    MARKER(
        "标记轨道",
        Icons.TAG,
        rgba(180, 180, 180, 255),  // 灰色
        rgba(100, 100, 100, 255),  // 深灰背景
        "添加时间标记和注释"
    );
    
    private final String displayName;
    private final String icon;
    private final int clipColor;       // 片段默认颜色
    private final int backgroundColor;  // 轨道背景色
    private final String description;
    
    TrackType(String displayName, String icon, int clipColor, int backgroundColor, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.clipColor = clipColor;
        this.backgroundColor = backgroundColor;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
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
        return description;
    }
    
    /**
     * 获取带图标的显示名称
     */
    public String getIconAndName() {
        return icon + " " + displayName;
    }
    
    /**
     * 根据名称查找轨道类型
     */
    public static TrackType fromName(String name) {
        for (TrackType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return CONTAINER;  // 默认返回容器轨道
    }
    
    private static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }
}

