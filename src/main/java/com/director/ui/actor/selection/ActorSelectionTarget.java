package com.director.ui.actor.selection;

import com.director.timeline.model.*;
import java.util.UUID;

/** Resolve a world actor to a unique timeline owner without choosing the first duplicate. */
public final class ActorSelectionTarget {
    private ActorSelectionTarget() {}
    public static TimelineTrack resolve(TimelineModel model, UUID id) {
        if(model==null || id==null) return null;
        try { return com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model,id); }
        catch(IllegalArgumentException unavailable) { return null; }
    }
}
