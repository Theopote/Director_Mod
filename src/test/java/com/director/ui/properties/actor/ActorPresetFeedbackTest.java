package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetFeedbackTest {
    private ActorPresetBinding binding(String id,int channels,Set<String> bones) {
        return new ActorPresetBinding(id,false,1,new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.8f,
                channels,false,bones,2,1));
    }
    @Test void emptyAssetDiffersFromAnExcludedChannelMask() {
        var library=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance();
        var asset=new com.director.actor.animation.asset.AnimationAsset("test:empty-feedback","Empty",ArticulatedPlayerRig.DEFINITION,2,com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        library.register(asset);
        try {
            var binding=new ActorPresetBinding(asset.id(),false,1,PoseBlendSettings.DEFAULT);
            var empty=ActorPresetFeedback.resolve(binding,asset.rig(),1,4);
            assertTrue(empty.available());assertFalse(empty.authoredData());
            asset.getOrCreateBoneTrack("head").rotation().put(0,new org.joml.Quaternionf(),com.director.actor.animation.asset.InterpolationMode.LINEAR);
            var masked=new ActorPresetBinding(asset.id(),false,1,new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,0,false,Set.of()));
            var excluded=ActorPresetFeedback.resolve(masked,asset.rig(),1,4);
            assertTrue(excluded.authoredData());assertTrue(excluded.bones().isEmpty());
            var restored=ActorPresetScope.restoreChannelsAndBones(masked);
            assertEquals(java.util.List.of("head"),ActorPresetFeedback.resolve(restored,asset.rig(),1,4).bones());
            assertEquals(masked.animationId(),restored.animationId());assertEquals(masked.speed(),restored.speed());
        } finally {library.remove(asset.id());}
    }

    @Test void routeLegWarningUsesActualRotationChannelsRatherThanScaleOrEmptyMask() {
        var library=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance();
        var asset=new com.director.actor.animation.asset.AnimationAsset("test:scope-feedback","Test",ArticulatedPlayerRig.DEFINITION,2,com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack("left_leg").scale().put(0,new org.joml.Vector3f(1.2f),com.director.actor.animation.asset.InterpolationMode.LINEAR);
        asset.getOrCreateBoneTrack("right_arm").rotation().put(0,new org.joml.Quaternionf(),com.director.actor.animation.asset.InterpolationMode.LINEAR);
        library.register(asset);
        try {
            var binding=new ActorPresetBinding(asset.id(),false,1,PoseBlendSettings.DEFAULT);
            var feedback=ActorPresetFeedback.resolve(binding,asset.rig(),1,4);
            assertTrue(feedback.bones().contains("left_leg"));assertFalse(feedback.rotationBones().stream().anyMatch(ActorPresetScope::isLeg));
            asset.getOrCreateBoneTrack("left_leg").rotation().put(0,new org.joml.Quaternionf(),com.director.actor.animation.asset.InterpolationMode.LINEAR);
            assertTrue(ActorPresetFeedback.resolve(binding,asset.rig(),1,4).rotationBones().stream().anyMatch(ActorPresetScope::isLeg));
            var upper=ActorPresetScope.UPPER.apply(binding,asset.rig());
            assertFalse(ActorPresetFeedback.resolve(upper,asset.rig(),1,4).rotationBones().stream().anyMatch(ActorPresetScope::isLeg));
        } finally {library.remove(asset.id());}
    }

    @Test void feedbackIncludesPathStopFadeOnlyForLocomotionPreset() {
        var sample=new com.director.timeline.playback.entity.EntitySample();
        sample.action=com.director.actor.action.ActorActionType.WALK;sample.routeLocalTime=0;
        sample.routeClip=new com.director.timeline.model.entity.ActorRouteClip("Route",0,4);
        sample.routeClip.setWaypoints(java.util.List.of(new org.joml.Vector3f(),new org.joml.Vector3f(2,0,0)));
        var rig=ArticulatedPlayerRig.DEFINITION;var profile=new com.director.actor.rig.HumanoidRigProfile();
        assertEquals(0,ActorPresetFeedback.resolve(binding("walk",2,Set.of("right_arm")),rig,3,6,sample,profile).weight());
        assertEquals(.8f,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,3,6,sample,profile).weight(),1e-6f);
    }

    @Test void feedbackIntersectsActualOwnershipAndSelectedChannels() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        for(var binding:new ActorPresetBinding[]{binding("wave",1,Set.of("right_arm")),
                binding("wave",2,Set.of("left_leg")),binding("wave",0,Set.of("right_arm"))}) {
            var feedback=ActorPresetFeedback.resolve(binding,rig,3,6);
            assertTrue(feedback.available());assertTrue(feedback.bones().isEmpty());assertEquals(0,feedback.weight());
        }
        var valid=ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm","left_leg")),rig,1,6);
        assertEquals(.4f,valid.weight(),1e-6f);assertEquals(java.util.List.of("right_arm"),valid.bones());
    }
    @Test void missingOutsideRangeAndFadeFeedbackRemainHonest() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        assertFalse(ActorPresetFeedback.resolve(binding("test:missing",2,Set.of("head")),rig,1,6).available());
        assertEquals(0,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,-1,6).weight());
        assertEquals(0,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,7,6).weight());
        assertEquals(.8f,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,3,6).weight(),1e-6f);
    }
}
