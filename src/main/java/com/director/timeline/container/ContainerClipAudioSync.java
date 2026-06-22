package com.director.timeline.container;

import com.director.timeline.model.TimelineClip;

import java.util.UUID;

/**
 * 容器片段与音频轨道同步的窄端口（由 UI 层 {@code TimelineAudioService} 实现）。
 */
public interface ContainerClipAudioSync {

    void onClipRemoved(TimelineClip clip);

    void updateLinkedAudioClipLabel(UUID containerId, String newName);
}
