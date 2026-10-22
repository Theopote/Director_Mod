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

class ActorRouteContinuityTest {
    private EntitySample moving(float speed) {
        var sample=new EntitySample();sample.speed=speed;sample.routeLocalTime=1;
        sample.routeClip=new ActorRouteClip("Route",0,4);
        sample.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(.8f,0,0)));
        EntityClipSampler.resolveAutoAction(sample);return sample;
    }
    @Test void automaticWalkRunPoseAndJointsAreContinuousAtSemanticThreshold() {
        for(var profile:List.<ActorRigProfile>of(new HumanoidRigProfile(),new QuadrupedRigProfile())) {
            var a=moving(3.9999f);var b=moving(4.0001f);
            assertEquals(ActorActionType.WALK,a.action);assertEquals(ActorActionType.RUN,b.action);
            var pa=ActorRouteGait.sample(a,profile,new ActorRuntime()).pose;
            var pb=ActorRouteGait.sample(b,profile,new ActorRuntime()).pose;
            assertEquals(pa.rightLegPitch,pb.rightLegPitch,.001f);
            assertEquals(pa.leftArmPitch,pb.leftArmPitch,.001f);assertEquals(pa.bodyPitch,pb.bodyPitch,.001f);
            var ja=new RigPose(ArticulatedPlayerRig.DEFINITION);var jb=new RigPose(ArticulatedPlayerRig.DEFINITION);
            ActorRouteGait.applyJoints(ja,a,profile,true,true);ActorRouteGait.applyJoints(jb,b,profile,true,true);
            for(String joint:List.of("left_shin","left_forearm"))
                assertTrue(ja.localTransform(joint).rotation().equals(jb.localTransform(joint).rotation(),.001f));
            assertEquals(ActorRouteGait.phaseCycles(a,profile),ActorRouteGait.phaseCycles(b,profile));
        }
    }
    @Test void explicitActionsAndStationarySemanticsKeepTheirAuthoredBehavior() {
        var sample=moving(4);sample.actionExplicit=true;sample.action=ActorActionType.WALK;sample.actionIntensity=.6f;
        var profile=new HumanoidRigProfile();
        var expected=profile.buildPose(sample.action,.6f,ActorRouteGait.time(sample,profile)).pose;
        assertEquals(expected.rightLegPitch,ActorRouteGait.sample(sample,profile,new ActorRuntime()).pose.rightLegPitch);
        sample.routeClip=null;sample.action=ActorActionType.RUN;sample.actionLocalTime=2;
        expected=profile.buildPose(sample.action,.6f,2).pose;
        assertEquals(expected.rightLegPitch,ActorRouteGait.sample(sample,profile,new ActorRuntime()).pose.rightLegPitch);
    }
    @Test void blendWindowIsBoundedAndRandomSeekingIsDeterministic() {
        assertEquals(0,ActorRouteGait.runWeight(3));assertEquals(.5f,ActorRouteGait.runWeight(4));assertEquals(1,ActorRouteGait.runWeight(5));
        var s=moving(4);var profile=new HumanoidRigProfile();
        float first=ActorRouteGait.sample(s,profile,new ActorRuntime()).pose.rightLegPitch;
        s.routeLocalTime=3;ActorRouteGait.sample(s,profile,new ActorRuntime());s.routeLocalTime=1;
        assertEquals(first,ActorRouteGait.sample(s,profile,new ActorRuntime()).pose.rightLegPitch);
    }
    @Test void cornerFacingIsContinuousWhileCurvePositionsStayExact() {
        var route=new ActorRouteClip("Corner",0,4);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(1,0,0),new Vector3f(1,0,1)));
        var a=ActorRouteSampler.facingAtDistance(route,.999f);var b=ActorRouteSampler.facingAtDistance(route,1.001f);
        assertTrue(a.angle(b)<.03f);assertTrue(a.x>0 && a.z>0);
        assertTrue(ActorRouteSampler.sampleAtDistance(route,.999f).position().equals(new Vector3f(.999f,0,0),1e-5f));
        assertTrue(ActorRouteSampler.sampleAtDistance(route,1.001f).position().equals(new Vector3f(1,0,.001f),1e-5f));
        assertTrue(ActorRouteSampler.facingAtDistance(route,0).equals(new Vector3f(1,0,0),1e-5f));
        assertTrue(ActorRouteSampler.facingAtDistance(route,2).equals(new Vector3f(0,0,1),1e-5f));
    }
    @Test void degenerateCornerFacingIsFiniteAndDeterministic() {
        var route=new ActorRouteClip("Degenerate",0,4);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(),new Vector3f(1,0,0),new Vector3f()));
        for(float distance:new float[]{0,.9f,1,1.1f,2}) {
            var a=ActorRouteSampler.facingAtDistance(route,distance);assertTrue(a.isFinite());
            assertEquals(a,ActorRouteSampler.facingAtDistance(route,distance));
        }
    }
}
