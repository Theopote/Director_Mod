package com.chronoblocks.animation.timeline;

import com.chronoblocks.animation.core.AnimationClip;
import com.chronoblocks.core.container.Container;
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

    public ClipBinding createClipForContainer(Container container) {
        if (container == null) {
            throw new IllegalArgumentException("container cannot be null");
        }
        UUID containerId = container.getId();
        return bindings.compute(containerId, (id, existing) -> {
            if (existing != null) {
                LOGGER.info("[TimelineService] 容器 {} 已存在绑定 Clip {}", container.getName(), existing.clip().getId());
                return existing;
            }
            AnimationClip clip = timelineController.createClipFor(containerId, container.getName() + " Clip");
            ClipBinding binding = new ClipBinding(clip, Instant.now());
            LOGGER.info("[TimelineService] 已为容器 {} 创建占位 Clip {} ({}s-{}s)",
                container.getName(), clip.getId(), clip.getStart(), clip.getEnd());
            return binding;
        });
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

    public void removeBinding(UUID containerId) {
        if (containerId == null) {
            return;
        }
        ClipBinding removed = bindings.remove(containerId);
        if (removed != null) {
            timelineController.getPlayableGraph().removeClip(removed.clip());
            LOGGER.info("[TimelineService] 已移除容器 {} 的 Clip 绑定 {}", containerId, removed.clip().getId());
        }
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


