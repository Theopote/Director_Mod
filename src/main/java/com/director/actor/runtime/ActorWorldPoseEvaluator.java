package com.director.actor.runtime;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import com.director.actor.preview.PreviewPlayerActorEntity;
import com.director.actor.rig.skeleton.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared render-space mapping. Placement already includes consumed root translation. */
public final class ActorWorldPoseEvaluator {
    private ActorWorldPoseEvaluator() {}
    public record Result(RigPose pose,RigWorldPose world,boolean gazeApplied) {}

    /**
     * LivingEntityRenderer applies rotateY(180)+scale(-1,-1,1). Gaze yaw/pitch written to
     * ModelPart must use the same axis convention as the player model path.
     * Pure custom cuboid hosts keep world axes.
     */
    public static boolean usesLivingGazeAxes(Actor actor, Entity entity) {
        if (actor != null && actor.getCustomModelId() != null && !actor.getCustomModelId().isBlank()) return false;
        return entity instanceof PreviewPlayerActorEntity || entity instanceof LivingEntity;
    }

    public static boolean usesLivingGazeAxes(ActorType type) {
        if (type == null || type == ActorType.CUSTOM) return false;
        return true;
    }

    public static RigWorldPose resolve(RigPose pose,Vector3f position,float yaw,Vector3f scale,boolean player) {
        if (player) return PlayerRenderWorldPose.resolve(pose,position,yaw,scale);
        var basis = new Matrix4f().translate(position).rotateY((float)Math.toRadians(-yaw));
        // Villager/quadruped rest bones face -Z; align world overlay/sockets with LivingEntity forward.
        if (livingForwardRig(pose.rig())) basis.rotateY((float)Math.PI);
        return RigWorldPose.resolve(pose, basis.scale(scale));
    }

    private static boolean livingForwardRig(RigDefinition rig) {
        if (rig == null) return false;
        String id = rig.id();
        return VillagerRig.DEFINITION.id().equals(id) || QuadrupedRig.DEFINITION.id().equals(id);
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
        return evaluate(input, position, yaw, scale, player, player, target, strength);
    }

    /**
     * @param playerRender use {@link PlayerRenderWorldPose} (player mesh pivots)
     * @param gazeLivingAxes ModelPart yaw/pitch signs matching LivingEntityRenderer
     */
    public static Result evaluate(RigPose input,Vector3f position,float yaw,Vector3f scale,
                                  boolean playerRender,boolean gazeLivingAxes,Vector3f target,float strength) {
        var pose=input.copy();var world=resolve(pose,position,yaw,scale,playerRender);
        boolean applied=target!=null && ActorGazeConstraint.apply(pose,world,target,gazeLivingAxes,strength);
        if(applied)
            world=resolve(pose,position,yaw,scale,playerRender);
        return new Result(pose,world,applied);
    }
}
