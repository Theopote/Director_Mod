package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared render-space mapping. Placement already includes consumed root translation. */
public final class ActorWorldPoseEvaluator {
    private ActorWorldPoseEvaluator() {}
    public record Result(RigPose pose,RigWorldPose world) {}
    public static RigWorldPose resolve(RigPose pose,Vector3f position,float yaw,Vector3f scale,boolean player) {
        return player?PlayerRenderWorldPose.resolve(pose,position,yaw,scale)
                :RigWorldPose.resolve(pose,new Matrix4f().translate(position).rotateY((float)Math.toRadians(-yaw)).scale(scale));
    }
    /** Eye anchor before gaze; independent of target gaze, so mutual look-at never recurses. */
    public static Vector3f eyePosition(RigPose pose,RigWorldPose world,boolean player) {
        int index=pose.rig().findBoneIndex(HumanoidRig.HEAD);
        if(index<0) return null;
        var head=pose.localTransform(index);
        var local=new Matrix4f().rotation(head.rotation()).scale(head.scale());
        if(Math.abs(local.determinant())<1e-10f) return null;
        var basis=world.transform(index).mul(local.invert());
        if(!basis.isFinite()) return null;
        return basis.transformPosition(new Vector3f(0,(player?-.25f:.125f)*head.scale().y,0));
    }

    /** Explicit target input; never reads preview entities, UI drafts or another timeline time. */
    public static Result evaluate(RigPose input,Vector3f position,float yaw,Vector3f scale,
                                  boolean player,Vector3f target,float strength) {
        var pose=input.copy();var world=resolve(pose,position,yaw,scale,player);
        if(target!=null && ActorGazeConstraint.apply(pose,world,target,player,strength))
            world=resolve(pose,position,yaw,scale,player);
        return new Result(pose,world);
    }
}
