package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Vector3f;

/** Bounded deterministic coordinate solve against the actual deformed player sole. */
final class ActorTurnLegIk {
    private ActorTurnLegIk() {}
    static void solve(RigPose pose,String leg,Vector3f target) {
        if(!target.isFinite() || !RigPoseComposer.sameLayout(pose.rig(),ArticulatedPlayerRig.DEFINITION)) return;
        String shin=HumanoidRig.LEFT_LEG.equals(leg)?ArticulatedPlayerRig.LEFT_SHIN:ArticulatedPlayerRig.RIGHT_SHIN;
        int hip=pose.rig().requireBoneIndex(leg),knee=pose.rig().requireBoneIndex(shin);
        var hipRest=pose.localTransform(hip);var kneeRest=pose.localTransform(knee);
        var encoded=LimbBendTransform.angles(kneeRest.rotation());
        var working=pose.copy();
        float[] angles=new float[4];
        float error=ActorRouteFootContact.solePosition(pose,leg).distanceSquared(target);
        boolean changed=false;
        // A straight leg is a singular start: seed a small paired hip/knee bend.
        for(float bend:new float[]{.3f,.6f,.9f}) {
            float[] seed={-.5f*bend,0,0,bend};
            write(working,hip,knee,hipRest,kneeRest,encoded,seed);
            float candidate=ActorRouteFootContact.solePosition(working,leg).distanceSquared(target);
            if(Float.isFinite(candidate) && candidate<error) {angles=seed;error=candidate;changed=true;}
        }
        write(working,hip,knee,hipRest,kneeRest,encoded,angles);
        // Fixed work and absolute input pose keep scrubbing independent of playback history.
        for(int iteration=0;iteration<18 && error>1e-8f;iteration++) {
            float step=.12f*(float)Math.pow(.84,iteration/3);
            for(int axis=0;axis<4;axis++) {
                float original=angles[axis],best=original,bestError=error;
                for(int sign:new int[]{-1,1}) {
                    angles[axis]=Math.clamp(original+sign*step,axis==3?0f:-.5f,axis==3?.9f:.5f);
                    write(working,hip,knee,hipRest,kneeRest,encoded,angles);
                    float candidate=ActorRouteFootContact.solePosition(working,leg).distanceSquared(target);
                    if(Float.isFinite(candidate) && candidate<bestError) {best=angles[axis];bestError=candidate;}
                }
                angles[axis]=best;error=bestError;changed|=best!=original;
                write(working,hip,knee,hipRest,kneeRest,encoded,angles);
            }
        }
        // Keep the input channel ownership intact when no useful correction exists.
        if(!changed) return;
        pose.setLocalTransform(hip,working.localTransform(hip),RigPose.ROTATION);
        pose.setLocalTransform(knee,working.localTransform(knee),RigPose.ROTATION);
    }
    private static void write(RigPose pose,int hip,int knee,BoneTransform upper,BoneTransform lower,
                              org.joml.Vector2f encoded,float[] a) {
        pose.setLocalTransform(hip,upper.withRotation(upper.rotation().rotateXYZ(a[0],a[1],a[2])),RigPose.ROTATION);
        pose.setLocalTransform(knee,lower.withRotation(LimbBendTransform.encodedRotation(encoded.x+a[3],encoded.y)),RigPose.ROTATION);
    }
}
