package com.director.actor.runtime;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ActorRouteGaitTest {
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
