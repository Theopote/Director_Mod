package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Bounded contact correction using the complete leg chain, not terrain IK. */
public final class ActorRouteFootContact {
    private ActorRouteFootContact() {}
    public static void apply(RigPose pose, EntitySample sample) {
        apply(pose,sample,java.util.Set.of());
    }
    public static void apply(RigPose pose, EntitySample sample, java.util.Set<String> authoredBones) {
        var weights=new java.util.HashMap<String,Float>();
        authoredBones.forEach(bone -> weights.put(bone,1f));
        apply(pose,sample,weights);
    }
    /** Authored control progressively releases procedural contact, including at fade boundaries. */
    public static void apply(RigPose pose,EntitySample sample,java.util.Map<String,Float> authoredWeights) {
        if (sample.routeClip == null || sample.routeClip.getLoopMode() != ActorRouteClip.LoopMode.ONCE
                || !sample.routeClip.isAlignRotationToRoute() || sample.actionExplicit || sample.relationEnabled
                || sample.poseOverrideEnabled || sample.speed <= .02f || !HumanoidTopology.matches(pose.rig())
                || sample.scale.x <= .001f || sample.scale.y <= .001f || sample.scale.z <= .001f) return;
        float distance = ActorRouteMotion.once(sample.routeClip, sample.routeLocalTime).distance();
        correct(pose,sample,HumanoidRig.LEFT_LEG,distance,0,contactWeight(authoredWeights,HumanoidRig.LEFT_LEG,ArticulatedPlayerRig.LEFT_SHIN));
        correct(pose,sample,HumanoidRig.RIGHT_LEG,distance,.5f,contactWeight(authoredWeights,HumanoidRig.RIGHT_LEG,ArticulatedPlayerRig.RIGHT_SHIN));
    }
    private static float contactWeight(java.util.Map<String,Float> weights,String leg,String shin) {
        float authored=Math.max(weights.getOrDefault(HumanoidRig.ROOT,0f),
                Math.max(weights.getOrDefault(leg,0f),weights.getOrDefault(shin,0f)));
        return Float.isFinite(authored)?1-Math.clamp(authored,0f,1f):0;
    }
    private static void correct(RigPose pose, EntitySample sample, String bone, float distance, float shift,float strength) {
        if (strength<=0) return;
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
        var sole = solePosition(pose, bone);
        var correction = target.sub(sole);
        if (correction.length() > .3f) correction.normalize().mul(.3f);
        // Correction is measured in model space; the leg translation is parent-local.
        int parent = pose.rig().bone(pose.rig().requireBoneIndex(bone)).parentIndex();
        if (parent >= 0) {
            var inverse = pose.calculateModelTransforms()[parent].invert();
            inverse.transformDirection(correction);
        }
        if (!correction.isFinite()) return;
        var translation = current.translation().add(correction.mul(weight*strength));
        pose.setLocalTransform(pose.rig().requireBoneIndex(bone), new BoneTransform(translation, current.rotation(), current.scale()), RigPose.TRANSLATION);
    }
    /** Model-space foot endpoint, including thigh, knee and segment scales. */
    public static Vector3f solePosition(RigPose pose, String leg) {
        String shin = HumanoidRig.LEFT_LEG.equals(leg) ? ArticulatedPlayerRig.LEFT_SHIN : ArticulatedPlayerRig.RIGHT_SHIN;
        int index = pose.rig().findBoneIndex(shin);
        float length = .375f;
        if (index < 0) { index = pose.rig().requireBoneIndex(leg); length = .75f; }
        return pose.calculateModelTransforms()[index].transformPosition(new Vector3f(0,-length,0));
    }
}
