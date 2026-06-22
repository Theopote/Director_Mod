package com.director.ui.timeline.interaction;

import com.director.actor.action.ActorActionType;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineMarker;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import com.director.ui.timeline.model.entity.ActorTrack;

/**
 * 拖动/涂抹交互期间持有的临时状态对象。
 */
public final class TimelineDragHandles {

    private TimelineDragHandles() {}

    public static final class ClipHandle {
        public final TimelineTrack track;
        public final TimelineClip clip;

        public ClipHandle(TimelineTrack track, TimelineClip clip) {
            this.track = track;
            this.clip = clip;
        }
    }

    public static final class KeyframeHandle {
        public final TimelineTrack track;
        public final TimelineClip clip;
        public final com.director.ui.timeline.model.clip.ClipKeyframe keyframe;
        public float currentTime;

        public KeyframeHandle(
                TimelineTrack track,
                TimelineClip clip,
                com.director.ui.timeline.model.clip.ClipKeyframe keyframe,
                float time) {
            this.track = track;
            this.clip = clip;
            this.keyframe = keyframe;
            this.currentTime = time;
        }
    }

    public static final class AutomationKeyframeHandle {
        public final TimelineTrack originalTrack;
        public TimelineTrack currentTrack;
        public final TimelineSelectable keyframe;
        public float currentTime;

        public AutomationKeyframeHandle(TimelineTrack track, TimelineSelectable keyframe) {
            this.originalTrack = track;
            this.currentTrack = track;
            this.keyframe = keyframe;
            this.currentTime = keyframe.getStartTime();
        }
    }

    public static final class MarkerHandle {
        public final TimelineMarker marker;
        public float currentTime;

        public MarkerHandle(TimelineMarker marker) {
            this.marker = marker;
            this.currentTime = marker.getTime();
        }
    }

    public static final class PathClipHandle {
        public final TimelineTrack track;
        public final CameraPathClip clip;
        public float tempStart;
        public float tempEnd;

        public PathClipHandle(TimelineTrack track, CameraPathClip clip) {
            this.track = track;
            this.clip = clip;
            this.tempStart = clip.getStartTime();
            this.tempEnd = clip.getEndTime();
        }
    }

    public static final class ActionPaintHandle {
        public final TimelineTrack track;
        public final ActorTrack actorTrack;
        public final ActorActionType actionType;
        public final float startTime;
        public float currentEndTime;

        public ActionPaintHandle(
                TimelineTrack track,
                ActorTrack actorTrack,
                ActorActionType actionType,
                float startTime) {
            this.track = track;
            this.actorTrack = actorTrack;
            this.actionType = actionType;
            this.startTime = startTime;
            this.currentEndTime = startTime;
        }
    }
}
