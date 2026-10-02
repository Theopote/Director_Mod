package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import java.util.*;

/** Prevent dependencies that can feed a previous-frame pose back into the same actor. */
final class ActorRelationPropertyValidation {
    private ActorRelationPropertyValidation() {}
    static void requireValid(TimelineModel model, UUID actor, EntityClip excluded, UUID target, float start, float end) {
        if (target == null) return; // Explicitly clearing a target disables this relationship.
        if (actor.equals(target) || ActorRegistryManager.getInstance().getActor(target).isEmpty())
            throw new IllegalArgumentException("关系目标不存在，或指向演员自身");
        if (reaches(model, excluded, actor, target, start, end, new HashSet<>()))
            throw new IllegalArgumentException("关系形成循环依赖，请修改目标或时间范围");
    }
    private static boolean reaches(TimelineModel model, EntityClip excluded, UUID root, UUID current,
                                   float start, float end, Set<UUID> visiting) {
        if (current.equals(root)) return true;
        if (!visiting.add(current)) return false;
        try {
            for (var track : model.getAllTracks()) {
                var payload = track.getActorTrackPayload();
                if (track.getType() != TrackType.ACTOR || track.isMuted() || payload == null || payload.getActorRef() == null
                        || !current.equals(payload.getActorRef().getActorId())) continue;
                for (var clip : payload.getClips()) if (clip != excluded && !clip.isMuted()
                        && clip instanceof ActorRelationClip relation && relation.getTargetActorId() != null
                        && ActorBehaviorCapabilities.supportsRelation(relation.getRelationType())) {
                    float intersectionStart = Math.max(start, relation.getStartTime());
                    float intersectionEnd = Math.min(end, relation.getEndTime());
                    if (intersectionStart <= intersectionEnd && reaches(model, excluded, root,
                            relation.getTargetActorId(), intersectionStart, intersectionEnd, visiting)) return true;
                }
            }
            return false;
        } finally { visiting.remove(current); }
    }
}
