package com.director.timeline.audio;

import com.director.timeline.model.TimelineModel;

/**
 * 持久化加载阶段与音频服务协作的端口（抑制自动创建、回绑模型）。
 */
public interface TimelineAudioLoadHook {

    TimelineAudioLoadHook NOOP = new TimelineAudioLoadHook() {
        @Override
        public boolean isSuppressAutoCreate() {
            return true;
        }

        @Override
        public void setSuppressAutoCreate(boolean suppress) {
        }

        @Override
        public void setTimelineModel(TimelineModel model) {
        }
    };

    boolean isSuppressAutoCreate();

    void setSuppressAutoCreate(boolean suppress);

    void setTimelineModel(TimelineModel model);
}
