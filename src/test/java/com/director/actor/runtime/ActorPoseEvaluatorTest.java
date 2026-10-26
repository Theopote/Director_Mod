package com.director.actor.runtime;

import com.director.actor.ActorType;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorPoseEvaluatorTest {
    private final HumanoidRigProfile profile=new HumanoidRigProfile();
    private ActorPoseEvaluator.Result evaluate(EntitySample s,ActorPoseEvaluator.Layer preset,ActorPoseEvaluator.Layer manual) {
        var base=ActorRouteGait.sample(s,profile,new ActorRuntime()).pose;
        return ActorPoseEvaluator.evaluate(ActorType.PLAYER,profile,ArticulatedPlayerRig.DEFINITION,
                ActorRouteGait.settle(base,s,profile),s,preset,manual,LimbBendMode.SHARP);
    }
    private RigPose keyed(String bone,float yaw) {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        pose.setLocalTransform(pose.rig().requireBoneIndex(bone),pose.localTransform(bone).withRotation(new Quaternionf().rotateY(yaw)),RigPose.ROTATION);
        return pose;
    }
    private EntitySample route() {
        var s=new EntitySample();s.action=ActorActionType.WALK;s.speed=1;s.routeLocalTime=.15f;s.position.z=.15f;
        s.routeClip=new ActorRouteClip("Path",0,4);s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(0,0,4)));
        return s;
    }
    private ActorPoseStage stage(ActorPoseEvaluator.Result result,ActorPoseStage.Kind kind) {
        return result.stages().stream().filter(s->s.kind()==kind).findFirst().orElseThrow();
    }
    @Test void stageFeedbackUsesAcceptedChannelsAndDoesNotInventContactWeight() {
        var s=route();var settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.4f,2,true,java.util.Set.of());
        var result=evaluate(s,new ActorPoseEvaluator.Layer(keyed("head",.2f),settings,1),null);
        var preset=stage(result,ActorPoseStage.Kind.PRESET);
        assertEquals(ActorPoseStage.State.APPLIED,preset.state());assertEquals(.4f,preset.weight());
        assertEquals(java.util.Set.of(new ActorPoseSources.Channel("head",2)),preset.channels());
        assertEquals(ActorPoseStage.State.ABSENT,stage(result,ActorPoseStage.Kind.MANUAL).state());
        assertFalse(Float.isFinite(stage(result,ActorPoseStage.Kind.CONTACT).weight()));
        assertThrows(UnsupportedOperationException.class,()->result.stages().clear());
        assertThrows(UnsupportedOperationException.class,()->preset.channels().clear());
        var wrong=new RigPose(HumanoidRig.DEFINITION);wrong.setLocalTransform(wrong.rig().requireBoneIndex("head"),wrong.localTransform("head"),2);
        var rejected=evaluate(s,new ActorPoseEvaluator.Layer(wrong,PoseBlendSettings.DEFAULT,1),null);
        assertEquals(ActorPoseStage.State.REJECTED,stage(rejected,ActorPoseStage.Kind.PRESET).state());
        assertTrue(stage(rejected,ActorPoseStage.Kind.PRESET).channels().isEmpty());
    }
    @Test void primarySlotPresetIsBelowQuickPoseAndNeverReportedAsManualKeys() {
        var s=route();s.animationIsPreset=true;s.poseOverrideEnabled=true;s.poseOverrideHead=true;s.poseHeadYaw=.8f;
        var result=evaluate(s,null,new ActorPoseEvaluator.Layer(keyed("head",.2f),PoseBlendSettings.DEFAULT,1));
        assertEquals(ActorPoseStage.State.APPLIED,stage(result,ActorPoseStage.Kind.PRESET).state());
        assertEquals(ActorPoseStage.State.ABSENT,stage(result,ActorPoseStage.Kind.MANUAL).state());
        assertTrue(new Quaternionf().rotateXYZ(0,.8f,0).equals(result.pose().localTransform("head").rotation(),1e-6f));
        var manual=keyed("head",1.2f);
        var corrected=evaluate(s,new ActorPoseEvaluator.Layer(keyed("head",.2f),PoseBlendSettings.DEFAULT,1),new ActorPoseEvaluator.Layer(manual,PoseBlendSettings.DEFAULT,1));
        assertEquals(ActorPoseStage.State.APPLIED,stage(corrected,ActorPoseStage.Kind.MANUAL).state());
        assertTrue(manual.localTransform("head").rotation().equals(corrected.pose().localTransform("head").rotation(),1e-6f));
    }
    @Test void nonPlayerNeverReportsPlayerContactAsActive() {
        var s=route();
        var result=ActorPoseEvaluator.evaluate(ActorType.COW,profile,HumanoidRig.DEFINITION,new Pose(),s,null,null,LimbBendMode.SHARP);
        assertEquals(ActorPoseStage.State.UNSUPPORTED,stage(result,ActorPoseStage.Kind.CONTACT).state());
        assertTrue(stage(result,ActorPoseStage.Kind.CONTACT).channels().isEmpty());
    }

    @Test void zeroWeightAndEmptyMasksAreDifferentStageStates() {
        var s=route();var zero=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,2,true,java.util.Set.of());
        var disabled=evaluate(s,new ActorPoseEvaluator.Layer(keyed("head",.2f),zero,1),null);
        assertEquals(ActorPoseStage.State.DISABLED,stage(disabled,ActorPoseStage.Kind.PRESET).state());
        var mask=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,4,true,java.util.Set.of());
        var empty=evaluate(s,new ActorPoseEvaluator.Layer(keyed("head",.2f),mask,1),null);
        assertEquals(ActorPoseStage.State.EMPTY,stage(empty,ActorPoseStage.Kind.PRESET).state());
    }

    @Test void rootTranslationAndLegScaleDoNotClaimLegRotationContact() {
        var s=route();var manual=new RigPose(ArticulatedPlayerRig.DEFINITION);
        int root=manual.rig().requireBoneIndex(HumanoidRig.ROOT);
        var rest=manual.localTransform(root);
        manual.setLocalTransform(root,new BoneTransform(rest.translation().add(0,.02f,0),rest.rotation(),rest.scale()),RigPose.TRANSLATION);
        int leg=manual.rig().requireBoneIndex(HumanoidRig.LEFT_LEG);var local=manual.localTransform(leg);
        manual.setLocalTransform(leg,new BoneTransform(local.translation(),local.rotation(),new Vector3f(1.1f)),RigPose.SCALE);
        var result=evaluate(s,null,new ActorPoseEvaluator.Layer(manual,PoseBlendSettings.DEFAULT,1));
        assertFalse(result.authoredWeights().containsKey(HumanoidRig.ROOT));
        assertFalse(result.authoredWeights().containsKey(HumanoidRig.LEFT_LEG));
        assertTrue(result.sources().values().stream().anyMatch(source->source.contains("脚底")));
    }

    @Test void feedbackReportsAcceptedSparseChannelsAndActualWeight() {
        var s=route(); var preset=keyed(HumanoidRig.HEAD,.4f); var manual=keyed(HumanoidRig.HEAD,.7f);
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.25f,2,true,java.util.Set.of());
        var result=evaluate(s,new ActorPoseEvaluator.Layer(preset,PoseBlendSettings.DEFAULT,1),
                new ActorPoseEvaluator.Layer(manual,settings,1));
        var head=new ActorPoseSources.Channel(HumanoidRig.HEAD,2);
        assertTrue(result.sources().get(head).contains("25%"));
        assertTrue(result.sources().get(head).contains("动作预设"));
        assertFalse(result.sources().containsKey(new ActorPoseSources.Channel(HumanoidRig.HEAD,4)));
        var invalid=new RigPose(HumanoidRig.DEFINITION);
        invalid.setLocalTransform(0,BoneTransform.identity(),2);
        var rejected=evaluate(s,null,new ActorPoseEvaluator.Layer(invalid,PoseBlendSettings.DEFAULT,1));
        assertFalse(rejected.diagnostics().isEmpty());
        assertFalse(rejected.sources().containsValue("手工骨骼关键帧"));
    }
    @Test void zeroWeightDoesNotClaimControlAndQuickPoseOwnsEqualValues() {
        var s=route();s.poseOverrideEnabled=true;s.poseOverrideHead=true;s.poseHeadYaw=0;
        var zero=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,2,true,java.util.Set.of());
        var result=evaluate(s,null,new ActorPoseEvaluator.Layer(keyed(HumanoidRig.HEAD,1),zero,1));
        assertEquals("快速姿态",result.sources().get(new ActorPoseSources.Channel(HumanoidRig.HEAD,2)));
    }

    @Test void manualSparseChannelsOverrideQuickPoseAndPresetWithoutMutatingInputs() {
        var s=route();s.poseOverrideEnabled=true;s.poseOverrideHead=true;s.poseHeadYaw=.8f;
        var preset=keyed(HumanoidRig.HEAD,.4f);var manual=keyed(HumanoidRig.HEAD,1.1f);
        var p=new ActorPoseEvaluator.Layer(preset,PoseBlendSettings.DEFAULT,1);
        var m=new ActorPoseEvaluator.Layer(manual,PoseBlendSettings.DEFAULT,1);
        var result=evaluate(s,p,m);
        assertTrue(result.pose().localTransform(HumanoidRig.HEAD).rotation().equals(new Quaternionf().rotateY(1.1f),1e-6f));
        assertTrue(preset.localTransform(HumanoidRig.HEAD).rotation().equals(new Quaternionf().rotateY(.4f),1e-6f));
        assertEquals(.15f,s.position.z);assertEquals(.8f,s.poseHeadYaw);
        assertEquals(LimbBendMode.SHARP,result.pose().limbBendMode());assertTrue(result.diagnostics().isEmpty());
    }
    @Test void committedAndExplicitDraftSnapshotsProduceSameBonesAndSockets() {
        var s=route();var source=keyed(HumanoidRig.RIGHT_ARM,.7f);
        var committed=new ActorPoseEvaluator.Layer(source,PoseBlendSettings.DEFAULT,.6f);
        var draft=new ActorPoseEvaluator.Layer(source.copy(),PoseBlendSettings.DEFAULT,.6f);
        var a=evaluate(s,null,committed);evaluate(route(),null,null);var b=evaluate(s,null,draft);
        for(int i=0;i<a.pose().rig().boneCount();i++) assertTrue(a.pose().localTransform(i).toMatrix().equals(b.pose().localTransform(i).toMatrix(),1e-6f));
        var socket=com.director.actor.prop.ActorPropSocketResolver.resolve(a.pose(),new Matrix4f(),com.director.actor.prop.ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        var other=com.director.actor.prop.ActorPropSocketResolver.resolve(b.pose(),new Matrix4f(),com.director.actor.prop.ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        assertTrue(socket.equals(other,1e-6f));
    }
    @Test void authoredLegWeightsProtectContactAndIncompatibleLayersDoNotClaimControl() {
        var s=route();var manual=keyed(HumanoidRig.LEFT_LEG,.5f);
        var result=evaluate(s,null,new ActorPoseEvaluator.Layer(manual,PoseBlendSettings.DEFAULT,1));
        assertTrue(manual.localTransform(HumanoidRig.LEFT_LEG).toMatrix().equals(result.pose().localTransform(HumanoidRig.LEFT_LEG).toMatrix(),1e-6f));
        var wrong=new RigPose(HumanoidRig.DEFINITION);wrong.setLocalTransform("head",wrong.localTransform("head"));
        var skipped=evaluate(s,null,new ActorPoseEvaluator.Layer(wrong,PoseBlendSettings.DEFAULT,1));
        assertEquals(1,skipped.diagnostics().size());assertEquals("pose-layer",skipped.diagnostics().getFirst().stage());
        assertTrue(skipped.authoredWeights().isEmpty());
    }
    @Test void layerSnapshotsAreFrozenAndZeroEnvelopeDoesNotClaimOwnership() {
        var source=keyed(HumanoidRig.HEAD,.7f);var layer=new ActorPoseEvaluator.Layer(source,PoseBlendSettings.DEFAULT,0);
        source.setLocalTransform("head",source.localTransform("head").withRotation(new Quaternionf()));
        layer.pose().setLocalTransform("head",source.localTransform("head"));
        assertTrue(layer.pose().localTransform("head").rotation().equals(new Quaternionf().rotateY(.7f),1e-6f));
        assertTrue(evaluate(route(),null,layer).authoredWeights().isEmpty());
    }
    @Test void inactiveQuickPoseFlagsDoNotDisableProceduralKnees() {
        var s=route();var baseline=evaluate(s,null,null);
        s.poseOverrideLegs=true;s.poseOverrideArms=true;
        var result=evaluate(s,null,null);
        for(int i=0;i<result.pose().rig().boneCount();i++) assertTrue(baseline.pose().localTransform(i).toMatrix().equals(result.pose().localTransform(i).toMatrix(),1e-6f));
    }
}
