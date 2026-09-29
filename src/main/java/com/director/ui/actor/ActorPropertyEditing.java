package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.*;

/** Undoable project actor edits; UI supplies preview refresh after a type change. */
public final class ActorPropertyEditing {
    private ActorPropertyEditing() {}

    public static void rename(TimelineModel model, Actor actor, String name) {
        if (model == null || name == null || name.isBlank()) return;
        String before = actor.getName(), after = name.strip();
        if (before.equals(after)) return;
        var tracks = ActorUsageQuery.findTimelineTracksUsingActor(model, actor.getActorId());
        var names = tracks.stream().map(t -> t.getName()).toList();
        var displayNames = tracks.stream().map(t -> t.getActorTrackPayload().getDisplayName()).toList();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Rename actor", () -> {
            actor.setName(before);
            for (int i = 0; i < tracks.size(); i++) {
                tracks.get(i).setName(names.get(i));
                tracks.get(i).getActorTrackPayload().setDisplayName(displayNames.get(i));
            }
        }, () -> {
            actor.setName(after);
            for (var track : tracks) {
                track.setName(after);
                track.getActorTrackPayload().setDisplayName(after);
            }
        }));
    }

    public static void changeType(TimelineModel model, Actor actor, ActorType type, Runnable refresh) {
        if (model == null || type == null || type == actor.getType()) return;
        var before = actor.getType();
        var profile = actor.getRigProfile();
        // Capture the new default once so redo preserves the same profile instance.
        var nextProfile = new Actor("preview", type).getRigProfile();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Change actor type", () -> {
            actor.setType(before);
            actor.setRigProfile(profile);
            refresh.run();
        }, () -> {
            actor.setType(type);
            actor.setRigProfile(nextProfile);
            refresh.run();
        }));
    }
}
