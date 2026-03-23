package com.director.core.animation.timeline;

import com.director.core.animation.container.AnimationClip;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AnimationTimelineService 提供时间线与外部系统的统一交互入口。
 */
public final class AnimationTimelineService {

    private static final AnimationTimelineService INSTANCE = new AnimationTimelineService();

    private final TimelineController timelineController = new TimelineController();
    private final Map<UUID, ClipBinding> bindings = new ConcurrentHashMap<>();

    private AnimationTimelineService() {
    }

    public static AnimationTimelineService get() {
        return INSTANCE;
    }

    public TimelineController getTimelineController() {
        return timelineController;
    }

    public void setCurrentTime(double timeSec) {
        timelineController.setCurrentTime(timeSec);
    }

    public java.util.List<UUID> getControlledContainers() {
        return java.util.List.copyOf(bindings.keySet());
    }

    public record ClipBinding(AnimationClip clip, Instant createdAt) {
        public UUID containerId() {
            return clip.getBoundContainerId();
        }

    }
}

