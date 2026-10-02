package com.director.actor;

import com.director.actor.action.ActorActionType;
import com.director.timeline.model.entity.ActorRelationClip;

/** Authoring choices backed by the currently implemented runtime. */
public final class ActorBehaviorCapabilities {
    private ActorBehaviorCapabilities() {}
    public static boolean supportsAction(Actor actor, ActorActionType action) {
        return actor != null && actor.getRigProfile() != null && action != null && actor.getRigProfile().supports(action);
    }
    public static boolean supportsRelation(ActorRelationClip.RelationType type) {
        return type == ActorRelationClip.RelationType.FACE_TO_FACE || type == ActorRelationClip.RelationType.FOLLOW
                || type == ActorRelationClip.RelationType.CIRCLE_AROUND || type == ActorRelationClip.RelationType.RELATIVE_POSITION;
    }
}
