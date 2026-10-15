package com.director.actor.rig.skeleton;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerRenderWorldPoseTest {
    @Test void actualShoulderAndKneePivotsRespectActorScaleAndTurn() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var scale=new Vector3f(2,3,4);
        var position=new Vector3f(10,20,30);
        var world=PlayerRenderWorldPose.resolve(pose,position,90,scale);
        var expected=new Matrix4f().translate(position).rotateY(-(float)java.lang.Math.PI/2)
                .rotateY((float)java.lang.Math.PI).scale(-1,-1,1).scale(.9375f).scale(scale).translate(0,-1.501f,0)
                .transformPosition(new Vector3f(5f/16,2f/16,0));
        assertTrue(world.position(HumanoidRig.LEFT_ARM).equals(expected,1e-5f));
        var knee=world.position(ArticulatedPlayerRig.LEFT_SHIN);
        var thigh=world.position(HumanoidRig.LEFT_LEG);
        assertEquals(.375f*.9375f*3,thigh.y-knee.y,1e-5f);
    }
    @Test void forearmPivotFollowsRenderedUpperArmRotation() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        pose.setLocalTransform(HumanoidRig.LEFT_ARM,pose.localTransform(HumanoidRig.LEFT_ARM).withRotation(new Quaternionf().rotateX(1)));
        var world=PlayerRenderWorldPose.resolve(pose,new Vector3f(),0,new Vector3f(1));
        var expected=world.transform(pose.rig().requireBoneIndex(HumanoidRig.LEFT_ARM)).transformPosition(new Vector3f(0,.25f,0));
        assertTrue(world.position(ArticulatedPlayerRig.LEFT_FOREARM).equals(expected,1e-5f));
    }
    @Test void waistRotationMovesHeadAndArmsWhileLegsStayIndependent() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var before=PlayerRenderWorldPose.modelTransforms(pose);
        int body=pose.rig().requireBoneIndex(HumanoidRig.BODY),head=pose.rig().requireBoneIndex(HumanoidRig.HEAD);
        int arm=pose.rig().requireBoneIndex(HumanoidRig.LEFT_ARM),leg=pose.rig().requireBoneIndex(HumanoidRig.LEFT_LEG);
        var rotation=new Quaternionf().rotateZ(.5f).rotateY(.4f);
        pose.setLocalTransform(body,pose.localTransform(body).withRotation(rotation),RigPose.ROTATION);
        pose.setLocalTransform(head,pose.localTransform(head).withRotation(new Quaternionf().rotateX(.3f)),RigPose.ROTATION);
        var after=PlayerRenderWorldPose.modelTransforms(pose);
        assertEquals(new Vector3f(0,.75f,0),after[body].getTranslation(new Vector3f()));
        assertTrue(after[leg].equals(before[leg],1e-5f));
        assertFalse(after[arm].getTranslation(new Vector3f()).equals(before[arm].getTranslation(new Vector3f()),1e-5f));
        assertTrue(after[head].getUnnormalizedRotation(new Quaternionf()).equals(new Quaternionf(rotation).rotateX(.3f),1e-5f));
    }
}
