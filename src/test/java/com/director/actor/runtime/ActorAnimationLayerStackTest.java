package com.director.actor.runtime;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.junit.jupiter.api.*;
import org.joml.Quaternionf;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationLayerStackTest {
    private static final String MISSING="test:manual-not-registered";
    private ActorTrack track() {
        var track=new ActorTrack("Actor");
        var spawn=new EntitySpawnClip("Spawn",2,8);spawn.setBoneAnimationId(MISSING);track.addClip(spawn);
        var preset=new EntityAnimationClip("Wave",1,10);preset.setAnimationId("wave");
        preset.setSpeed(2);preset.setLoop(true);track.addClip(preset);
        return track;
    }
    @Test void independentClocksSettingsAndCopySurviveSampling() {
        var track=track();
        var preset=(EntityAnimationClip)track.getClips().stream().filter(c->c instanceof EntityAnimationClip).findFirst().orElseThrow();
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.4f,2,true,Set.of(),2,1);
        preset.setBlendSettings(settings);
        var sample=new EntityClipSampler(track).sampleSnapshot(3);
        assertEquals(MISSING,sample.animationId);assertEquals(1,sample.animationLocalTime);
        assertNotNull(sample.presetAnimation);assertEquals("wave",sample.presetAnimation.id());
        assertEquals(2,sample.presetAnimation.localTime());assertEquals(2,sample.presetAnimation.speed());
        assertEquals(settings,sample.presetAnimation.settings());assertEquals(1,sample.presetAnimation.envelope());
        assertSame(sample.presetAnimation,sample.copy().presetAnimation);
        assertEquals(2,track.getClips().size());
    }
    @Test void embeddedLayerWinsRegardlessOfClipStartAndMutedPresetIsAbsent() {
        var track=track();
        var preset=(EntityAnimationClip)track.getClips().stream().filter(c->c instanceof EntityAnimationClip).findFirst().orElseThrow();
        preset.setTimeRange(3,10);
        var sampler=new EntityClipSampler(track);
        assertEquals(MISSING,sampler.sampleSnapshot(4).animationId);
        preset.setMuted(true);assertNull(sampler.sampleSnapshot(4).presetAnimation);
        assertNull(sampler.sampleSnapshot(0).presetAnimation);
    }
    @Test void presetThenSparseManualKeepsOtherChannelsAndProtectsFeetByActualOwnership() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        var base=new RigPose(rig);var weights=new HashMap<String,Float>();
        var preset=new ActorAnimationLayerSample("wave",.4f,false,1,PoseBlendSettings.DEFAULT,1);
        var lower=ActorRigPoseComposition.prependPreset(base,preset,d->fail(d.reason()),weights);
        assertTrue(weights.containsKey("right_arm"));assertFalse(weights.containsKey("left_leg"));
        var manual=new RigPose(rig);var rotation=new Quaternionf().rotateX(.3f);
        manual.setLocalTransform(rig.requireBoneIndex("head"),manual.localTransform("head").withRotation(rotation),RigPose.ROTATION);
        var result=ActorRigPoseComposition.compose(lower,manual,p->{},d->fail(d.reason()),PoseBlendSettings.DEFAULT);
        assertEquals(rotation,result.localTransform("head").rotation());
        assertEquals(lower.localTransform("right_arm"),result.localTransform("right_arm"));
        assertEquals(base.localTransform("left_leg"),result.localTransform("left_leg"));
        assertEquals(new Quaternionf(),base.localTransform("head").rotation());
    }
    @Test void presetDraftStaysBelowManualCorrectionAndKeepsPresetEnvelope() {
        var rig=ArticulatedPlayerRig.DEFINITION;var base=new RigPose(rig);
        var value=base.localTransform("right_arm").withRotation(new Quaternionf().rotateX(.8f));
        var draft=new com.director.ui.actor.pose.ActorBoneAnimationDraft(base,"right_arm",value,2,
                PoseBlendSettings.DEFAULT,1,5,"wave");
        assertEquals("wave",draft.animationId());
        var preset=new ActorAnimationLayerSample("wave",1,false,1,PoseBlendSettings.DEFAULT,.5f);
        var lower=ActorRigPoseComposition.prependPreset(base,preset,d->fail(),new HashMap<>(),draft);
        assertEquals(.4f,lower.localTransform("right_arm").rotation().getEulerAnglesXYZ(new org.joml.Vector3f()).x,1e-5f);
        var manual=new RigPose(rig);manual.setLocalTransform(rig.requireBoneIndex("right_arm"),
                value.withRotation(new Quaternionf().rotateX(.2f)),2);
        var finalPose=ActorRigPoseComposition.compose(lower,manual,p->{},d->fail());
        assertEquals(.2f,finalPose.localTransform("right_arm").rotation().getEulerAnglesXYZ(new org.joml.Vector3f()).x,1e-5f);
    }
    @Test void incompatiblePresetIsSkippedWithoutClaimingFeet() {
        String id="test:layer-stack-incompatible";
        var asset=new AnimationAsset(id,id,HumanoidRig.DEFINITION,1,AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack("head").rotation().put(0,new Quaternionf().rotateX(1),InterpolationMode.LINEAR);
        AnimationAssetLibrary.getInstance().addIfAbsent(asset);
        try {
            var base=new RigPose(ArticulatedPlayerRig.DEFINITION);var diagnostics=new ArrayList<RigPoseComposer.Diagnostic>();
            var weights=new HashMap<String,Float>();
            var result=ActorRigPoseComposition.prependPreset(base,
                    new ActorAnimationLayerSample(id,0,false,1,PoseBlendSettings.DEFAULT,1),diagnostics::add,weights);
            assertEquals(1,diagnostics.size());assertTrue(weights.isEmpty());
            assertEquals(base.localTransform("head"),result.localTransform("head"));
        } finally { AnimationAssetLibrary.getInstance().remove(id); }
    }

    @Test void zeroEnvelopeAndMissingReferenceDoNotClaimControl() {
        var base=new RigPose(ArticulatedPlayerRig.DEFINITION);var weights=new HashMap<String,Float>();
        var zero=new ActorAnimationLayerSample("wave",.4f,false,1,PoseBlendSettings.DEFAULT,0);
        var result=ActorRigPoseComposition.prependPreset(base,zero,d->fail(d.reason()),weights);
        assertEquals(base.localTransform("right_arm"),result.localTransform("right_arm"));assertTrue(weights.isEmpty());
        assertSame(base,ActorRigPoseComposition.prependPreset(base,
                new ActorAnimationLayerSample(MISSING,1,false,1,PoseBlendSettings.DEFAULT,1),d->fail(),weights));
    }
}
