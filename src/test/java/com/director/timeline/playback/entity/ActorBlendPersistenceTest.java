package com.director.timeline.playback.entity;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBlendPersistenceTest {
    private static PoseBlendSettings settings() {
        return new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.35f,RigPose.ROTATION,false,Set.of(HumanoidRig.HEAD));
    }
    private static EntityClip roundTrip(EntityClip clip) {
        var gson=new Gson();
        var dto=gson.fromJson(gson.toJson(TimelineModelPersistence.entityClipToDTO(clip)),TimelineModelPersistence.EntityClipDTO.class);
        return TimelineModelPersistence.entityClipFromDTO(dto);
    }
    @Test void spawnReloadRetainsOwnedReferenceAndPlaybackSettingsWithoutExtraClips() {
        var spawn=new EntitySpawnClip("Spawn",2,8);spawn.setBoneAnimationId("project:test");spawn.setBlendSettings(settings());
        var restored=(EntitySpawnClip)roundTrip(spawn);
        assertEquals(settings(),restored.getBlendSettings());assertEquals("project:test",restored.getBoneAnimationId());
        assertEquals(settings(),restored.getBoneAnimation().getBlendSettings());
        var track=new ActorTrack("actor");track.addClip(restored);
        var sample=new EntityClipSampler(track).sampleSnapshot(4);
        assertEquals(settings(),sample.animationBlendSettings);assertEquals(2,sample.animationLocalTime);
        assertEquals(settings(),sample.copy().animationBlendSettings);assertEquals(1,track.getClips().size());
        var replacement=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,7,true,Set.of());
        restored.setBlendSettings(replacement);
        assertEquals(replacement,new EntityClipSampler(track).sampleSnapshot(4).animationBlendSettings);
    }
    @Test void explicitAnimationRoundTripAndSamplerUseSameSettings() {
        var animation=new EntityAnimationClip("Animation",0,8);animation.setAnimationId("project:test");animation.setBlendSettings(settings());
        var restored=(EntityAnimationClip)roundTrip(animation);assertEquals(settings(),restored.getBlendSettings());
        var track=new ActorTrack("actor");track.addClip(new EntitySpawnClip("Spawn",0,8));track.addClip(restored);
        assertEquals(settings(),new EntityClipSampler(track).sampleSnapshot(3).animationBlendSettings);
        assertEquals(PoseBlendSettings.DEFAULT,new EntityClipSampler(track).sampleSnapshot(10).animationBlendSettings);
    }
}
