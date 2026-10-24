package com.director.actor.runtime;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorWorldPresenceTest {
    @Test void lifecycleWindowAndCompletedRemovalAgreeWithSamplerAfterSeeking() {
        var track=new ActorTrack();track.addDefaultSpawnClip(3);
        assertEquals(5,track.getClips().getFirst().getLength());
        var remove=new EntityDespawnClip("Remove",5,6);remove.setMode(EntityDespawnClip.DespawnMode.REMOVE);track.addClip(remove);
        var sampler=new EntityClipSampler(track);
        for(float time:new float[]{2,3,5.5f,6,7,9,4})
            assertEquals(track.isPresentAt(time),ActorRuntimeController.previewActive(sampler.sampleSnapshot(time)),"time="+time);
        assertFalse(track.isPresentAt(7));
        remove.setMuted(true);assertTrue(track.isPresentAt(7));
    }
    @Test void wrapperMovementInvalidatesSortedClipCacheAndSpawnReadsArePure() {
        var track=new ActorTrack();var a=new EntitySpawnClip("A",3,8);var b=new EntitySpawnClip("B",10,15);
        track.addClip(a);track.addClip(b);track.getClips();
        var wrapper=new EntityTimelineClip(b,0);wrapper.setTimeRange(1,6);
        assertTrue(track.findClipsAt(2).contains(b));
        b.setBoneAnimationId("test:keys");var animation=b.getBoneAnimation();
        var childWrapper=animation.getTimelineClip();
        animation.setSpeed(2);
        for(int i=0;i<3;i++) {assertSame(animation,b.getBoneAnimation());assertEquals(2,animation.getSpeed());}
        wrapper.setTimeRange(4,9);assertEquals(4,animation.getStartTime());assertEquals(9,animation.getEndTime());
        assertSame(childWrapper,animation.getTimelineClip());
        b.setMuted(true);assertTrue(animation.isMuted());
    }

    @Test void rebuildingAtPausedTimeRequiresEnabledPresenceAndCannotRespawnOutsideClip() {
        var track=new ActorTrack();track.addClip(new EntitySpawnClip("Spawn",3,8));
        var sampler=new EntityClipSampler(track);
        assertFalse(ActorRuntimeController.previewActive(sampler.sampleSnapshot(2)));
        assertTrue(ActorRuntimeController.previewActive(sampler.sampleSnapshot(5)));
        assertFalse(ActorRuntimeController.previewActive(sampler.sampleSnapshot(9)));
        assertTrue(ActorRuntimeController.previewActive(sampler.sampleSnapshot(5)));
        assertFalse(ActorRuntimeController.previewActive(EntitySample.disabled()));
        assertFalse(ActorRuntimeController.previewActive(null));
    }
    @Test void runtimeResetReleasesFrameStateWithoutResettingEventTriggerCache() throws Exception {
        var owner=new com.director.timeline.model.TimelineTrack("Actor",com.director.timeline.model.TrackType.ACTOR);
        var payload=owner.getOrCreateActorTrackPayload();payload.addClip(new EntitySpawnClip("Spawn",0,5));
        payload.addClip(new EntityActionClip("Event",0,5));
        var type=Class.forName("com.director.actor.runtime.ActorRuntimeController$TrackRuntime");
        var ctor=type.getDeclaredConstructor(ActorRuntimeController.class,com.director.timeline.model.TimelineTrack.class);ctor.setAccessible(true);
        var runtime=ctor.newInstance(ActorRuntimeController.getInstance(),owner);
        var samplerField=type.getDeclaredField("sampler");samplerField.setAccessible(true);
        var sampler=(EntityClipSampler)samplerField.get(runtime);
        assertFalse(sampler.sampleAt(2).actionEvents.isEmpty());
        for(var entry:java.util.Map.<String,Object>of("spawned",true,"lastSample",new EntitySample(),"lastTime",2f,"groundRevision",123L).entrySet()) {
            var field=type.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(runtime,entry.getValue());
        }
        var reset=type.getDeclaredMethod("resetWorldResources");reset.setAccessible(true);reset.invoke(runtime);
        var spawned=type.getDeclaredField("spawned");spawned.setAccessible(true);assertFalse(spawned.getBoolean(runtime));
        var sample=type.getDeclaredField("lastSample");sample.setAccessible(true);assertNull(sample.get(runtime));
        var time=type.getDeclaredField("lastTime");time.setAccessible(true);assertEquals(-1f,time.getFloat(runtime));
        var revision=type.getDeclaredField("groundRevision");revision.setAccessible(true);assertEquals(-1L,revision.getLong(runtime));
        assertTrue(sampler.sampleAt(2).actionEvents.isEmpty());assertTrue(sampler.sampleSnapshot(2).active);
    }
}
