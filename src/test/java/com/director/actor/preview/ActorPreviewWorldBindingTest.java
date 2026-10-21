package com.director.actor.preview;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import com.director.ui.actor.edit.ActorGestureContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ActorPreviewWorldBindingTest {
    private record World(String dimension) {}
    @Test void sameInstanceKeepsResourcesButReloadOfSameDimensionReleasesThem() {
        var resources=new HashMap<Integer,String>();var calls=new AtomicInteger();
        var binding=new ActorPreviewWorldBinding<World>(()->{resources.clear();calls.incrementAndGet();});
        var first=new World("overworld");assertTrue(binding.bind(first));
        resources.put(7,"old actor");long revision=binding.revision();
        assertFalse(binding.bind(first));assertEquals("old actor",resources.get(7));assertEquals(revision,binding.revision());
        var reloaded=new World("overworld");assertEquals(first,reloaded);assertTrue(binding.bind(reloaded));
        assertTrue(resources.isEmpty());assertTrue(binding.revision()>revision);assertSame(reloaded,binding.world());
        assertEquals(2,calls.get());
    }
    @Test void unloadAndDisconnectClearOldPickingIdsBeforeTheyCanBeReused() {
        var picking=new HashMap<Integer,UUID>();var binding=new ActorPreviewWorldBinding<World>(picking::clear);
        var first=new World("overworld");binding.bind(first);picking.put(42,UUID.randomUUID());
        assertTrue(binding.bind(null));assertTrue(picking.isEmpty());assertNull(binding.world());
        assertFalse(binding.bind(null));binding.bind(new World("nether"));var current=UUID.randomUUID();picking.put(42,current);
        assertEquals(current,picking.get(42));binding.disconnect();assertNull(binding.world());assertTrue(picking.isEmpty());
        assertTrue(binding.bind(first));assertTrue(picking.isEmpty());
    }
    @Test void worldChangeInvalidatesDraftsWithoutChangingTimelineOrConsumingActionEvents() {
        var model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(2);
        var payload=new ActorTrack();payload.addClip(new EntitySpawnClip("Spawn",0,5));
        payload.addClip(new EntityActionClip("Event",0,5));var sampler=new EntityClipSampler(payload);
        assertFalse(sampler.sampleAt(2).actionEvents.isEmpty());
        var resources=new HashSet<String>();
        var binding=new ActorPreviewWorldBinding<World>(()->{resources.clear();model.invalidateActorEditContext();});
        binding.bind(new World("overworld"));resources.add("old preview");
        var gesture=new ActorGestureContext(model);long revision=model.getActorEditContextRevision();
        assertTrue(gesture.isCurrent(model));
        binding.bind(new World("nether"));assertFalse(gesture.isCurrent(model));
        assertTrue(model.getActorEditContextRevision()>revision);assertEquals(2,model.getCurrentTime());
        assertEquals(2,payload.getClipCount());assertTrue(resources.isEmpty());
        assertTrue(sampler.sampleAt(2).actionEvents.isEmpty());assertTrue(sampler.sampleSnapshot(2).active);
    }
}
