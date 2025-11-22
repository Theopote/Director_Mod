package com.chronoblocks.animation.timeline;

import com.chronoblocks.animation.core.AnimationClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AnimationTimelineService 提供时间线与外部系统的统一交互入口。
 */
public final class AnimationTimelineService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationTimelineService.class);
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

    public Optional<ClipBinding> getBinding(UUID containerId) {
        if (containerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bindings.get(containerId));
    }

    public java.util.List<UUID> getControlledContainers() {
        return java.util.List.copyOf(bindings.keySet());
    }

    public void focusClip(UUID clipId) {
        if (clipId == null) {
            return;
        }
        bindings.values().stream()
            .map(ClipBinding::clip)
            .filter(clip -> clip.getId().equals(clipId))
            .findFirst()
            .ifPresent(clip ->
                LOGGER.info("[TimelineService] 定位到 Clip {} (容器 {})", clip.getId(), clip.getBoundContainerId())
            );
    }

    public record ClipBinding(AnimationClip clip, Instant createdAt) {
        public UUID containerId() {
            return clip.getBoundContainerId();
        }

        public String clipName() {
            return clip.getName();
        }

        public double startSeconds() {
            return clip.getStart();
        }

        public double endSeconds() {
            return clip.getEnd();
        }
    }
}