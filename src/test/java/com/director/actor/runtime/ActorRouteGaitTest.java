package com.director.actor.runtime;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ActorRouteGaitTest {
    @Test void playerPhaseDoesNotJumpWhenIntensityOrWalkRunClassificationChanges() {
        var s = new EntitySample(); s.action = ActorActionType.WALK; s.actionLocalTime = 1;
        s.routeClip = new ActorRouteClip("Route",0,4); s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));
        var style = new com.director.actor.rig.RigProfileStyle();
        s.actionIntensity=.3f; float phase=ActorRouteGait.playerTime(s,style)*(2+s.actionIntensity)*style.tempo;
        s.actionIntensity=.8f; assertEquals(phase,ActorRouteGait.playerTime(s,style)*(2+s.actionIntensity)*style.tempo,1e-5f);
        s.action=ActorActionType.RUN; assertEquals(phase,ActorRouteGait.playerTime(s,style)*(2+s.actionIntensity)*style.tempo*2,1e-5f);
    }
    @Test void smoothTravelUsesDistanceIntegralInsteadOfCurrentSpeedTimesTime() {
        var s = new EntitySample(); s.action = ActorActionType.WALK; s.actionLocalTime = 1;
        s.routeClip = new ActorRouteClip("Smooth", 0, 4); s.routeClip.setSmoothTravel(true);
        s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));
        var motion = com.director.timeline.playback.entity.ActorRouteMotion.once(s.routeClip, 1);
        assertEquals(1.25f, motion.distance(), 1e-5f); assertEquals(2.25f, motion.speed(), 1e-5f);
        assertEquals(.625f, ActorRouteGait.time(s), 1e-5f);
        assertEquals(0, com.director.timeline.playback.entity.ActorRouteMotion.once(s.routeClip, 0).speed());
        assertEquals(0, com.director.timeline.playback.entity.ActorRouteMotion.once(s.routeClip, 4).speed());
        assertEquals(8, com.director.timeline.playback.entity.ActorRouteMotion.once(s.routeClip, 4).distance());
    }
    @Test void fasterRouteAdvancesCadenceAndExplicitActionsRemainUnchanged() {
        var s = new EntitySample(); s.action = ActorActionType.WALK; s.actionLocalTime = 1;
        s.routeClip = new ActorRouteClip("Route", 0, 4);
        s.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));
        assertEquals(1, ActorRouteGait.time(s));
        s.routeClip.setTimeRange(0,2); assertEquals(2, ActorRouteGait.time(s));
        s.actionExplicit = true; assertEquals(1, ActorRouteGait.time(s));
        s.actionExplicit = false; s.actionLocalTime = .25f; assertEquals(.5f, ActorRouteGait.time(s));
        s.actionLocalTime = 1; assertEquals(2, ActorRouteGait.time(s));
    }
}
