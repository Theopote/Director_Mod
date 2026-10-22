package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBoneAnimationDraftTest {
    @Test void missingScaleSeedDoesNotJumpAtLowWeightAndExistingChannelIsNotMixedTwice() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        var asset=new com.director.actor.animation.asset.AnimationAsset("test:seed-low","Seed",rig,5,
                com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        var lower=new RigPose(rig);var rest=lower.localTransform("head");
        lower.setLocalTransform("head",new BoneTransform(rest.translation(),new Quaternionf().rotateY(.8f),new Vector3f(2)));
        for(float weight:new float[]{0,.25f,.7f,1}) {
            var settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,weight,7,true,Set.of());
            var seeded=ActorEmbeddedAnimation.seedNewChannels(asset,1,settings,lower);
            var draft=new ActorBoneAnimationDraft(asset.sample(1),"head",seeded.localTransform("head"),RigPose.SCALE,settings,1,5);
            var mixed=RigPoseComposer.compose(lower,List.of(settings.layer(draft.authoredPose()))).pose();
            assertTrue(lower.localTransform("head").toMatrix().equals(mixed.localTransform("head").toMatrix(),1e-6f));
        }
        asset.getOrCreateBoneTrack("head").rotation().put(1,new Quaternionf().rotateY(.2f),
                com.director.actor.animation.asset.InterpolationMode.LINEAR);
        var seeded=ActorEmbeddedAnimation.seedNewChannels(asset,1,PoseBlendSettings.DEFAULT,lower);
        assertTrue(seeded.localTransform("head").rotation().equals(new Quaternionf().rotateY(.2f),1e-6f));
    }

    @Test void inputIsFrozenAndOnlyTheGestureChannelChanges() {
        var source=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var original=source.localTransform("head");
        var value=new BoneTransform(new Vector3f(9),new Quaternionf().rotateY(.8f),new Vector3f(3));
        var draft=new ActorBoneAnimationDraft(source,"head",value,RigPose.ROTATION,PoseBlendSettings.DEFAULT,1,5);
        source.setLocalTransform("head",BoneTransform.identity());
        var edited=draft.authoredPose();assertEquals(original.translation(),edited.localTransform("head").translation());
        assertEquals(original.scale(),edited.localTransform("head").scale());
        assertTrue(edited.localTransform("head").rotation().equals(value.rotation(),1e-6f));
        assertEquals(RigPose.ROTATION,edited.authoredChannels(edited.rig().requireBoneIndex("head")));
        edited.setLocalTransform("head",BoneTransform.identity());
        assertTrue(draft.authoredPose().localTransform("head").rotation().equals(value.rotation(),1e-6f));
    }
    @Test void translationAndScaleAreIndependentAndRetainOtherAuthoredBones() {
        var source=new RigPose(ArticulatedPlayerRig.DEFINITION);
        source.setLocalTransform("left_leg",source.localTransform("left_leg").withRotation(new Quaternionf().rotateX(.3f)));
        var old=source.localTransform("root");
        var value=new BoneTransform(new Vector3f(1,2,3),new Quaternionf().rotateY(1),new Vector3f(2,3,4));
        for(int channel:new int[]{RigPose.TRANSLATION,RigPose.SCALE}) {
            var pose=new ActorBoneAnimationDraft(source,"root",value,channel,PoseBlendSettings.DEFAULT,2,5).authoredPose();
            assertEquals(source.localTransform("left_leg"),pose.localTransform("left_leg"));
            assertEquals(old.rotation(),pose.localTransform("root").rotation());
            assertEquals(channel==RigPose.TRANSLATION?value.translation():old.translation(),pose.localTransform("root").translation());
            assertEquals(channel==RigPose.SCALE?value.scale():old.scale(),pose.localTransform("root").scale());
        }
    }
    @Test void liveDraftAndCommittedAssetInputProduceSameMixedResult() {
        var source=new RigPose(ArticulatedPlayerRig.DEFINITION);var base=source.copy();
        base.setLocalTransform("head",base.localTransform("head").withRotation(new Quaternionf().rotateY(.4f)));
        var value=source.localTransform("head").withRotation(new Quaternionf().rotateY(1));
        for(var mode:PoseLayer.BlendMode.values()) for(float weight:new float[]{0,.5f,1}) {
            var settings=new PoseBlendSettings(mode,weight,RigPose.ROTATION,false,Set.of("head"),2,1);
            for(float time:new float[]{0,1,2,4.5f,5,1}) {
                var draft=new ActorBoneAnimationDraft(source,"head",value,RigPose.ROTATION,settings,time,5);
                var live=RigPoseComposer.compose(base,List.of(settings.layer(draft.authoredPose(),draft.envelope()))).pose();
                var committed=source.copy();committed.setLocalTransform(committed.rig().requireBoneIndex("head"),value,RigPose.ROTATION);
                var played=RigPoseComposer.compose(base,List.of(settings.layer(committed,settings.envelopeAt(time,5)))).pose();
                assertTrue(live.localTransform("head").toMatrix().equals(played.localTransform("head").toMatrix(),1e-6f));
            }
        }
    }
    @Test void excludedBoneOrChannelDoesNotLeakDraftIntoFinalPose() {
        var source=new RigPose(ArticulatedPlayerRig.DEFINITION);var value=source.localTransform("head").withRotation(new Quaternionf().rotateX(.6f));
        for(var settings:List.of(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,RigPose.SCALE,true,Set.of()),
                new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,1,7,false,Set.of("left_leg")))) {
            var draft=new ActorBoneAnimationDraft(source,"head",value,RigPose.ROTATION,settings,2,5);
            assertEquals(source.localTransform("head"),RigPoseComposer.compose(source,List.of(settings.layer(draft.authoredPose(),draft.envelope()))).pose().localTransform("head"));
        }
        assertThrows(IllegalArgumentException.class,()->new ActorBoneAnimationDraft(source,"head",value,7,PoseBlendSettings.DEFAULT,1,5));
    }
}
