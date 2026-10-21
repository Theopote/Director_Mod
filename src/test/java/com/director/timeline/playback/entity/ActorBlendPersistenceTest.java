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
    @Test void fadesRoundTripOnBothClipTypesAndSamplesRemainIndependentOfSeekOrder() {
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.6f,RigPose.ROTATION,false,Set.of(HumanoidRig.HEAD),2,1);
        var spawn=new EntitySpawnClip("Spawn",10,16);spawn.setBoneAnimationId("project:test");spawn.setBlendSettings(settings);
        spawn=(EntitySpawnClip)roundTrip(spawn);assertEquals(settings,spawn.getBlendSettings());
        var track=new ActorTrack("actor");track.addClip(spawn);var sampler=new EntityClipSampler(track);
        float[][] cases={{10,0},{11,.5f},{12,1},{15.5f,.5f},{16,0},{11,.5f},{14,1},{10,0}};
        for(var c:cases) {
            var sample=sampler.sampleSnapshot(c[0]);
            assertEquals(c[1],sample.animationBlendEnvelope,1e-6f);
            assertEquals(settings,sample.animationBlendSettings);
            assertEquals(c[1],sample.copy().animationBlendEnvelope,1e-6f);
            assertEquals(c[1],sampler.sampleAt(c[0]).animationBlendEnvelope,1e-6f);
        }
        var animation=new EntityAnimationClip("Animation",10,16);animation.setAnimationId("project:explicit");
        animation.setBlendSettings(settings);animation.setLoop(true);animation.setSpeed(4);
        animation=(EntityAnimationClip)roundTrip(animation);assertEquals(settings,animation.getBlendSettings());
        track.removeClip(spawn.getId());track.addClip(new EntitySpawnClip("Spawn",0,20));track.addClip(animation);
        assertEquals(.5f,sampler.sampleSnapshot(11).animationBlendEnvelope,1e-6f);
        animation.setSpeed(.2f);animation.setLoop(false);
        assertEquals(.5f,sampler.sampleSnapshot(11).animationBlendEnvelope,1e-6f);
        animation.setTimeRange(20,26);track.getClips().getFirst().setTimeRange(0,30);
        assertEquals(.5f,sampler.sampleSnapshot(21).animationBlendEnvelope,1e-6f);
        assertEquals(1,sampler.sampleSnapshot(19).animationBlendEnvelope,1e-6f);
        assertEquals(2,track.getClips().size());
    }
    @Test void shorteningSpawnReevaluatesFadesUsingItsCurrentDuration() {
        var spawn=new EntitySpawnClip("Spawn",0,6);spawn.setBoneAnimationId("project:test");
        spawn.setBlendSettings(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,true,Set.of(),4,2));
        var track=new ActorTrack("actor");track.addClip(spawn);var sampler=new EntityClipSampler(track);
        assertEquals(.15625f,sampler.sampleSnapshot(1).animationBlendEnvelope,1e-6f);
        spawn.setTimeRange(0,3);
        assertEquals(.5f,sampler.sampleSnapshot(1).animationBlendEnvelope,1e-6f);
        assertEquals(1,sampler.sampleSnapshot(2).animationBlendEnvelope,1e-6f);
        assertEquals(0,sampler.sampleSnapshot(3).animationBlendEnvelope,1e-6f);
        assertFalse(sampler.sampleSnapshot(4).enabled);
    }
}
