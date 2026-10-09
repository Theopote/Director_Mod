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
        s.speed=1; s.actionLocalTime=time; s.position.set(0,0,time); return s;
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
}
