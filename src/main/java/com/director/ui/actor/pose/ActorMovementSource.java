package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;

/** Reports the authored movement sources used by playback. */
public final class ActorMovementSource {
    private ActorMovementSource() {}
    public static ActorRouteClip activeRoute(TimelineTrack owner, float time) {
        if (owner == null || owner.isMuted()) return null;
        var payload = owner.getActorTrackPayload();
        return payload == null || !payload.isEnabled() ? null : payload.findRouteClipAt(time);
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
