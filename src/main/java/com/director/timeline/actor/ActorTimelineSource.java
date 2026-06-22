package com.director.timeline.actor;

import com.director.timeline.model.TimelineTrack;

import java.util.List;

/**
 * 演员运行时所需的时间线视图（窄端口）。
 * <p>
 * {@link com.director.timeline.model.TimelineModel} 直接实现本接口；
 * {@link com.director.actor.runtime.ActorRuntimeController} 仅依赖此端口，不持有完整模型。
 */
public interface ActorTimelineSource {

    float getCurrentTime();

    List<TimelineTrack> getTracks();

    void addTrackStructureListener(TrackStructureListener listener);

    void removeTrackStructureListener(TrackStructureListener listener);

    @FunctionalInterface
    interface TrackStructureListener {
        void onTrackStructureChanged();
    }
}
