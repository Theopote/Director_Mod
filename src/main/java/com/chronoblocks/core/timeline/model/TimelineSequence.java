package com.chronoblocks.core.timeline.model;

import java.util.*;

/**
 * 时间线序列
 * 包含多个轨道的序列
 */
public class TimelineSequence {
    private final List<TimelineTrack> tracks = new ArrayList<>();
    private String name = "Sequence";
    private float duration = 10.0f; // 默认10秒
    private float currentTime = 0.0f;

    public TimelineSequence() {
    }

    public TimelineSequence(String name) {
        this.name = name;
    }

    /**
     * 添加轨道
     */
    public void addTrack(TimelineTrack track) {
        if (track != null && !tracks.contains(track)) {
            tracks.add(track);
        }
    }

    /**
     * 移除轨道
     */
    public boolean removeTrack(TimelineTrack track) {
        return tracks.remove(track);
    }

    /**
     * 根据ID移除轨道
     */
    public boolean removeTrackById(UUID trackId) {
        return tracks.removeIf(track -> track.getId().equals(trackId));
    }

    /**
     * 获取所有轨道
     */
    public List<TimelineTrack> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    /**
     * 根据名称获取轨道
     */
    public Optional<TimelineTrack> getTrackByName(String name) {
        return tracks.stream()
                .filter(track -> track.getName().equals(name))
                .findFirst();
    }

    /**
     * 根据ID获取轨道
     */
    public Optional<TimelineTrack> getTrackById(UUID id) {
        return tracks.stream()
                .filter(track -> track.getId().equals(id))
                .findFirst();
    }

    /**
     * 获取轨道数量
     */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * 设置名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置持续时间
     */
    public void setDuration(float duration) {
        this.duration = duration;
    }

    /**
     * 获取持续时间
     */
    public float getDuration() {
        return duration;
    }

    /**
     * 设置当前时间
     */
    public void setCurrentTime(float currentTime) {
        this.currentTime = Math.max(0, Math.min(currentTime, duration));
    }

    /**
     * 获取当前时间
     */
    public float getCurrentTime() {
        return currentTime;
    }

    /**
     * 清空所有轨道
     */
    public void clearTracks() {
        tracks.clear();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("TimelineSequence{name='%s', tracks=%d, duration=%.2f}", 
                name, tracks.size(), duration);
    }
}
