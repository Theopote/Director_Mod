package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Bounded contact correction using the complete leg chain, not terrain IK. */
public final class ActorRouteFootContact {
    private static final com.director.actor.rig.ActorRigProfile DEFAULT_PROFILE=new com.director.actor.rig.HumanoidRigProfile();
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
        apply(pose,sample,DEFAULT_PROFILE,authoredWeights);
    }
    public static void apply(RigPose pose,EntitySample sample,com.director.actor.rig.ActorRigProfile profile,
                             java.util.Map<String,Float> authoredWeights) {
        if (ActorRouteTurnPose.supports(pose,sample) && !(sample.poseOverrideEnabled && sample.poseOverrideLegs)
                && sample.scale.x > .001f && sample.scale.y > .001f && sample.scale.z > .001f) {
            float progress=ActorRouteMotion.stopTurn(sample.routeClip,sample.routeLocalTime).turnProgress();
            if (progress>0 && progress<1) {
                correctTurnSupport(pose,sample,authoredWeights,progress);
                return;
            }
        }
        float stride=ActorRouteGait.stride(sample,profile);
        if (!Float.isFinite(stride) || sample.routeClip == null
                || !sample.routeClip.isAlignRotationToRoute()
                || (sample.actionExplicit && sample.action!=com.director.actor.action.ActorActionType.WALK
                    && sample.action!=com.director.actor.action.ActorActionType.RUN) || sample.relationEnabled
                || (sample.poseOverrideEnabled && sample.poseOverrideLegs) || sample.speed <= .02f || !HumanoidTopology.matches(pose.rig())
                || sample.scale.x <= .001f || sample.scale.y <= .001f || sample.scale.z <= .001f) return;
        float distance = ActorRouteMotion.distanceTravelled(sample.routeClip, sample.routeLocalTime);
        correct(pose,sample,HumanoidRig.LEFT_LEG,distance,stride,0,contactWeight(authoredWeights,HumanoidRig.LEFT_LEG,ArticulatedPlayerRig.LEFT_SHIN));
        correct(pose,sample,HumanoidRig.RIGHT_LEG,distance,stride,.5f,contactWeight(authoredWeights,HumanoidRig.RIGHT_LEG,ArticulatedPlayerRig.RIGHT_SHIN));
    }
    /** Bounded support-foot stabilization, released smoothly at each foot exchange. */
    private static void correctTurnSupport(RigPose pose,EntitySample sample,
                                            java.util.Map<String,Float> weights,float progress) {
        boolean first=progress<.5f;
        String leg=first?HumanoidRig.RIGHT_LEG:HumanoidRig.LEFT_LEG;
        String shin=first?ArticulatedPlayerRig.RIGHT_SHIN:ArticulatedPlayerRig.LEFT_SHIN;
        float strength=contactWeight(weights,leg,shin);
        if (strength<=0) return;
        float phase=first?progress*2:(progress-.5f)*2;
        float envelope=(float)Math.pow(Math.sin(Math.PI*phase),2);
        // Reconstruct the half-turn entry from absolute scene time, never last-frame state.
        float relativeYaw=(float)(Math.PI*(progress-(first?0:.5f)));
        var restPose=new RigPose(pose.rig());
        var target=solePosition(restPose,leg).mul(sample.scale);
        new Quaternionf().rotateY(relativeYaw).transform(target);
        target.div(sample.scale);
        var correction=target.sub(solePosition(pose,leg));
        if (correction.length()>.2f) correction.normalize().mul(.2f);
        int index=pose.rig().requireBoneIndex(leg);
        int parent=pose.rig().bone(index).parentIndex();
        if(parent>=0) pose.calculateModelTransforms()[parent].invert().transformDirection(correction);
        if(!correction.isFinite()) return;
        var current=pose.localTransform(index);
        pose.setLocalTransform(index,new BoneTransform(current.translation().add(correction.mul(envelope*strength)),
                current.rotation(),current.scale()),RigPose.TRANSLATION);
    }

    private static float contactWeight(java.util.Map<String,Float> weights,String leg,String shin) {
        float authored=Math.max(weights.getOrDefault(HumanoidRig.ROOT,0f),
                Math.max(weights.getOrDefault(leg,0f),weights.getOrDefault(shin,0f)));
        return Float.isFinite(authored)?1-Math.clamp(authored,0f,1f):0;
    }
    private static void correct(RigPose pose, EntitySample sample, String bone, float distance,float stride,float shift,float strength) {
        if (strength<=0) return;
        float cycle = distance / stride + shift;
        float phase = cycle - (float)Math.floor(cycle);
        if (phase >= .5f) return;
        float weight = Math.min(1, phase / .08f) * Math.min(1, (.5f - phase) / .08f);
        float contactDistance = Math.max(0, ((float)Math.floor(cycle) - shift + .25f) * stride);
        float length=ActorRouteSampler.lengthOf(sample.routeClip);
        var traversal=ActorRouteMotion.traversalAt(sample.routeClip,distance);
        float anchor=contactDistance;
        if(sample.routeClip.getLoopMode()!=ActorRouteClip.LoopMode.ONCE) {
            float edge=Math.min(traversal.travelInLeg(),length-traversal.travelInLeg());
            float ramp=Math.min(stride*.25f,length*.25f);
            float release=Math.clamp(edge/Math.max(.0001f,ramp),0f,1f);
            weight*=release*release*(3-2*release);
            if(weight<=0) return;
            anchor=Math.clamp(contactDistance-(distance-traversal.travelInLeg()),0f,length);
            if(traversal.reversed()) anchor=length-anchor;
        }
        var routePoint = ActorRouteSampler.sampleAtDistance(sample.routeClip,anchor);
        var direction=new Vector3f(routePoint.direction());
        if(traversal.reversed()) direction.negate();
        float yaw = (float)Math.atan2(-direction.x,direction.z);
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
