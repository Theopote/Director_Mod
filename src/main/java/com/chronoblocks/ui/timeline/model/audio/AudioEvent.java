package com.chronoblocks.ui.timeline.model.audio;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import java.util.UUID;

/**
 * AudioEvent v1.0
 *
 * <p>时间线上一个独立的"声音事件"。
 *
 * <p>由事件驱动系统主动生成，也可允许用户手动创建。
 * 
 * <p>用于记录：
 * <ul>
 *   <li>方块放置音效</li>
 *   <li>方块破坏音效</li>
 *   <li>摄像机切换声</li>
 *   <li>动画事件触发声（粒子、机关等）</li>
 * </ul>
 *
 * <p>最终用途：
 * <ul>
 *   <li>时间线播放时发声</li>
 *   <li>视频导出时混音成最终音轨</li>
 * </ul>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>完全兼容 TimelineSelectable - 可选中、可拖动、可编辑、支持 Undo/Redo</li>
 *   <li>声音只作为"点事件"（无时长）- 轻量且常见</li>
 *   <li>支持 volume/pitch - 满足 Minecraft 声音系统基本参数</li>
 *   <li>enabled 字段 - 可用于临时静音单个事件</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class AudioEvent implements TimelineSelectable {

    private final UUID id;

    /** 事件名称（用于 UI 显示） */
    private String label;

    /** Minecraft 声音 ID，例如 "minecraft:block.stone.place" */
    private String soundId;

    /** 事件发生的绝对时间（秒） */
    private float time;

    /** 声音音量 0.0 - 1.0 */
    private float volume = 1.0f;

    /** 声音音高 0.1 - 3.0 */
    private float pitch = 1.0f;

    /** 是否启用（可用于临时静音单个事件） */
    private boolean enabled = true;

    /**
     * 构造函数
     * 
     * @param label 事件名称
     * @param soundId Minecraft 声音 ID
     * @param time 事件发生的绝对时间（秒）
     */
    public AudioEvent(String label, String soundId, float time) {
        this.id = UUID.randomUUID();
        this.label = label;
        this.soundId = soundId;
        this.time = time;
    }

    // ================================
    // TimelineSelectable 接口实现
    // ================================

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public float getStartTime() {
        return time;
    }

    @Override
    public float getEndTime() {
        return time; // AudioEvent 是点事件
    }

    /**
     * 设置事件时间（TimelineSelectable 接口不要求此方法，但提供便利方法）
     */
    public void setStartTime(float newTime) {
        this.time = newTime;
    }

    @Override
    public String getName() {
        return label != null ? label : "音频事件";
    }

    @Override
    public String getType() {
        return "AudioEvent";
    }

    // ================================
    // Getters / Setters
    // ================================

    public String getLabel() {
        return label;
    }

    public void setLabel(String name) {
        this.label = name;
    }

    public String getSoundId() {
        return soundId;
    }

    public void setSoundId(String soundId) {
        this.soundId = soundId;
    }

    /**
     * 获取事件时间（与 getStartTime() 相同，提供便利方法）
     */
    public float getTime() {
        return time;
    }

    /**
     * 设置事件时间（与 setStartTime() 相同，提供便利方法）
     */
    public void setTime(float time) {
        this.time = time;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float p) {
        this.pitch = Math.max(0.1f, Math.min(3.0f, p));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    // ================================
    // Convenience
    // ================================

    @Override
    public String toString() {
        return "AudioEvent{" +
                "id=" + id +
                ", label='" + label + '\'' +
                ", soundId='" + soundId + '\'' +
                ", time=" + time +
                ", volume=" + volume +
                ", pitch=" + pitch +
                ", enabled=" + enabled +
                '}';
    }
}
