package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Bounded route contact: articulated leg rotation solve, rigid-rig translation fallback; no terrain projection. */
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
        if (pose == null || pose.rig().legs().isEmpty()) return;
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
                || (sample.poseOverrideEnabled && sample.poseOverrideLegs) || sample.speed <= .02f
                || sample.scale.x <= .001f || sample.scale.y <= .001f || sample.scale.z <= .001f) return;
        float distance = ActorRouteMotion.distanceTravelled(sample.routeClip, sample.routeLocalTime);
        var legs = pose.rig().legs();
        for (int i = 0; i < legs.size(); i++) {
            LegChain chain = legs.get(i);
            float shift = contactPhaseShift(legs, i);
            correct(pose, sample, chain, distance, stride, shift,
                    contactWeight(authoredWeights, chain));
        }
    }
    /** Match LegacyMobPoseAdapter diagonal gait: LF+RH / RF+LH share a phase. */
    static float contactPhaseShift(java.util.List<LegChain> legs, int index) {
        if (legs == null || legs.isEmpty()) return 0f;
        String id = legs.get(index).legBone();
        if (QuadrupedRig.LEFT_FRONT_LEG.equals(id) || QuadrupedRig.RIGHT_HIND_LEG.equals(id)) return 0f;
        if (QuadrupedRig.RIGHT_FRONT_LEG.equals(id) || QuadrupedRig.LEFT_HIND_LEG.equals(id)) return .5f;
        return legs.size() <= 1 ? 0f : (float) index / legs.size();
    }
    /** Bounded support-foot stabilization, released smoothly at each foot exchange. */
    private static void correctTurnSupport(RigPose pose,EntitySample sample,
                                            java.util.Map<String,Float> weights,float progress) {
        var legs = pose.rig().legs();
        if (legs.isEmpty()) return;
        boolean first=progress<.5f;
        LegChain chain = legs.size() >= 2
                ? (first ? legs.get(1) : legs.get(0))
                : legs.getFirst();
        float strength=contactWeight(weights,chain);
        if (strength<=0) return;
        float phase=first?progress*2:(progress-.5f)*2;
        float envelope=(float)Math.pow(Math.sin(Math.PI*phase),2);
        float relativeYaw=(float)(Math.PI*(progress-(first?0:.5f)));
        var restPose=new RigPose(pose.rig());
        var target=solePosition(restPose,chain).mul(sample.scale);
        new Quaternionf().rotateY(relativeYaw).transform(target);
        target.div(sample.scale);
        var correction=target.sub(solePosition(pose,chain));
        if (correction.length()>.2f) correction.normalize().mul(.2f);
        if(!correction.isFinite()) return;
        var desired=solePosition(pose,chain).add(correction.mul(envelope*strength));
        ActorLegContactIk.solve(pose,chain,desired,1f);
    }

    private static float contactWeight(java.util.Map<String,Float> weights, LegChain chain) {
        float authored = weights.getOrDefault(chain.legBone(), 0f);
        if (chain.lowerBone() != null) authored = Math.max(authored, weights.getOrDefault(chain.lowerBone(), 0f));
        if (chain.footBone() != null) authored = Math.max(authored, weights.getOrDefault(chain.footBone(), 0f));
        return Float.isFinite(authored) ? 1 - Math.clamp(authored, 0f, 1f) : 0;
    }

    private static void correct(RigPose pose, EntitySample sample, LegChain chain, float distance,float stride,float shift,float strength) {
        if (strength<=0) return;
        String bone = chain.legBone();
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
        var rest = chain.footBone() == null
                ? pose.rig().bone(pose.rig().requireBoneIndex(bone)).restLocal().translation()
                : solePosition(new RigPose(pose.rig()), chain);
        var side = new Quaternionf().rotateY(-yaw).transform(new Vector3f(
                rest.x * sample.scale.x, 0, rest.z * sample.scale.z));
        var target = new Vector3f(routePoint.position()).add(side).sub(sample.position);
        new Quaternionf().rotateY((float)Math.toRadians(sample.rotation.x)).transform(target);
        target.div(sample.scale);
        var sole = solePosition(pose, chain);
        var correction = target.sub(sole);
        if (correction.length() > .3f) correction.normalize().mul(.3f);
        ActorLegContactIk.solve(pose, chain, sole.add(correction), weight * strength);
    }

    /** Model-space foot endpoint, including thigh, knee and segment scales. */
    public static Vector3f solePosition(RigPose pose, String leg) {
        LegChain chain = pose.rig().findLeg(leg);
        if (chain == null) chain = LegChain.thigh(leg);
        return solePosition(pose, chain);
    }

    public static Vector3f solePosition(RigPose pose, LegChain chain) {
        if (chain.footBone() != null) {
            int foot = pose.rig().requireBoneIndex(chain.footBone());
            return pose.calculateModelTransform(foot).getTranslation(new Vector3f());
        }
        if (chain.hasKnee()) {
            int shin = pose.rig().findBoneIndex(chain.lowerBone());
            if (shin >= 0) {
                return pose.calculateModelTransform(shin).transformPosition(new Vector3f(0, -segmentLength(pose, shin), 0));
            }
        }
        int index = pose.rig().requireBoneIndex(chain.legBone());
        return pose.calculateModelTransform(index).transformPosition(new Vector3f(0, -segmentLength(pose, index), 0));
    }

    /** Prefer rest height of the limb bone; fall back for degenerate custom rigs. */
    static float segmentLength(RigPose pose, int boneIndex) {
        float restY = Math.abs(pose.rig().bone(boneIndex).restLocal().translation().y);
        if (restY > .01f) return restY;
        return pose.rig().bone(boneIndex).id().contains("shin")
                || pose.rig().bone(boneIndex).id().contains("forearm") ? .375f : .55f;
    }
}
