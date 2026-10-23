package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure head constraint; uses the rendered head basis, including rotated/scaled ancestors. */
public final class ActorGazeConstraint {
    private ActorGazeConstraint() {}
    public static boolean apply(RigPose pose,RigWorldPose world,Vector3f target,boolean playerModelAxes,float strength) {
        if(pose==null || world==null || target==null || !target.isFinite() || !Float.isFinite(strength) || strength<=0) return false;
        int head=pose.rig().findBoneIndex(HumanoidRig.HEAD);
        if(head<0) return false;
        var current=pose.localTransform(head);
        // Remove head rotation/scale but retain the exact rendered pivot and ancestor basis.
        var local=new Matrix4f().rotation(current.rotation()).scale(current.scale());
        if(Math.abs(local.determinant())<1e-10f) return false;
        var basis=world.transform(head).mul(local.invert());
        if(!basis.isFinite() || Math.abs(basis.determinant())<1e-10f) return false;
        var eye=basis.transformPosition(new Vector3f(0,(playerModelAxes?-.25f:.125f)*current.scale().y,0));
        var direction=new Vector3f(target).sub(eye);
        if(direction.lengthSquared()<1e-4f) return false;
        basis.invert().transformDirection(direction);
        if(!direction.isFinite() || direction.lengthSquared()<1e-10f) return false;
        direction.normalize();
        float yaw=(float)Math.atan2(playerModelAxes?-direction.x:direction.x,playerModelAxes?-direction.z:direction.z);
        float pitch=(float)Math.asin(Math.clamp(playerModelAxes?direction.y:-direction.y,-1f,1f));
        var desired=new Quaternionf().rotationYXZ(yaw,pitch,0);
        var rotation=current.rotation().slerp(desired,Math.clamp(strength,0,1));
        pose.setLocalTransform(head,current.withRotation(rotation),RigPose.ROTATION);
        return true;
    }
}
