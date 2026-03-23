package com.director.ui.timeline.model;

/**
 * 时间线插值类型枚举
 * 
 * <p>用于控制关键帧之间的插值方式，影响动画的平滑程度和视觉效果。
 * 
 * @author Director Team
 * @version 1.0
 */
public enum TimelineInterpolation {
    /**
     * 线性插值 - 匀速运动
     * 适用于机械运动、摄像机移动等需要恒定速度的场景
     */
    LINEAR("线性", "匀速运动"),

    /**
     * 平滑插值 - 缓入缓出
     * 适用于大多数自然动画，提供平滑的加速和减速
     */
    SMOOTH("平滑", "缓入缓出"),

    /**
     * 阶梯插值 - 立即跳变
     * 适用于开关效果、状态切换等需要瞬间变化的场景
     */
    STEP("阶梯", "立即跳变");

    private final String displayName;
    private final String description;

    TimelineInterpolation(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}