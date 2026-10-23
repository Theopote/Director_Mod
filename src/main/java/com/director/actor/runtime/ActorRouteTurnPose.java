package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.ActorRouteMotion;
import com.director.timeline.playback.entity.EntitySample;

/** Stateless turn-in-place base pose, composed before presets, authored channels and constraints. */
public final class ActorRouteTurnPose {
    private ActorRouteTurnPose() {}

    public static void apply(RigPose pose, EntitySample sample) {
        if (!supports(pose, sample)) return;
        float p = ActorRouteMotion.stopTurn(sample.routeClip, sample.routeLocalTime).turnProgress();
        if (!(p > 0 && p < 1)) return;
        // Two smooth, non-overlapping lifts; both feet return to rest between steps.
        float lift = (float)Math.pow(Math.sin(2 * Math.PI * p), 2);
        if (!(sample.poseOverrideEnabled && sample.poseOverrideLegs)) {
            String hip = p < .5f ? HumanoidRig.LEFT_LEG : HumanoidRig.RIGHT_LEG;
            String knee = p < .5f ? ArticulatedPlayerRig.LEFT_SHIN : ArticulatedPlayerRig.RIGHT_SHIN;
            rotate(pose, hip, -.32f * lift, .1f * lift);
            rotate(pose, knee, .64f * lift, 0);
        }
        if (!(sample.poseOverrideEnabled && sample.poseOverrideBody))
            rotate(pose, HumanoidRig.BODY, 0, (float)(.1 * Math.sin(2 * Math.PI * p) * Math.sin(Math.PI * p)));
    }

    public static boolean supports(RigPose pose, EntitySample sample) {
        return pose != null && sample != null
                && RigPoseComposer.sameLayout(pose.rig(), ArticulatedPlayerRig.DEFINITION)
                && sample.routeClip != null && sample.routeClip.hasEndpointTurns()
                && sample.routeClip.isAlignRotationToRoute() && !sample.relationEnabled
                && (!sample.actionExplicit || sample.action == ActorActionType.WALK || sample.action == ActorActionType.RUN);
    }

    private static void rotate(RigPose pose, String bone, float pitch, float yaw) {
        int index = pose.rig().requireBoneIndex(bone);
        var local = pose.localTransform(index);
        pose.setLocalTransform(index, local.withRotation(local.rotation().rotateY(yaw).rotateX(pitch)), RigPose.ROTATION);
    }
}
