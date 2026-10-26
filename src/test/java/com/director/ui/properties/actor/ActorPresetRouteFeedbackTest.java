package com.director.ui.properties.actor;

import com.director.actor.animation.AnimationData;
import com.director.actor.animation.asset.*;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetRouteFeedbackTest {
    @Test void warningUsesResolvedClockAndOnlyActualVisibleLegRotation() {
        var library=AnimationAssetLibrary.getInstance();
        var asset=new AnimationAsset("test:route-warning","Gait",ArticulatedPlayerRig.DEFINITION,2,AnimationLoopMode.LOOP);
        asset.getOrCreateBoneTrack("left_leg").rotation().put(0,new Quaternionf(),InterpolationMode.LINEAR);library.register(asset);
        try {
            var sample=new EntitySample();sample.enabled=true;sample.active=true;sample.visibility=1;
            sample.action=ActorActionType.WALK;sample.speed=1;sample.routeLocalTime=1;
            sample.routeClip=new ActorRouteClip("Route",0,4);
            sample.routeClip.setWaypoints(List.of(new Vector3f(),new Vector3f(4,0,0)));
            var binding=new ActorPresetBinding(asset.id(),true,1,PoseBlendSettings.DEFAULT);
            assertEquals(ActorPresetRouteFeedback.LOCAL_CLOCK,state(binding,asset,sample,false));
            asset.setPurpose(AnimationData.Purpose.LOCOMOTION);
            assertEquals(ActorPresetRouteFeedback.DISTANCE_CLOCK,state(binding,asset,sample,false));
            sample.actionExplicit=true;sample.action=ActorActionType.ATTACK;
            assertEquals(ActorPresetRouteFeedback.LOCAL_CLOCK,state(binding,asset,sample,false));
            assertEquals(ActorPresetRouteFeedback.NONE,state(binding,asset,sample,true));
            assertEquals(ActorPresetRouteFeedback.NONE,state(ActorPresetScope.UPPER.apply(binding,asset.rig()),asset,sample,false));
            sample.visibility=0;assertEquals(ActorPresetRouteFeedback.NONE,state(binding,asset,sample,false));
        } finally {library.remove(asset.id());}
    }
    private ActorPresetRouteFeedback state(ActorPresetBinding binding,AnimationAsset asset,EntitySample sample,boolean muted) {
        var feedback=ActorPresetFeedback.resolve(binding,asset.rig(),1,4,sample,new HumanoidRigProfile());
        return ActorPresetRouteFeedback.resolve(feedback,sample,muted);
    }
    @Test void scopeMatchingDoesNotAcceptUnknownBonesAsAnExactGroup() {
        var blend=PoseScope.UPPER.apply(PoseBlendSettings.DEFAULT,ArticulatedPlayerRig.DEFINITION);
        assertTrue(PoseScope.UPPER.matches(blend,ArticulatedPlayerRig.DEFINITION));
        var extra=new HashSet<>(blend.boneIds());extra.add("unknown");
        var custom=new PoseBlendSettings(blend.mode(),blend.weight(),blend.channels(),false,extra);
        assertFalse(PoseScope.UPPER.matches(custom,ArticulatedPlayerRig.DEFINITION));
        assertFalse(PoseScope.LOWER.matches(blend,ArticulatedPlayerRig.DEFINITION));
    }
}
