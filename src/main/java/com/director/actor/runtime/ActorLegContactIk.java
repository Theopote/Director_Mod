package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Vector3f;

/** Bounded deterministic coordinate solve against the actual deformed player sole. */
final class ActorLegContactIk {
    private ActorLegContactIk() {}
    /** Blend solved rotations, rather than a quantized target solve, for continuous release. */
    static void solve(RigPose pose,String leg,Vector3f target,float weight) {
        if(!Float.isFinite(weight) || weight<=0) return;
        weight=Math.clamp(weight,0,1);
        var solved=solvePose(pose,leg,target);
        if(solved==null) return;
        String shin=HumanoidRig.LEFT_LEG.equals(leg)?ArticulatedPlayerRig.LEFT_SHIN:ArticulatedPlayerRig.RIGHT_SHIN;
        for(String bone:new String[]{leg,shin}) {
            int i=pose.rig().findBoneIndex(bone);
            if(i<0) continue;
            var before=pose.localTransform(i);var after=solved.localTransform(i);
            if(before.rotation().equals(after.rotation(),1e-7f)) continue;
            var rotation=before.rotation().slerp(after.rotation(),weight);
            pose.setLocalTransform(i,before.withRotation(rotation),RigPose.ROTATION);
        }
    }

    static void solve(RigPose pose,String leg,Vector3f target) { solve(pose,leg,target,1); }
    private static RigPose solvePose(RigPose pose,String leg,Vector3f target) {
        if(!target.isFinite() || !RigPoseComposer.sameLayout(pose.rig(),ArticulatedPlayerRig.DEFINITION)) return null;
        String shin=HumanoidRig.LEFT_LEG.equals(leg)?ArticulatedPlayerRig.LEFT_SHIN:ArticulatedPlayerRig.RIGHT_SHIN;
        int hip=pose.rig().requireBoneIndex(leg),knee=pose.rig().requireBoneIndex(shin);
        var hipRest=pose.localTransform(hip);var kneeRest=pose.localTransform(knee);
        var encoded=LimbBendTransform.angles(kneeRest.rotation());
        float bendSign=encoded.x<0?-1f:1f;
        float minimum=bendSign>0?Math.max(-.9f,-encoded.x):-.9f;
        float maximum=bendSign>0?.9f:Math.min(.9f,-encoded.x);
        var working=pose.copy();
        var cache=working.calculateModelTransforms();
        float[] angles=new float[4];
        float error=ActorRouteFootContact.solePosition(pose,leg).distanceSquared(target);
        boolean changed=false;
        // A straight leg is a singular start: seed a small paired hip/knee bend.
        for(float bend:new float[]{.3f,.6f,.9f}) {
            float[] seed={-.5f*bend*bendSign,0,0,bend*bendSign};
            write(working,hip,knee,hipRest,kneeRest,encoded,seed);
            float candidate=sole(working,hip,knee,cache).distanceSquared(target);
            if(Float.isFinite(candidate) && candidate<error) {angles=seed;error=candidate;changed=true;}
        }
        write(working,hip,knee,hipRest,kneeRest,encoded,angles);
        // Fixed work and absolute input pose keep scrubbing independent of playback history.
        for(int iteration=0;iteration<18 && error>1e-8f;iteration++) {
            float step=.12f*(float)Math.pow(.84,iteration/3);
            for(int axis=0;axis<4;axis++) {
                float original=angles[axis],best=original,bestError=error;
                for(int sign:new int[]{-1,1}) {
                    angles[axis]=Math.clamp(original+sign*step,axis==3?minimum:-.5f,axis==3?maximum:.5f);
                    write(working,hip,knee,hipRest,kneeRest,encoded,angles);
                    float candidate=sole(working,hip,knee,cache).distanceSquared(target);
                    if(Float.isFinite(candidate) && candidate<bestError) {best=angles[axis];bestError=candidate;}
                }
                angles[axis]=best;error=bestError;changed|=best!=original;
                write(working,hip,knee,hipRest,kneeRest,encoded,angles);
            }
        }
        // Keep the input channel ownership intact when no useful correction exists.
        return changed?working:null;
    }
    private static Vector3f sole(RigPose pose,int hip,int knee,org.joml.Matrix4f[] cache) {
        // The only queried descendant is this knee; all ancestors above the hip are fixed.
        cache[hip]=null;cache[knee]=null;
        return pose.calculateModelTransform(knee,cache).transformPosition(new Vector3f(0,-.375f,0));
    }
    private static void write(RigPose pose,int hip,int knee,BoneTransform upper,BoneTransform lower,
                              org.joml.Vector2f encoded,float[] a) {
        pose.setLocalTransform(hip,upper.withRotation(upper.rotation().rotateXYZ(a[0],a[1],a[2])),RigPose.ROTATION);
        pose.setLocalTransform(knee,lower.withRotation(LimbBendTransform.encodedRotation(encoded.x+a[3],encoded.y)),RigPose.ROTATION);
    }
}
