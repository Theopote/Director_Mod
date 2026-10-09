package com.director.ui.actor.path;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.*;
import java.util.UUID;

/** Scene path visibility follows the current editing context. */
public final class ActorPathVisibility {
    private ActorPathVisibility() {}

    public static boolean shouldShow(UUID path, boolean pathTool, UUID selectedPath, UUID actorPath) {
        return path != null && (pathTool || path.equals(selectedPath) || path.equals(actorPath));
    }

    public static UUID boundPath(TimelineModel model) {
        if (model == null || !(model.getSelectedClip() instanceof EntityTimelineClip selected)) return null;
        var owner = selected.getParentTrack();
        if (owner == null || owner.isMuted() || !model.getAllTracks().contains(owner)
                || !owner.getClips().contains(selected)) return null;
        var payload = owner.getActorTrackPayload();
        if (payload == null || !payload.isEnabled()) return null;
        float time = model.getCurrentTime();
        // Selecting a route directly shows that binding even when the playhead is elsewhere.
        if (selected.getEntityClip() instanceof ActorRouteClip route && !route.isMuted()) return route.getSourcePathId();
        ActorRouteClip active = null;
        for (var clip : payload.getClips()) {
            if (clip instanceof ActorRouteClip route && !route.isMuted()
                    && time >= route.getStartTime() && time <= route.getEndTime()
                    && (active == null || route.getStartTime() > active.getStartTime())) active = route;
        }
        return active == null ? null : active.getSourcePathId();
    }
}
