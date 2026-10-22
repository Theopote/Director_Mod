package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetClockTest {
    private EntitySample moving() {
        var sample=new EntitySample();sample.action=ActorActionType.WALK;sample.routeLocalTime=1;
        sample.routeClip=new ActorRouteClip("Route",0,4);
        sample.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));
        return sample;
    }
    private ActorAnimationLayerSample input(String id) {
        return new ActorAnimationLayerSample(id,19,false,3,PoseBlendSettings.DEFAULT,.4f);
    }
    @Test void selectionDefaultsFollowPurposeWithoutInheritingOldLoopFlags() {
        assertEquals(AnimationData.Purpose.LOCOMOTION,AnimationLibrary.getInstance().get("walk").orElseThrow().purpose());
        var walk=ActorPresetBinding.select("walk",null);assertTrue(walk.loop());
        var wave=ActorPresetBinding.select("wave",walk);assertFalse(wave.loop());
        var attack=ActorPresetBinding.select("attack",walk);assertFalse(attack.loop());
        assertTrue(ActorPresetBinding.select("idle",attack).loop());
        assertEquals(walk.blend(),wave.blend());assertEquals(walk.speed(),wave.speed());
        assertNull(ActorPresetBinding.select(null,wave));
    }
    @Test void locomotionDistanceClockIgnoresPerformanceTimeAndRetainsBlendEnvelope() {
        var sample=moving();var profile=new HumanoidRigProfile();var input=input("walk");
        var resolved=ActorPresetClock.resolve(input,sample,profile);
        float expected=ActorRouteGait.phaseCycles(sample,profile)%1;
        assertEquals(expected,resolved.localTime(),1e-6f);assertTrue(resolved.loop());assertEquals(1,resolved.speed());
        assertEquals(.4f,resolved.envelope());assertSame(input.settings(),resolved.settings());
        sample.actionExplicit=true;sample.actionLocalTime=100;sample.motionLocalTime=2;sample.action=ActorActionType.RUN;
        assertEquals(resolved,ActorPresetClock.resolve(input,sample,profile));
        assertEquals(19,input.localTime());assertEquals(3,input.speed());assertFalse(input.loop());
    }
    @Test void performanceStationaryAndExplicitNonLocomotionKeepClipClock() {
        var sample=moving();var profile=new HumanoidRigProfile();var wave=input("wave");
        assertSame(wave,ActorPresetClock.resolve(wave,sample,profile));
        var walk=input("walk");sample.action=ActorActionType.IDLE;sample.actionExplicit=true;
        assertSame(walk,ActorPresetClock.resolve(walk,sample,profile));
        sample.action=ActorActionType.WALK;sample.routeClip=null;
        assertSame(walk,ActorPresetClock.resolve(walk,sample,profile));
        assertFalse(ActorPresetClock.followsDistance("walk",null));
    }
    @Test void smoothLoopAndPingPongUseDeterministicCumulativeTravel() {
        var sample=moving();sample.routeClip.setSmoothTravel(true);var profile=new HumanoidRigProfile();
        float expected=ActorRouteMotion.once(sample.routeClip,1).distance()/profile.locomotionStride();
        assertEquals(expected%1,ActorPresetClock.resolve(input("walk"),sample,profile).localTime(),1e-5f);
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            sample.routeClip.setLoopMode(mode);sample.routeClip.setSpeed(2);
            for(float time:new float[]{4.1f,1,9,0,4.1f}) {
                sample.routeLocalTime=time;
                assertEquals((time*2/profile.locomotionStride())%1,
                        ActorPresetClock.resolve(input("walk"),sample,profile).localTime(),1e-4f);
            }
        }
    }
    @Test void automaticallyIdleRouteEndpointsNeverSwitchBackToClipTime() {
        var sample=moving();var profile=new HumanoidRigProfile();var input=input("walk");
        sample.action=ActorActionType.IDLE;sample.routeLocalTime=0;
        assertEquals(0,ActorPresetClock.resolve(input,sample,profile).localTime());
        sample.routeLocalTime=4;
        float cycles=ActorRouteGait.phaseCycles(sample,profile);
        assertEquals(cycles%1,ActorPresetClock.resolve(input,sample,profile).localTime(),1e-5f);
        sample.actionExplicit=true;assertSame(input,ActorPresetClock.resolve(input,sample,profile));
    }

    @Test void walkCycleStartsNeutralAndMatchesProceduralLegPhase() {
        var profile=new HumanoidRigProfile();var sample=moving();
        sample.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(profile.locomotionStride(),0,0)));
        for(float time:new float[]{0,1,2,3,4}) {
            sample.routeLocalTime=time;var clock=ActorPresetClock.resolve(input("walk"),sample,profile);
            var pose=AnimationDataSampler.sample(clock.id(),clock.localTime(),clock.loop(),clock.speed()).pose;
            float expected=(float)Math.sin(2*Math.PI*time/4);
            assertEquals(expected*.65f,pose.rightLegPitch,1e-5f);
            assertEquals(-expected*.65f,pose.leftLegPitch,1e-5f);
        }
    }
    @Test void authoredAssetsWithSameIdAreNeverReinterpretedAsBuiltInGait() {
        var library=AnimationAssetLibrary.getInstance();var before=library.find("walk").orElse(null);
        library.register(new AnimationAsset("walk","Authored",ArticulatedPlayerRig.DEFINITION,1,AnimationLoopMode.ONCE));
        try { var input=input("walk");assertSame(input,ActorPresetClock.resolve(input,moving(),new HumanoidRigProfile())); }
        finally { library.remove("walk");if(before!=null) library.register(before); }
    }
}
