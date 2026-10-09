package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Bounded contact correction for the standard rigid-legged player, not knee IK. */
public final class ActorRouteFootContact {
    private ActorRouteFootContact() {}
    public static void apply(RigPose pose, EntitySample sample) {
        if (sample.routeClip == null || sample.routeClip.getLoopMode() != ActorRouteClip.LoopMode.ONCE
                || !sample.routeClip.isAlignRotationToRoute() || sample.actionExplicit || sample.relationEnabled
                || sample.poseOverrideEnabled || sample.speed <= .02f || !pose.rig().id().equals(HumanoidRig.DEFINITION.id())
                || sample.scale.x <= .001f || sample.scale.y <= .001f || sample.scale.z <= .001f) return;
        float distance = ActorRouteMotion.once(sample.routeClip, sample.actionLocalTime).distance();
        correct(pose, sample, HumanoidRig.LEFT_LEG, distance, 0);
        correct(pose, sample, HumanoidRig.RIGHT_LEG, distance, .5f);
    }
    private static void correct(RigPose pose, EntitySample sample, String bone, float distance, float shift) {
        float stride = .8f * sample.scale.z;
        float cycle = distance / stride + shift;
        float phase = cycle - (float)Math.floor(cycle);
        if (phase >= .5f) return;
        float weight = Math.min(1, phase / .08f) * Math.min(1, (.5f - phase) / .08f);
        float contactDistance = Math.max(0, ((float)Math.floor(cycle) - shift + .25f) * stride);
        var routePoint = ActorRouteSampler.sampleAtDistance(sample.routeClip.getWaypoints(), contactDistance, sample.routeClip.getInterpolationMode());
        float yaw = (float)Math.atan2(-routePoint.direction().x, routePoint.direction().z);
        var rest = pose.rig().bone(pose.rig().requireBoneIndex(bone)).restLocal().translation();
        var side = new Quaternionf().rotateY(-yaw).transform(new Vector3f(rest.x * sample.scale.x, 0, 0));
        var target = new Vector3f(routePoint.position()).add(side).sub(sample.position);
        new Quaternionf().rotateY((float)Math.toRadians(sample.rotation.x)).transform(target);
        target.div(sample.scale);
        var current = pose.localTransform(bone);
        var sole = current.rotation().transform(new Vector3f(0, -.75f * current.scale().y, 0)).add(current.translation());
        var correction = target.sub(sole);
        if (correction.length() > .3f) correction.normalize().mul(.3f);
        var translation = current.translation().add(correction.mul(weight));
        pose.setLocalTransform(pose.rig().requireBoneIndex(bone), new BoneTransform(translation, current.rotation(), current.scale()), RigPose.TRANSLATION);
    }
}
