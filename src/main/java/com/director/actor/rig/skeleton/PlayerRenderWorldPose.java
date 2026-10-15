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
        var rig=pose.rig(); Matrix4f[] result=new Matrix4f[rig.boneCount()];
        for(int i=0;i<result.length;i++) {
            var bone=rig.bone(i); var value=pose.localTransform(i);
            String id=bone.id();
            if(HumanoidRig.ROOT.equals(id)) { result[i]=new Matrix4f(world).scale(scale); continue; }
            boolean joint=id.equals(ArticulatedPlayerRig.LEFT_FOREARM)||id.equals(ArticulatedPlayerRig.RIGHT_FOREARM)
                    ||id.equals(ArticulatedPlayerRig.LEFT_SHIN)||id.equals(ArticulatedPlayerRig.RIGHT_SHIN);
            var delta=value.translation().sub(bone.restLocal().translation());
            delta.y=-delta.y;
            Vector3f origin;
            if(joint) origin=new Vector3f(0,id.equals(ArticulatedPlayerRig.LEFT_FOREARM)||id.equals(ArticulatedPlayerRig.RIGHT_FOREARM)?.25f:.375f,0);
            else origin=switch(id) {
                case HumanoidRig.LEFT_ARM -> new Vector3f(5f/16,2f/16,0);
                case HumanoidRig.RIGHT_ARM -> new Vector3f(-5f/16,2f/16,0);
                case HumanoidRig.LEFT_LEG -> new Vector3f(1.9f/16,12f/16,0);
                case HumanoidRig.RIGHT_LEG -> new Vector3f(-1.9f/16,12f/16,0);
                default -> new Vector3f();
            };
            var parent=joint?result[bone.parentIndex()]:model;
            result[i]=new Matrix4f(parent).translate(origin.add(delta)).rotate(value.rotation()).scale(value.scale());
        }
        return RigWorldPose.fromTransforms(rig,result);
    }
}
