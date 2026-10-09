package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;

/** Reports current authored movement sources without changing legacy playback priority. */
public final class ActorMovementSource {
    private ActorMovementSource() {}
    public static ActorRouteClip activeRoute(TimelineTrack owner, float time) {
        if (owner == null || owner.isMuted() || owner.getActorTrackPayload() == null || !Float.isFinite(time)) return null;
        ActorRouteClip active = null;
        for (var clip : owner.getActorTrackPayload().getClips())
            if (clip instanceof ActorRouteClip route && !route.isMuted() && !route.getWaypoints().isEmpty()
                    && time >= route.getStartTime() && time <= route.getEndTime()
                    && (active == null || route.getStartTime() > active.getStartTime())) active = route;
        return active;
    }
    public static boolean routeOwnsInterval(TimelineModel model, TimelineTrack owner) {
        return model != null && activeRoute(owner, model.getCurrentTime()) != null;
    }
    public static boolean transformOverridesRoute(TimelineTrack owner, float time) {
        if (activeRoute(owner, time) == null) return false;
        return owner.getActorTrackPayload().getClips().stream().anyMatch(c -> c instanceof EntityTransformClip
                && !c.isMuted() && time >= c.getStartTime() && time <= c.getEndTime());
    }
}
