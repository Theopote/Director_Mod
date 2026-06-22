package com.director.ui.timeline.model.audio;

import com.director.timeline.model.TimelineClip;

/**
 * AudioTimelineClip
 *
 * <p>用于表示音频轨道上的“音频片段”（用于承载音量自动化/与容器片段的链接信息）。</p>
 *
 * <p>它本质仍是 {@link TimelineClip}，但通过子类型让 UI（属性面板/渲染）能够区分音频片段
 * 与容器片段，避免错误走 {@code TimelineClipPropertyAdapter} 的“序列/动画”编辑器。</p>
 */
public class AudioTimelineClip extends TimelineClip {

    public AudioTimelineClip(String label, float startTime, float endTime, int color) {
        super(label, startTime, endTime, color);
    }

    public AudioTimelineClip(TimelineClip other) {
        super(other);
    }
}


