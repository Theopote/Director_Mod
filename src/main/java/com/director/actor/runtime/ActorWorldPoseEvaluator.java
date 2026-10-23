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
    /** Explicit target input; never reads preview entities, UI drafts or another timeline time. */
    public static Result evaluate(RigPose input,Vector3f position,float yaw,Vector3f scale,
                                  boolean player,Vector3f target,float strength) {
        var pose=input.copy();var world=resolve(pose,position,yaw,scale,player);
        if(target!=null && ActorGazeConstraint.apply(pose,world,target,player,strength))
            world=resolve(pose,position,yaw,scale,player);
        return new Result(pose,world);
    }
}
