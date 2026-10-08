package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;

/** Explicit empty-track binding; selecting an unused actor never creates project data. */
public final class ActorTrackBindingEditing {
    private ActorTrackBindingEditing() {}
    public static TimelineTrack create(TimelineModel model, Actor actor) {
        requireActor(model,actor);
        if(!ActorUsageQuery.findTimelineTracksUsingActor(model,actor.getActorId()).isEmpty())
            throw new IllegalArgumentException("Actor already has a track");
        var track=new TimelineTrack(actor.getName(),TrackType.ACTOR);
        var payload=track.getOrCreateActorTrackPayload();
        payload.setActorRef(ActorRef.of(actor)); payload.setDisplayName(actor.getName());
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Bind actor track",() -> {
            requireActor(model,actor);
            if(!model.getAllTracks().contains(track) || track.getActorTrackPayload()!=payload
                    || payload.getActorRef()==null || !actor.getActorId().equals(payload.getActorRef().getActorId())
                    || !track.getClips().isEmpty() || !payload.getClips().isEmpty() || track.isLocked())
                throw new IllegalStateException("Actor track changed");
            model.removeTrack(track);
        },() -> {
            requireActor(model,actor);
            if(!ActorUsageQuery.findTimelineTracksUsingActor(model,actor.getActorId()).isEmpty())
                throw new IllegalStateException("Actor already has a track");
            model.addTrack(track);
        }));
        return track;
    }
    private static void requireActor(TimelineModel model,Actor actor) {
        if(model==null || model.isPlaying() || model.isScrubbing() || actor==null
                || ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElse(null)!=actor)
            throw new IllegalStateException("Actor binding is unavailable");
    }
}
