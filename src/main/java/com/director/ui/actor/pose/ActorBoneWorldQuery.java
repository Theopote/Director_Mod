package com.director.ui.actor.pose;

import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.preview.PreviewActorEntity;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.RigWorldPose;
import org.joml.Matrix4f;

import java.util.UUID;

/** Resolves the selected preview actor's indexed pose into world space. */
public final class ActorBoneWorldQuery {
    private ActorBoneWorldQuery() {}

    public static RigWorldPose resolve(UUID actorId) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof PreviewActorEntity entity)) {
            return null;
        }
        RigPose pose = entity.getFinalRigPose();
        if (pose == null) return null;
        Matrix4f actorTransform = new Matrix4f()
                .translate((float) entity.getX(), (float) entity.getY(), (float) entity.getZ())
                .rotateY((float) Math.toRadians(-entity.getYaw()));
        return RigWorldPose.resolve(pose, actorTransform);
    }
}
