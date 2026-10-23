package com.director.ui.actor.pose;

import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.preview.ActorPosePreview;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.RigWorldPose;

import java.util.UUID;

/** Resolves the selected preview actor's indexed pose into world space. */
public final class ActorBoneWorldQuery {
    private ActorBoneWorldQuery() {}

    public static RigWorldPose resolve(UUID actorId) {
        if (!com.director.actor.rig.ActorRigCompatibility.editable(actorId)) return null;
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview entity)) {
            return null;
        }
        RigPose pose = entity.getFinalRigPose();
        if (pose == null) return null;
        return com.director.actor.runtime.ActorWorldPoseEvaluator.resolve(pose,
                new org.joml.Vector3f((float)entity.getX(),(float)entity.getY(),(float)entity.getZ()),
                entity.getYaw(),entity.getActorTransformScale(),entity instanceof com.director.actor.preview.PreviewPlayerActorEntity);
    }
}
