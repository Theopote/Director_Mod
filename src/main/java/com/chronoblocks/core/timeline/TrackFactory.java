package com.chronoblocks.core.timeline;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.timeline.model.TimelineTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.UUID;

/**
 * 轨道工厂
 * 负责创建各种类型的轨道
 */
public final class TrackFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackFactory.class);

    private TrackFactory() {}

    /**
     * 创建容器轨道
     */
    public static TimelineTrack createContainerTrack(Container container, Color tagColor) {
        if (container == null) {
            throw new IllegalArgumentException("Container cannot be null");
        }

        TimelineTrack track = new TimelineTrack("Track_" + container.getName());
        track.setId(UUID.randomUUID());
        track.setBoundTarget(container);              // 绑定容器
        track.setColor(tagColor != null ? tagColor : new Color(64, 160, 255));
        track.setBlendMode(TimelineTrack.BlendMode.Normal);
        track.setWeight(1.0f);
        
        LOGGER.info("[TrackFactory] 创建容器轨道: {} -> {}", track.getName(), container.getName());
        return track;
    }

    /**
     * 创建容器轨道（使用默认颜色）
     */
    public static TimelineTrack createContainerTrack(Container container) {
        return createContainerTrack(container, new Color(64, 160, 255));
    }

    /**
     * 创建空轨道
     */
    public static TimelineTrack createEmptyTrack(String name) {
        TimelineTrack track = new TimelineTrack(name);
        track.setId(UUID.randomUUID());
        track.setColor(new Color(128, 128, 128));
        track.setBlendMode(TimelineTrack.BlendMode.Normal);
        track.setWeight(1.0f);
        
        LOGGER.info("[TrackFactory] 创建空轨道: {}", name);
        return track;
    }

    /**
     * 创建音频轨道
     */
    public static TimelineTrack createAudioTrack(String name) {
        TimelineTrack track = new TimelineTrack(name);
        track.setId(UUID.randomUUID());
        track.setColor(new Color(255, 128, 0)); // 橙色表示音频
        track.setBlendMode(TimelineTrack.BlendMode.Normal);
        track.setWeight(1.0f);
        
        LOGGER.info("[TrackFactory] 创建音频轨道: {}", name);
        return track;
    }

    /**
     * 创建特效轨道
     */
    public static TimelineTrack createEffectTrack(String name) {
        TimelineTrack track = new TimelineTrack(name);
        track.setId(UUID.randomUUID());
        track.setColor(new Color(255, 0, 255)); // 紫色表示特效
        track.setBlendMode(TimelineTrack.BlendMode.Normal);
        track.setWeight(1.0f);
        
        LOGGER.info("[TrackFactory] 创建特效轨道: {}", name);
        return track;
    }
}
