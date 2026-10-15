package com.director.actor.rig.skeleton;
import com.director.actor.prop.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArticulatedPlayerRigTest {
    @Test void jointsAreChildrenAndMoveHandSocket() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        assertTrue(HumanoidTopology.matches(rig));
        assertSame(rig,RigDefinitions.find(rig.id()).orElseThrow());
        var pose=new RigPose(rig);
        var initial=ActorPropSocketResolver.resolve(pose,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow().getTranslation(new Vector3f());
        int joint=rig.requireBoneIndex(ArticulatedPlayerRig.RIGHT_FOREARM);
        assertEquals(rig.requireBoneIndex(HumanoidRig.RIGHT_ARM),rig.bone(joint).parentIndex());
        pose.setLocalTransform(joint,pose.localTransform(joint).withRotation(new Quaternionf().rotateX(1)),RigPose.ROTATION);
        var bent=ActorPropSocketResolver.resolve(pose,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow().getTranslation(new Vector3f());
        assertTrue(initial.distance(bent)>.1f);
    }
    @Test void straightHandsUseCorrectedShoulderWidth() {
        var rigid=new RigPose(HumanoidRig.DEFINITION);
        var bent=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var a=ActorPropSocketResolver.resolve(rigid,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        var b=ActorPropSocketResolver.resolve(bent,new Matrix4f(),ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        var hand=b.getTranslation(new Vector3f());
        assertEquals(-.3125f,hand.x,1e-5f);
        assertEquals(.75f,hand.y,1e-5f);
    }
    @Test void keyframeAssetSamplesElbowChannelWithoutTakingOverLegs() {
        var asset=new com.director.actor.animation.asset.AnimationAsset("test:joint","joint",ArticulatedPlayerRig.DEFINITION,2,
                com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        var channel=asset.getOrCreateBoneTrack(ArticulatedPlayerRig.LEFT_FOREARM).rotation();
        channel.put(0,new Quaternionf(),com.director.actor.animation.asset.InterpolationMode.LINEAR);
        channel.put(2,new Quaternionf().rotateX(1),com.director.actor.animation.asset.InterpolationMode.LINEAR);
        var pose=asset.sample(1,com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        assertEquals(RigPose.ROTATION,pose.authoredChannels(pose.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_FOREARM)));
        assertFalse(pose.isAuthored(pose.rig().requireBoneIndex(HumanoidRig.LEFT_LEG)));
        assertTrue(pose.localTransform(ArticulatedPlayerRig.LEFT_FOREARM).rotation().equals(new Quaternionf().rotateX(.5f),1e-5f));
    }
}
