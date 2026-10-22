package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorRouteStopTransitionTest {
    private EntitySample sample(float time) {
        var sample=new EntitySample();sample.action=ActorActionType.WALK;sample.routeLocalTime=time;
        sample.routeClip=new ActorRouteClip("Route",0,4);
        sample.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(2.13f,0,0)));
        return sample;
    }
    @Test void onceBoundariesReleasePoseWithoutChangingTravelOrPhase() {
        var s=sample(0);var profile=new HumanoidRigProfile();
        assertEquals(0,ActorRouteGait.motionWeight(s));s.routeLocalTime=2;assertEquals(1,ActorRouteGait.motionWeight(s));
        s.routeLocalTime=3.9f;float phase=ActorRouteGait.phaseCycles(s,profile);
        assertEquals(.5f,ActorRouteGait.motionWeight(s),1e-5f);
        assertEquals(phase,ActorRouteGait.phaseCycles(s,profile));
        s.routeLocalTime=4;assertEquals(0,ActorRouteGait.motionWeight(s));
        var moving=profile.buildPose(ActorActionType.WALK,.7f,ActorRouteGait.time(s,profile)).pose;
        var idle=profile.buildPose(ActorActionType.IDLE,1,0).pose;
        var settled=ActorRouteGait.settle(moving,s,profile);
        assertEquals(idle.leftArmPitch,settled.leftArmPitch);assertEquals(idle.rightLegPitch,settled.rightLegPitch);
    }
    @Test void onlyDeclaredPathGaitPresetFadesWhilePerformanceAndAuthoredKeysRemain() {
        var s=sample(4);var profile=new HumanoidRigProfile();
        var gait=new ActorAnimationLayerSample("walk",1,true,1,PoseBlendSettings.DEFAULT,.8f);
        assertEquals(0,ActorPresetClock.resolve(gait,s,profile).envelope());
        var wave=new ActorAnimationLayerSample("wave",1,true,1,PoseBlendSettings.DEFAULT,.8f);
        assertSame(wave,ActorPresetClock.resolve(wave,s,profile));
        var base=new RigPose(ArticulatedPlayerRig.DEFINITION);var manual=new RigPose(base.rig());
        manual.setLocalTransform("right_leg",manual.localTransform("right_leg").withRotation(new org.joml.Quaternionf().rotateX(.9f)));
        var finalPose=RigPoseComposer.compose(base,List.of(PoseBlendSettings.DEFAULT.layer(manual))).pose();
        assertTrue(finalPose.localTransform("right_leg").rotation().equals(manual.localTransform("right_leg").rotation(),1e-6f));
    }
    @Test void smoothShortLoopAndExplicitPerformanceCasesRemainBoundedAndDeterministic() {
        var s=sample(.1f);s.routeClip.setSmoothTravel(true);
        float first=ActorRouteGait.motionWeight(s);assertTrue(first>0 && first<1);
        s.routeLocalTime=3.9f;assertEquals(first,ActorRouteGait.motionWeight(s),1e-5f);
        s.routeClip.setTimeRange(0,.1f);s.routeLocalTime=.05f;assertEquals(1,ActorRouteGait.motionWeight(s));
        s.routeLocalTime=.1f;assertEquals(0,ActorRouteGait.motionWeight(s));
        s.routeClip.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);assertEquals(1,ActorRouteGait.motionWeight(s));
        s.action=ActorActionType.IDLE;s.actionExplicit=true;var profile=new HumanoidRigProfile();
        var pose=profile.buildPose(ActorActionType.IDLE,1,0).pose;
        assertSame(pose,ActorRouteGait.settle(pose,s,profile));
    }
    @Test void jointAmplitudeReleaseDoesNotRecomputeItsFrequency() {
        var rig=ArticulatedPlayerRig.DEFINITION;var profile=new HumanoidRigProfile();
        var s=sample(.1f);float time=ActorRouteGait.time(s,profile);
        var full=new RigPose(rig);var half=new RigPose(rig);
        ActorJointGait.apply(full,s.action,.8f,time,profile,true,true,1);
        ActorJointGait.apply(half,s.action,.8f,time,profile,true,true,.5f);
        for(String joint:List.of("left_forearm","left_shin")) {
            float a=full.localTransform(joint).rotation().getEulerAnglesXYZ(new Vector3f()).x;
            float b=half.localTransform(joint).rotation().getEulerAnglesXYZ(new Vector3f()).x;
            assertEquals(a*.5f,b,1e-5f);
        }
    }
}
