package com.director.timeline.audio;

import com.director.timeline.model.TimelineClip;

/**
 * 容器片段绑定后自动收集声音事件的端口（由 UI 层 {@code TimelineAudioService} 实现）。
 */
public interface TimelineClipSoundCollector {

    void collectAndAddSoundEvents(TimelineClip clip);
}
