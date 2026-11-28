package com.director.timeline.container;

import com.director.timeline.model.TimelineClip;

import java.util.UUID;

/**
 * 容器片段与音频轨道同步的窄端口（由 UI 层 {@code TimelineAudioService} 实现）。
 */
public interface ContainerClipAudioSync {

    ContainerClipAudioSync NOOP = new ContainerClipAudioSync() {
        @Override
        public void onClipRemoved(TimelineClip clip) {
        }

        @Override
        public void updateLinkedAudioClipLabel(UUID containerId, String newName) {
        }
    };

    void onClipRemoved(TimelineClip clip);

    void updateLinkedAudioClipLabel(UUID containerId, String newName);

    /**
     * 捕获片段关联音频的删除前状态。默认端口无需恢复任何音频。
     * 实现类已提供同名方法时无需额外适配。
     */
    default Runnable captureRemovalState(TimelineClip clip) {
        return () -> { };
    }
}
