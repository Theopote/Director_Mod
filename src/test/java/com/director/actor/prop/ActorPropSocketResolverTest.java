package com.director.actor.prop;
import com.director.actor.rig.skeleton.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorPropSocketResolverTest {
    RigPose pose=new RigPose(HumanoidRig.DEFINITION);
    Matrix4f socket(ActorPropSlot slot) { return ActorPropSocketResolver.resolve(pose,new Matrix4f(),slot,new Vector3f(),new Vector3f()).orElseThrow(); }
    void near(Vector3f expected,Vector3f actual) { assertTrue(expected.distance(actual)<.0001f,expected+" != "+actual); }
    @Test void handSocketsAreAtArmEndsAndDistinct() {
        near(new Vector3f(-.375f,.75f,0),socket(ActorPropSlot.MAIN_HAND).getTranslation(new Vector3f()));
        near(new Vector3f(.375f,.75f,0),socket(ActorPropSlot.OFF_HAND).getTranslation(new Vector3f()));
    }
    @Test void armRotationMovesHandAndOffsetInBoneLocalSpace() {
        var arm=pose.localTransform(HumanoidRig.RIGHT_ARM);
        pose.setLocalTransform(HumanoidRig.RIGHT_ARM,arm.withRotation(new Quaternionf().rotateZ((float)Math.PI/2)));
        var matrix=ActorPropSocketResolver.resolve(pose,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(0,-.1f,0),new Vector3f()).orElseThrow();
        near(new Vector3f(.35f,1.375f,0),matrix.getTranslation(new Vector3f()));
    }
    @Test void actorYawScaleAndTranslationComposeWithSocket() {
        var world=new Matrix4f().translate(10,2,3).rotateY((float)Math.PI/2).scale(2);
        var matrix=ActorPropSocketResolver.resolve(pose,world,ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        near(new Vector3f(10,3.5f,3.75f),matrix.getTranslation(new Vector3f()));
    }
    @Test void bothHandsUseMidpointAndHeadBackUseDifferentBones() {
        near(new Vector3f(0,.75f,0),socket(ActorPropSlot.BOTH_HANDS).getTranslation(new Vector3f()));
        near(new Vector3f(0,1.875f,0),socket(ActorPropSlot.HEAD).getTranslation(new Vector3f()));
        near(new Vector3f(0,1.25f,.2f),socket(ActorPropSlot.BACK).getTranslation(new Vector3f()));
    }
    @Test void localRotationIsAppliedAfterPositionAndGettersAreDefensive() {
        var prop=new RuntimeProp(PropRegistry.getInstance().get("stone").orElseThrow(),ActorPropSlot.MAIN_HAND);
        prop.setLocalRotation(0,0,90); prop.updateSocketTransform(pose,new Matrix4f());
        var matrix=prop.getWorldTransform(); near(new Vector3f(0,1,0),matrix.transformDirection(new Vector3f(1,0,0)));
        matrix.identity(); near(new Vector3f(-.375f,.75f,0),prop.getWorldTransform().getTranslation(new Vector3f()));
        prop.setWorldTransform(new net.minecraft.util.math.Vec3d(5,6,7)); prop.updateSocketTransform(pose,new Matrix4f());
        near(new Vector3f(5,6,7),prop.getWorldTransform().getTranslation(new Vector3f()));
    }
    @Test void missingTopologyAndInvalidOffsetsDoNotFallBackToActorOrigin() {
        assertTrue(ActorPropSocketResolver.resolve(new RigPose(BuiltInCustomRigs.SIMPLE_ROBOT),new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).isEmpty());
        assertTrue(ActorPropSocketResolver.resolve(pose,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(Float.NaN,0,0),new Vector3f()).isEmpty());
    }
    @Test void compatibleCustomProportionsArePreserved() {
        var custom=new RigPose(BuiltInCustomRigs.CHIBI_HUMANOID);
        var matrix=ActorPropSocketResolver.resolve(custom,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        near(new Vector3f(-.4f,.375f,0),matrix.getTranslation(new Vector3f()));
    }
}
