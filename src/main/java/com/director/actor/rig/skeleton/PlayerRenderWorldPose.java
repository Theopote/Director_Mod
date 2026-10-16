package com.director.actor.rig.skeleton;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Mirrors PlayerEntityModel pivots and LivingEntityRenderer's standing-player transform. */
public final class PlayerRenderWorldPose {
    private PlayerRenderWorldPose() {}
    public static RigWorldPose resolve(RigPose pose,Vector3f position,float yaw,Vector3f scale) {
        var world=new Matrix4f().translate(position).rotateY((float)Math.toRadians(-yaw));
        var model=new Matrix4f(world).rotateY((float)Math.PI).scale(-1,-1,1)
                .scale(.9375f).scale(scale).translate(0,-1.501f,0);
        var rig=pose.rig();
        Matrix4f[] result=modelTransforms(pose);
        for(int i=0;i<result.length;i++) {
            if(HumanoidRig.ROOT.equals(rig.bone(i).id())) result[i]=new Matrix4f(world).scale(scale);
            else result[i]=new Matrix4f(model).mul(result[i]);
        }
        return RigWorldPose.fromTransforms(rig,result);
    }
    /** Player model-space matrices, in blocks with model Y pointing down. */
    public static Matrix4f[] modelTransforms(RigPose pose) {
        var rig=pose.rig(); Matrix4f[] result=new Matrix4f[rig.boneCount()];
        for(int i=0;i<result.length;i++) {
            var bone=rig.bone(i); var value=pose.localTransform(i); String id=bone.id();
            var delta=value.translation().sub(bone.restLocal().translation()); delta.y=-delta.y;
            Vector3f origin=switch(id) {
                case HumanoidRig.BODY -> new Vector3f(0,.75f,0);
                case HumanoidRig.HEAD -> new Vector3f(0,-.75f,0);
                case HumanoidRig.LEFT_ARM -> new Vector3f(5f/16,-10f/16,0);
                case HumanoidRig.RIGHT_ARM -> new Vector3f(-5f/16,-10f/16,0);
                case HumanoidRig.LEFT_LEG -> new Vector3f(1.9f/16,12f/16,0);
                case HumanoidRig.RIGHT_LEG -> new Vector3f(-1.9f/16,12f/16,0);
                case ArticulatedPlayerRig.LEFT_FOREARM,ArticulatedPlayerRig.RIGHT_FOREARM -> new Vector3f(0,.25f,0);
                case ArticulatedPlayerRig.LEFT_SHIN,ArticulatedPlayerRig.RIGHT_SHIN -> new Vector3f(0,.375f,0);
                default -> new Vector3f();
            };
            boolean root=HumanoidRig.ROOT.equals(id);
            Matrix4f parent=bone.parentIndex()>0?result[bone.parentIndex()]:new Matrix4f();
            result[i]=root?new Matrix4f():new Matrix4f(parent).translate(origin.add(delta));
            if(!root) {
                if(LimbBendTransform.isJoint(id)) {
                    var bend=new LimbBendTransform(value.rotation(),value.scale());
                    result[i].translate(bend.lowerOffset().div(16)).rotate(bend.rotation()).scale(value.scale());
                } else result[i].rotate(value.rotation()).scale(value.scale());
            }
        }
        return result;
    }
}
