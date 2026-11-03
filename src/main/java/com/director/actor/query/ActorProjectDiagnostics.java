package com.director.actor.query;

import com.director.actor.*;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.timeline.model.*;
import java.util.*;

/** Explicit read-only audit of stored references, including muted and future clips. */
public final class ActorProjectDiagnostics {
    public enum Reason { MISSING_ACTOR, MISSING_ANIMATION, INCOMPATIBLE_RIG, MISSING_CUSTOM_RIG, MISSING_CUSTOM_MODEL, MODEL_RIG_MISMATCH, MISSING_RELATION_TARGET, SELF_RELATION, MISSING_PATH, UNSUPPORTED_ACTION, UNSUPPORTED_RELATION }
    public record Issue(TimelineTrack track, Reason reason, String reference,
            com.director.timeline.model.entity.EntityClip clip) {
        public Issue(TimelineTrack track, Reason reason, String reference) { this(track, reason, reference, null); }
        public com.director.timeline.model.entity.EntityTimelineClip currentWrapper(TimelineModel model) {
            if (clip == null || model == null || !model.getAllTracks().contains(track)
                    || track.getActorTrackPayload() == null || !track.getActorTrackPayload().getClips().contains(clip)) return null;
            var wrapper = clip.getTimelineClip();
            return wrapper != null && wrapper.getEntityClip() == clip && wrapper.getParentTrack() == track
                    && track.getClips().contains(wrapper) ? wrapper : null;
        }
    }
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
            if (actor != null && actor.getType() == ActorType.CUSTOM) {
                if (actor.getCustomRigId() != null && com.director.actor.rig.skeleton.CustomRigLibrary.getInstance().find(actor.getCustomRigId()).isEmpty())
                    issues.add(new Issue(track, Reason.MISSING_CUSTOM_RIG, actor.getCustomRigId()));
                if (actor.getCustomModelId() != null) {
                    var customModel = com.director.actor.model.CustomModelLibrary.getInstance().find(actor.getCustomModelId()).orElse(null);
                    if (customModel == null) issues.add(new Issue(track, Reason.MISSING_CUSTOM_MODEL, actor.getCustomModelId()));
                    else if (!Objects.equals(customModel.rigId(), actor.getCustomRigId()))
                        issues.add(new Issue(track, Reason.MODEL_RIG_MISMATCH, actor.getCustomModelId()));
                }
            }
            for (var clip : payload.getClips()) {
                if (clip instanceof com.director.timeline.model.entity.ActorRouteClip route && route.getSourcePathId() != null
                        && com.director.actor.path.ActorPathLibraryManager.getPath(route.getSourcePathId()) == null)
                    issues.add(new Issue(track, Reason.MISSING_PATH, clip.getName() + " · " + route.getSourcePathId(), clip));
                if (clip instanceof com.director.timeline.model.entity.ActorActionClip action && actor != null
                        && !ActorBehaviorCapabilities.supportsAction(actor, action.getActionType()))
                    issues.add(new Issue(track, Reason.UNSUPPORTED_ACTION, clip.getName() + " · " + action.getActionType(), clip));
                if (clip instanceof com.director.timeline.model.entity.ActorRelationClip relation) {
                    if (!ActorBehaviorCapabilities.supportsRelation(relation.getRelationType()))
                        issues.add(new Issue(track, Reason.UNSUPPORTED_RELATION, clip.getName() + " · " + relation.getRelationType(), clip));
                    UUID target = relation.getTargetActorId();
                    if (target == null || registry.getActor(target).isEmpty())
                        issues.add(new Issue(track, Reason.MISSING_RELATION_TARGET, clip.getName() + " · " + target, clip));
                    else if (actor != null && actor.getActorId().equals(target))
                        issues.add(new Issue(track, Reason.SELF_RELATION, clip.getName(), clip));
                }
            }
            for (var clip : payload.getClips()) for (String id : ActorAnimationReferences.ids(clip)) {
                var asset = AnimationAssetLibrary.getInstance().find(id).orElse(null);
                boolean builtIn = AnimationLibrary.getInstance().get(id).isPresent();
                if (asset == null && !builtIn) issues.add(new Issue(track, Reason.MISSING_ANIMATION, id, clip));
                else if (actor != null && (asset != null ? !ActorRigCompatibility.accepts(actor, asset.rigId())
                        : ActorRigCompatibility.family(actor.getType()) != ActorRigCompatibility.Family.HUMANOID))
                    issues.add(new Issue(track, Reason.INCOMPATIBLE_RIG, id, clip));
            }
        }
        return List.copyOf(issues);
    }
}
