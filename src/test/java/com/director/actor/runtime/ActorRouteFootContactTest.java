package com.director.actor.runtime;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ActorRouteFootContactTest {
    private EntitySample sample(float time) {
        var s = new EntitySample(); s.routeClip = new ActorRouteClip("Route",0,4);
        s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(0,0,4)));
        s.speed=1; s.routeLocalTime=time; s.position.set(0,0,time); return s;
    }
    private Vector3f sole(RigPose pose, EntitySample s) {
        var leg=pose.localTransform(HumanoidRig.LEFT_LEG);
        return leg.rotation().transform(new Vector3f(0,-.75f,0)).add(leg.translation()).add(s.position);
    }
    @Test void stanceKeepsWorldSoleStationaryForSmallTranslationAndIsDeterministic() {
        var a=sample(.15f); var b=sample(.2f);
        var pa=new RigPose(HumanoidRig.DEFINITION); var pb=new RigPose(HumanoidRig.DEFINITION);
        ActorRouteFootContact.apply(pa,a); ActorRouteFootContact.apply(pb,b);
        assertEquals(sole(pa,a),sole(pb,b));
        var replay=new RigPose(HumanoidRig.DEFINITION); ActorRouteFootContact.apply(replay,a);
        assertEquals(pa.localTransform(HumanoidRig.LEFT_LEG).translation(),replay.localTransform(HumanoidRig.LEFT_LEG).translation());
        a.actionExplicit=true; var manual=new RigPose(HumanoidRig.DEFINITION); ActorRouteFootContact.apply(manual,a);
        assertEquals(new Vector3f(.125f,.75f,0),manual.localTransform(HumanoidRig.LEFT_LEG).translation());
    }
    @Test void articulatedSoleIncludesKneeRotationAndScale() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        int knee=pose.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_SHIN);
        pose.setLocalTransform(knee,new BoneTransform(pose.localTransform(knee).translation(),
                new org.joml.Quaternionf().rotateX((float)Math.PI/2),new Vector3f(2)),RigPose.ROTATION|RigPose.SCALE);
        var sole=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG);
        assertEquals(.125f,sole.x,1e-5f);
        var meshFoot=new LimbBendTransform(new org.joml.Quaternionf().rotateX((float)Math.PI/2),new Vector3f(2))
                .map(new Vector3f(0,6,0)).mul(1f/16);
        assertEquals(.375f-meshFoot.y,sole.y,1e-5f);
        assertEquals(-meshFoot.z,sole.z,1e-5f);
    }
    @Test void articulatedStanceCorrectionImprovesSoleWithoutMovingHip() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var sample=sample(.2f);
        int knee=pose.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_SHIN);
        var rotation=new org.joml.Quaternionf().rotateX(.2f);
        pose.setLocalTransform(knee,pose.localTransform(knee).withRotation(rotation),RigPose.ROTATION);
        var before=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(sample.position);
        ActorRouteFootContact.apply(pose,sample);
        var after=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(sample.position);
        assertTrue(after.distance(new Vector3f(.125f,0,.2f)) < before.distance(new Vector3f(.125f,0,.2f)));
        assertEquals(new RigPose(pose.rig()).localTransform(HumanoidRig.LEFT_LEG).translation(),pose.localTransform(HumanoidRig.LEFT_LEG).translation());
        var replay=new RigPose(ArticulatedPlayerRig.DEFINITION);
        replay.setLocalTransform(knee,replay.localTransform(knee).withRotation(rotation),RigPose.ROTATION);
        ActorRouteFootContact.apply(replay,sample);
        assertEquals(pose.localTransform(HumanoidRig.LEFT_LEG).translation(),replay.localTransform(HumanoidRig.LEFT_LEG).translation());
    }
    @Test void keyedLegIsUntouchedWhileOtherLegCanReceiveContactCorrection() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var left=pose.localTransform(HumanoidRig.LEFT_LEG).toMatrix();
        var right=pose.localTransform(HumanoidRig.RIGHT_LEG).toMatrix();
        ActorRouteFootContact.apply(pose,sample(.56f),java.util.Set.of(ArticulatedPlayerRig.LEFT_SHIN));
        assertTrue(left.equals(pose.localTransform(HumanoidRig.LEFT_LEG).toMatrix(),1e-6f));
        assertFalse(right.equals(pose.localTransform(HumanoidRig.RIGHT_LEG).toMatrix(),1e-6f));
    }
    @Test void contactCorrectionReleasesContinuouslyWithAuthoredWeight() {
        var sample=sample(.15f);
        var rest=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var full=rest.copy();ActorRouteFootContact.apply(full,sample);
        assertFalse(full.localTransform(HumanoidRig.LEFT_LEG).rotation().equals(rest.localTransform(HumanoidRig.LEFT_LEG).rotation(),1e-6f));
        for(float weight:new float[]{0,.001f,.25f,.5f,.999f,1,0}) {
            var pose=rest.copy();
            ActorRouteFootContact.apply(pose,sample,java.util.Map.of(ArticulatedPlayerRig.LEFT_SHIN,weight));
            for(String bone:List.of(HumanoidRig.LEFT_LEG,ArticulatedPlayerRig.LEFT_SHIN)) {
                var expected=rest.localTransform(bone).rotation().slerp(full.localTransform(bone).rotation(),1-weight);
                assertTrue(expected.equals(pose.localTransform(bone).rotation(),1e-6f));
                assertEquals(rest.localTransform(bone).translation(),pose.localTransform(bone).translation());
            }
        }
        var protectedPose=rest.copy();ActorRouteFootContact.apply(protectedPose,sample,java.util.Map.of(HumanoidRig.ROOT,1f));
        assertTrue(full.localTransform(HumanoidRig.LEFT_LEG).toMatrix().equals(protectedPose.localTransform(HumanoidRig.LEFT_LEG).toMatrix(),1e-6f));
    }
    private EntitySample repeated(ActorRouteClip.LoopMode mode,float time) {
        var sample=sample(time);sample.routeClip.setLoopMode(mode);sample.routeClip.setSpeed(1);
        var point=com.director.timeline.playback.entity.ActorRouteMotion.traversalAt(sample.routeClip,time);
        sample.position.set(0,0,point.curveDistance());sample.rotation.x=point.reversed()?180:0;
        sample.action=com.director.actor.action.ActorActionType.WALK;
        return sample;
    }
    private Vector3f worldSole(RigPose pose,EntitySample sample) {
        var point=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).mul(sample.scale);
        return new org.joml.Quaternionf().rotateY((float)Math.toRadians(-sample.rotation.x)).transform(point).add(sample.position);
    }
    @Test void loopingAndReturningStanceHoldTheMappedWorldPointAndAreSeekDeterministic() {
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            var a=repeated(mode,4.95f);var b=repeated(mode,5f);
            var pa=new RigPose(HumanoidRig.DEFINITION);var pb=new RigPose(HumanoidRig.DEFINITION);
            ActorRouteFootContact.apply(pa,a);ActorRouteFootContact.apply(pb,b);
            assertTrue(worldSole(pa,a).equals(worldSole(pb,b),1e-5f));
            var replay=new RigPose(HumanoidRig.DEFINITION);ActorRouteFootContact.apply(replay,a);
            assertEquals(pa.localTransform(HumanoidRig.LEFT_LEG).translation(),replay.localTransform(HumanoidRig.LEFT_LEG).translation());
            assertNotEquals(new RigPose(HumanoidRig.DEFINITION).localTransform(HumanoidRig.LEFT_LEG).translation(),pa.localTransform(HumanoidRig.LEFT_LEG).translation());
        }
    }
    @Test void seamAndTurnReleaseContactRatherThanPullingFeetAcrossTraversalBoundary() {
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            for(float time:new float[]{3.9999f,4f,4.0001f,7.9999f,8f,8.0001f}) {
                var sample=repeated(mode,time);var pose=new RigPose(HumanoidRig.DEFINITION);var original=pose.localTransform(HumanoidRig.LEFT_LEG).translation();
                ActorRouteFootContact.apply(pose,sample);
                assertTrue(pose.localTransform(HumanoidRig.LEFT_LEG).translation().distance(original)<.001f);
                assertTrue(worldSole(pose,sample).isFinite());
            }
        }
    }
    private com.director.actor.rig.ActorRigProfile profile(float stride) {
        var delegate=new com.director.actor.rig.HumanoidRigProfile();
        return new com.director.actor.rig.ActorRigProfile() {
            public float locomotionStride() { return stride; }
            public boolean supports(com.director.actor.action.ActorActionType action) { return delegate.supports(action); }
            public com.director.actor.rig.RigProfileStyle getStyle() { return delegate.getStyle(); }
            public void applyAction(ActorRuntime runtime,com.director.actor.action.ActorActionType action,float intensity,float delta) { delegate.applyAction(runtime,action,intensity,delta); }
            public com.director.actor.rig.RigPoseResult buildPose(com.director.actor.action.ActorActionType action,float intensity,float time) { return delegate.buildPose(action,intensity,time); }
        };
    }
    @Test void contactUsesProfileStrideAndMatchesScaledGaitPhase() {
        var sample=sample(.2f);var wide=profile(1.6f);var pose=new RigPose(HumanoidRig.DEFINITION);
        ActorRouteFootContact.apply(pose,sample,wide,java.util.Map.of());
        assertEquals(.2f,pose.localTransform(HumanoidRig.LEFT_LEG).translation().z,1e-5f);
        assertEquals(.125f,ActorRouteGait.phaseCycles(sample,wide),1e-5f);
        sample.scale.z=2;assertEquals(.0625f,ActorRouteGait.phaseCycles(sample,wide),1e-5f);
        assertEquals(3.2f,ActorRouteGait.stride(sample,wide),1e-5f);
        var bad=new RigPose(HumanoidRig.DEFINITION);ActorRouteFootContact.apply(bad,sample,profile(Float.NaN),java.util.Map.of());
        assertEquals(new RigPose(HumanoidRig.DEFINITION).localTransform(HumanoidRig.LEFT_LEG),bad.localTransform(HumanoidRig.LEFT_LEG));
    }
    @Test void explicitLocomotionAllowsContactButManualLegKeysStillWin() {
        var sample=repeated(ActorRouteClip.LoopMode.LOOP,4.95f);sample.actionExplicit=true;
        for(var action:List.of(com.director.actor.action.ActorActionType.WALK,com.director.actor.action.ActorActionType.RUN)) {
            sample.action=action;var pose=new RigPose(HumanoidRig.DEFINITION);
            ActorRouteFootContact.apply(pose,sample);
            assertNotEquals(new RigPose(HumanoidRig.DEFINITION).localTransform(HumanoidRig.LEFT_LEG).translation(),pose.localTransform(HumanoidRig.LEFT_LEG).translation());
            var manual=new RigPose(HumanoidRig.DEFINITION);ActorRouteFootContact.apply(manual,sample,java.util.Set.of(HumanoidRig.LEFT_LEG));
            assertEquals(new RigPose(HumanoidRig.DEFINITION).localTransform(HumanoidRig.LEFT_LEG),manual.localTransform(HumanoidRig.LEFT_LEG));
        }
    }
    @Test void upperBodyQuickPoseDoesNotDisableLegContact() {
        for(int region=0;region<3;region++) {
            var s=sample(.15f);s.poseOverrideEnabled=true;
            s.poseOverrideHead=region==0;s.poseOverrideArms=region==1;s.poseOverrideBody=region==2;
            var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
            var rest=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG);
            ActorRouteFootContact.apply(pose,s);
            assertNotEquals(rest,ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG));
            s.poseOverrideLegs=true;pose=new RigPose(pose.rig());ActorRouteFootContact.apply(pose,s);
            assertEquals(rest,ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG));
        }
    }
    @Test void articulatedLoopAndReturnContactsReduceWorldErrorWithoutMovingHip() {
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            var s=repeated(mode,4.15f);var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
            var hip=pose.localTransform(HumanoidRig.LEFT_LEG).translation();
            var target=new Vector3f(mode==ActorRouteClip.LoopMode.LOOP?.125f:-.125f,0,mode==ActorRouteClip.LoopMode.LOOP?.2f:3.8f);
            float error=worldSole(pose,s).distance(target);
            ActorRouteFootContact.apply(pose,s);
            assertTrue(worldSole(pose,s).distance(target)<error);
            assertEquals(hip,pose.localTransform(HumanoidRig.LEFT_LEG).translation());
            var replay=new RigPose(pose.rig());ActorRouteFootContact.apply(replay,s);
            assertTrue(pose.localTransform(HumanoidRig.LEFT_LEG).toMatrix().equals(replay.localTransform(HumanoidRig.LEFT_LEG).toMatrix(),1e-6f));
        }
    }
    @Test void singleBoneQueryMatchesFullDeformedRigForBothBendModesAndScales() {
        for(var mode:LimbBendMode.values()) {
            var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);pose.setLimbBendMode(mode);
            int hip=pose.rig().requireBoneIndex(HumanoidRig.LEFT_LEG),knee=pose.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_SHIN);
            pose.setLocalTransform(hip,new BoneTransform(pose.localTransform(hip).translation(),new org.joml.Quaternionf().rotateXYZ(.3f,.2f,.1f),new Vector3f(1,.8f,1.2f)));
            pose.setLocalTransform(knee,new BoneTransform(pose.localTransform(knee).translation(),LimbBendTransform.encodedRotation(.7f,.2f),new Vector3f(1.2f,.9f,1)));
            var all=pose.calculateModelTransforms();
            for(int i=0;i<pose.rig().boneCount();i++) assertTrue(all[i].equals(pose.calculateModelTransform(i),1e-6f));
        }
    }
}
