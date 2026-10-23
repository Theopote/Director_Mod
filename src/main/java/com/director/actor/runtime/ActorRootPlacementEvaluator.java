package com.director.actor.runtime;

import com.director.actor.path.*;
import com.director.actor.rig.skeleton.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Objects;

/** Consumes local root translation once, after the timeline/relation base position is resolved. */
public final class ActorRootPlacementEvaluator {
    private ActorRootPlacementEvaluator() {}
    public enum Mode { EXACT_ROUTE, GROUND_CONSTRAINED }
    public record Result(RigPose pose,Vec3d position,String warning) {}
    public static boolean needsTerrain(RigPose pose,Mode mode) {
        int root=pose.rig().findBoneIndex(HumanoidRig.ROOT);
        return mode==Mode.GROUND_CONSTRAINED && root>=0 && pose.localTransform(root).translation().lengthSquared()>1e-10f;
    }
    public static Result evaluate(RigPose input,Vec3d base,float yaw,Vector3f scale,Mode mode,GroundRoute.Terrain terrain) {
        Objects.requireNonNull(input);Objects.requireNonNull(mode);
        var pose=input.copy();
        if(base==null || !Double.isFinite(base.x) || !Double.isFinite(base.y) || !Double.isFinite(base.z)
                || !Float.isFinite(yaw) || scale==null || !scale.isFinite() || scale.x<=0 || scale.y<=0 || scale.z<=0)
            return new Result(pose,null,"最终演员位置或缩放无效");
        int root=pose.rig().findBoneIndex(HumanoidRig.ROOT);
        if(root<0) return new Result(pose,base,"");
        var local=pose.localTransform(root);var offset=local.translation();
        Vec3d position=base;String warning="";
        if(offset.lengthSquared()>1e-10f) {
            var worldOffset=new Matrix4f().rotateY((float)Math.toRadians(-yaw)).scale(scale).transformDirection(offset);
            if(!worldOffset.isFinite()) return new Result(pose,null,"最终根位移无效");
            if(mode==Mode.EXACT_ROUTE) position=base.add(worldOffset.x,worldOffset.y,worldOffset.z);
            else {
                var result=ActorFinalGroundPlacement.project(base,new Vec3d(worldOffset.x,worldOffset.y,worldOffset.z),0,terrain);
                if(result.position()==null) return new Result(pose,null,result.warning());
                position=result.position();warning=result.warning();
            }
        }
        if(!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
            return new Result(pose,null,"最终演员位置无效");
        pose.setLocalTransform(root,new BoneTransform(new Vector3f(),local.rotation(),local.scale()),RigPose.TRANSLATION);
        return new Result(pose,position,warning);
    }
}
