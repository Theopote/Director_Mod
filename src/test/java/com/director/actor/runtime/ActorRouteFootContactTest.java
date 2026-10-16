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
    @Test void articulatedStanceCorrectionRunsAndKeepsJointRotation() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var sample=sample(.2f);
        int knee=pose.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_SHIN);
        var rotation=new org.joml.Quaternionf().rotateX(.2f);
        pose.setLocalTransform(knee,pose.localTransform(knee).withRotation(rotation),RigPose.ROTATION);
        var before=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(sample.position);
        ActorRouteFootContact.apply(pose,sample);
        var after=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(sample.position);
        assertTrue(after.distance(new Vector3f(.125f,0,.2f)) < before.distance(new Vector3f(.125f,0,.2f)));
        assertTrue(pose.localTransform(knee).rotation().equals(rotation,1e-6f));
        var replay=new RigPose(ArticulatedPlayerRig.DEFINITION);
        replay.setLocalTransform(knee,replay.localTransform(knee).withRotation(rotation),RigPose.ROTATION);
        ActorRouteFootContact.apply(replay,sample);
        assertEquals(pose.localTransform(HumanoidRig.LEFT_LEG).translation(),replay.localTransform(HumanoidRig.LEFT_LEG).translation());
    }
    @Test void keyedLegIsUntouchedWhileOtherLegCanReceiveContactCorrection() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var left=pose.localTransform(HumanoidRig.LEFT_LEG).translation();
        var right=pose.localTransform(HumanoidRig.RIGHT_LEG).translation();
        ActorRouteFootContact.apply(pose,sample(.56f),java.util.Set.of(ArticulatedPlayerRig.LEFT_SHIN));
        assertEquals(left,pose.localTransform(HumanoidRig.LEFT_LEG).translation());
        assertNotEquals(right,pose.localTransform(HumanoidRig.RIGHT_LEG).translation());
    }
}
