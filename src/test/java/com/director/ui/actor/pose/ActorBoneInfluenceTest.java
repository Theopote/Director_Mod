package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBoneInfluenceTest {
    private AnimationAsset asset() {
        var asset=new AnimationAsset("test:influence","Influence",ArticulatedPlayerRig.DEFINITION,4,AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack("head").rotation().put(0,new Quaternionf().rotateY(.4f),InterpolationMode.LINEAR);
        return asset;
    }
    @Test void keyedAndUnkeyedChannelsRemainDistinctWithoutCreatingTracks() {
        var asset=asset();
        var rotation=ActorBoneInfluence.evaluate(asset,PoseBlendSettings.DEFAULT,"head",RigPose.ROTATION,1,4);
        assertEquals(ActorBoneInfluence.Reason.ACTIVE,rotation.reason());assertTrue(rotation.hasKeys());assertEquals(1,rotation.configuredWeight());
        var scale=ActorBoneInfluence.evaluate(asset,PoseBlendSettings.DEFAULT,"head",RigPose.SCALE,1,4);
        assertEquals(ActorBoneInfluence.Reason.NO_KEYS,scale.reason());assertFalse(scale.hasKeys());assertEquals(1,scale.configuredWeight());
        ActorBoneInfluence.evaluate(asset,PoseBlendSettings.DEFAULT,"left_leg",RigPose.ROTATION,1,4);
        assertEquals(1,asset.boneTracks().size());assertTrue(asset.findBoneTrack("head").orElseThrow().scale().isEmpty());
    }
    @Test void exclusionAndZeroWeightExplainWhyKeyedChannelsHaveNoInfluence() {
        var asset=asset();
        var excluded=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,Set.of("left_leg"));
        var state=ActorBoneInfluence.evaluate(asset,excluded,"head",RigPose.ROTATION,1,4);
        assertTrue(state.hasKeys());assertEquals(ActorBoneInfluence.Reason.BONE_EXCLUDED,state.reason());assertEquals(0,state.configuredWeight());
        excluded=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,RigPose.SCALE,true,Set.of());
        assertEquals(ActorBoneInfluence.Reason.CHANNEL_EXCLUDED,ActorBoneInfluence.evaluate(asset,excluded,"head",RigPose.ROTATION,1,4).reason());
        excluded=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,0,7,true,Set.of());
        assertEquals(ActorBoneInfluence.Reason.ZERO_WEIGHT,ActorBoneInfluence.evaluate(asset,excluded,"head",RigPose.ROTATION,1,4).reason());
    }
    @Test void fadeReadoutMatchesPlaybackAndNullAssetDoesNotPromiseExistingKeys() {
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.8f,7,true,Set.of(),2,1);
        assertEquals(ActorBoneInfluence.Reason.FADE_ZERO,ActorBoneInfluence.evaluate(asset(),settings,"head",2,0,4).reason());
        assertEquals(.4f,ActorBoneInfluence.evaluate(asset(),settings,"head",2,1,4).configuredWeight(),1e-6f);
        assertEquals(ActorBoneInfluence.Reason.NO_KEYS,ActorBoneInfluence.evaluate(null,settings,"head",2,1,4).reason());
        assertThrows(IllegalArgumentException.class,()->ActorBoneInfluence.evaluate(asset(),settings,"head",7,1,4));
    }
    @Test void locatingEmbeddedReferenceReturnsOriginalSpawnWithoutCreatingVisibleClips() {
        var track=new TimelineTrack("Actor",TrackType.ACTOR);var payload=track.getOrCreateActorTrackPayload();
        var spawn=new EntitySpawnClip("Spawn",0,4);payload.addClip(spawn);
        var wrapper=new EntityTimelineClip(spawn,0);track.addClip(wrapper);spawn.setBoneAnimationId("test:influence");
        assertSame(wrapper,ActorEmbeddedAnimation.visibleOwner(track,spawn.getBoneAnimation()));
        assertEquals(1,track.getClips().size());assertEquals(1,payload.getClips().size());
        var old=spawn.getBoneAnimation();spawn.setBoneAnimationId("test:new");
        assertNull(ActorEmbeddedAnimation.visibleOwner(track,old));
        assertSame(wrapper,ActorEmbeddedAnimation.visibleOwner(track,spawn.getBoneAnimation()));
    }
    @Test void locatingExplicitReferenceRejectsDetachedOrOtherTrackTargets() {
        var track=new TimelineTrack("Actor",TrackType.ACTOR);var payload=track.getOrCreateActorTrackPayload();
        var animation=new EntityAnimationClip("Animation",0,4);payload.addClip(animation);
        var wrapper=new EntityTimelineClip(animation,0);track.addClip(wrapper);
        assertSame(wrapper,ActorEmbeddedAnimation.visibleOwner(track,animation));
        assertNull(ActorEmbeddedAnimation.visibleOwner(new TimelineTrack("Other",TrackType.ACTOR),animation));
        payload.removeClip(animation.getId());assertNull(ActorEmbeddedAnimation.visibleOwner(track,animation));
    }
}
