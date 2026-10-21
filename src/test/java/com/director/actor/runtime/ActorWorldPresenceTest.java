package com.director.actor.runtime;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorWorldPresenceTest {
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
