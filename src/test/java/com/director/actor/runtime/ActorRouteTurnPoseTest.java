package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorRouteTurnPoseTest {
    private EntitySample sample(float time) {
        var s=new EntitySample();var route=new ActorRouteClip("Turn",0,10);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(0,0,4)));
        route.setSpeed(2);route.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);route.setEndpointTurnDuration(.4f);
        s.routeClip=route;s.routeLocalTime=time;return s;
    }
    private boolean unchanged(RigPose pose,String bone) {
        return pose.localTransform(bone).toMatrix().equals(pose.rig().bone(pose.rig().requireBoneIndex(bone)).restLocal().toMatrix(),1e-6f);
    }
    @Test void liftsAlternateWithoutChangingRootHeadOrArms() {
        var first=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(first,sample(2.1f));
        assertFalse(unchanged(first,HumanoidRig.LEFT_LEG));assertTrue(unchanged(first,HumanoidRig.RIGHT_LEG));
        for(String bone:List.of(HumanoidRig.ROOT,HumanoidRig.HEAD,HumanoidRig.LEFT_ARM,HumanoidRig.RIGHT_ARM)) assertTrue(unchanged(first,bone));
        var second=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(second,sample(2.3f));
        assertTrue(unchanged(second,HumanoidRig.LEFT_LEG));assertFalse(unchanged(second,HumanoidRig.RIGHT_LEG));
    }
    @Test void travelAndBoundariesRemainNeutralAndSeekingIsDeterministic() {
        for(float t:new float[]{0,1,2,2.4f,3}) {
            var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(pose,sample(t));
            assertTrue(pose.authoredBones().isEmpty());
        }
        var a=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(a,sample(2.1f));
        ActorRouteTurnPose.apply(new RigPose(ArticulatedPlayerRig.DEFINITION),sample(7));
        var b=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(b,sample(2.1f));
        for(int i=0;i<a.rig().boneCount();i++) assertTrue(a.localTransform(i).toMatrix().equals(b.localTransform(i).toMatrix(),1e-6f));
    }
    @Test void respectsQuickOverridesAndDisabledFacingAndUnsupportedRig() {
        var s=sample(2.1f);s.poseOverrideEnabled=true;s.poseOverrideLegs=true;s.poseOverrideBody=true;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(pose,s);assertTrue(pose.authoredBones().isEmpty());
        s.poseOverrideEnabled=false;s.routeClip.setAlignRotationToRoute(false);ActorRouteTurnPose.apply(pose,s);assertTrue(pose.authoredBones().isEmpty());
        s.routeClip.setAlignRotationToRoute(true);var rigid=new RigPose(HumanoidRig.DEFINITION);ActorRouteTurnPose.apply(rigid,s);assertTrue(rigid.authoredBones().isEmpty());
        s.relationEnabled=true;ActorRouteTurnPose.apply(pose,s);assertTrue(pose.authoredBones().isEmpty());
    }
    @Test void authoredChannelsOverrideTurnWithoutErasingOtherProceduralBones() {
        var base=new RigPose(ArticulatedPlayerRig.DEFINITION);ActorRouteTurnPose.apply(base,sample(2.1f));
        var manual=new RigPose(base.rig());manual.setLocalTransform(base.rig().requireBoneIndex(HumanoidRig.LEFT_LEG),base.rig().bone(5).restLocal(),RigPose.ROTATION);
        var result=base.overlay(manual);assertTrue(unchanged(result,HumanoidRig.LEFT_LEG));assertFalse(unchanged(result,ArticulatedPlayerRig.LEFT_SHIN));
    }
}
