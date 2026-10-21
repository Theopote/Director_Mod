package com.director.actor.runtime;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ActorRouteGaitTest {
    private EntitySample moving() {
        var s=new EntitySample();s.action=ActorActionType.WALK;s.routeLocalTime=1;
        s.routeClip=new ActorRouteClip("Route",0,4);
        s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));return s;
    }
    private float phase(EntitySample sample,ActorRigProfile rig) {
        return ActorRouteGait.time(sample,rig)*rig.locomotionFrequency(sample.action,sample.actionIntensity);
    }
    @Test void everyProfileKeepsDistancePhaseWhenActionStartsOrIntensityChanges() {
        for(var rig:List.<ActorRigProfile>of(new HumanoidRigProfile(),new QuadrupedRigProfile())) {
            var s=moving();float expected=2/rig.locomotionStride()*(float)(2*Math.PI);
            assertEquals(expected,phase(s,rig),1e-5f);
            s.actionExplicit=true;s.actionLocalTime=0;s.action=ActorActionType.RUN;s.actionIntensity=.3f;
            assertEquals(expected,phase(s,rig),1e-5f);
            s.actionLocalTime=100;s.actionIntensity=.9f;rig.getStyle().tempo=1.3f;
            assertEquals(expected,phase(s,rig),1e-5f);
            s.scale.z=2;assertEquals(expected/2,phase(s,rig),1e-5f);
        }
    }
    @Test void smoothTravelUsesIntegratedDistanceAndStopsAtTheEndpoint() {
        var s=moving();s.routeClip.setSmoothTravel(true);var rig=new HumanoidRigProfile();
        assertEquals(1.25f,ActorRouteMotion.once(s.routeClip,1).distance(),1e-5f);
        assertEquals(1.25f/.8f,ActorRouteGait.phaseCycles(s,rig),1e-5f);
        assertEquals(0,ActorRouteMotion.once(s.routeClip,4).speed());
        s.routeLocalTime=40;assertEquals(8/.8f,ActorRouteGait.phaseCycles(s,rig),1e-5f);
    }
    @Test void loopAndPingPongUseCumulativeTravelAndIgnoreSamplingHistory() {
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            var s=moving();s.routeClip.setLoopMode(mode);s.routeClip.setSpeed(2);var rig=new HumanoidRigProfile();
            for(float t:new float[]{3.9f,4,4.1f,8.1f,0,4.1f}) {
                s.routeLocalTime=t;assertEquals(t*2/.8f,ActorRouteGait.phaseCycles(s,rig),1e-4f);
            }
        }
    }
    @Test void stationaryAndNonLocomotionActionsKeepTheirOwnClock() {
        var s=moving();var rig=new HumanoidRigProfile();
        s.actionExplicit=true;s.action=ActorActionType.DANCE;s.actionLocalTime=.7f;
        assertEquals(.7f,ActorRouteGait.time(s,rig));
        s.routeClip=null;s.action=ActorActionType.RUN;assertEquals(.7f,ActorRouteGait.time(s,rig));
        s.actionExplicit=false;s.motionLocalTime=3;assertEquals(3,ActorRouteGait.time(s,rig));
    }
    @Test void routeActionAndAnimationTimesRemainIndependentInSamplerAndCopies() {
        var track=new ActorTrack("test");track.addClip(new EntitySpawnClip("Spawn",0,20));
        var route=moving().routeClip;route.setTimeRange(2,10);track.addClip(route);
        var action=new ActorActionClip("Run",4,9);action.setActionType(ActorActionType.RUN);track.addClip(action);
        var animation=new EntityAnimationClip("Wave",3,8);track.addClip(animation);
        var value=new EntityClipSampler(track).sampleAt(5);
        assertEquals(3,value.routeLocalTime);assertEquals(3,value.motionLocalTime);
        assertEquals(1,value.actionLocalTime);assertEquals(2,value.animationLocalTime);
        assertEquals(3,value.copy().routeLocalTime);assertEquals(3,value.copy().motionLocalTime);
        var other=new EntityClipSampler(track).sampleAt(6);var blend=new EntitySample();blend.lerpFrom(value,other,.5f,com.director.timeline.model.TimelineInterpolation.LINEAR);
        assertEquals(3.5f,blend.routeLocalTime);assertEquals(1.5f,blend.actionLocalTime);
    }
}
