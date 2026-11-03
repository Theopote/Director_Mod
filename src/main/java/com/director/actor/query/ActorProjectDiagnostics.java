package com.director.actor.query;

import com.director.actor.*;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.timeline.model.*;
import java.util.*;

/** Explicit read-only audit of stored references, including muted and future clips. */
public final class ActorProjectDiagnostics {
    public enum Reason { MISSING_ACTOR, MISSING_ANIMATION, INCOMPATIBLE_RIG }
    public record Issue(TimelineTrack track, Reason reason, String reference) {}
    private ActorProjectDiagnostics() {}
    public static List<Issue> scan(TimelineModel model, ActorRegistry registry) {
        if (model == null) return List.of();
        List<Issue> issues = new ArrayList<>();
        for (var track : model.getAllTracks()) {
            var payload = track.getActorTrackPayload();
            if (payload == null) continue;
            var actor = payload.getActorRef() == null ? null : registry.getActor(payload.getActorRef().getActorId()).orElse(null);
            if (actor == null) issues.add(new Issue(track, Reason.MISSING_ACTOR,
                    payload.getActorRef() == null ? "" : payload.getActorRef().getActorId().toString()));
            for (String id : ActorAnimationReferences.ids(payload)) {
                var asset = AnimationAssetLibrary.getInstance().find(id).orElse(null);
                boolean builtIn = AnimationLibrary.getInstance().get(id).isPresent();
                if (asset == null && !builtIn) issues.add(new Issue(track, Reason.MISSING_ANIMATION, id));
                else if (actor != null && (asset != null ? !ActorRigCompatibility.accepts(actor, asset.rigId())
                        : ActorRigCompatibility.family(actor.getType()) != ActorRigCompatibility.Family.HUMANOID))
                    issues.add(new Issue(track, Reason.INCOMPATIBLE_RIG, id));
            }
        }
        return List.copyOf(issues);
    }
}
