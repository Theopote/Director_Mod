package com.director.ui.timeline.render;

import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTransformClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.EntityClipKeyframe;
import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorRootTimelineRowsTest {
    @AfterEach void cleanup() { ActorRegistryManager.setRegistry(null); }
    @Test void nonHumanoidsGetRootRowsWithoutInventedBonesOrTracks() {
        var registry = new ActorRegistry(); ActorRegistryManager.setRegistry(registry);
        var model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        var cow = registry.createActor("Cow", ActorType.COW);
        var player = registry.createActor("Player", ActorType.PLAYER);
        var a = new TimelineTrack("Cow", TrackType.ACTOR); a.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(cow));
        var b = new TimelineTrack("Player", TrackType.ACTOR); b.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(player));
        model.addTrack(a); model.addTrack(b);
        var rows = new ActorBoneTimelineRows(); rows.setExpanded(a, true); rows.setExpanded(b, true);
        var layout = rows.prepare(model, 30, 4);
        assertTrue(rows.supports(a)); assertTrue(rows.supports(b));
        assertEquals(30 + 4 + 3 * 24, layout.trackTop(1));
        assertEquals(2 * 30 + 4 + (6 + HumanoidRig.DEFINITION.boneCount()) * 24, layout.totalHeight());
        assertEquals(2, model.getAllTracks().size());
        rows.setExpanded(a, false);
        assertEquals(34, rows.prepare(model, 30, 4).trackTop(1));
        registry.removeActor(cow.getActorId()); rows.prepare(model, 30, 4); assertFalse(rows.supports(a));
    }
    @Test void rootKeyHighlightRequiresExactReferenceAndSceneTime() {
        var model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("Actor", TrackType.ACTOR); model.addTrack(track);
        var spawn = new EntitySpawnClip("Actor", 0, 5); var transform = new EntityTransformClip("Move", 0, 5);
        var a = new EntityTimelineClip(spawn, 0); var b = new EntityTimelineClip(transform, 0);
        track.addClip(a); track.addClip(b);
        var key = new EntityClipKeyframe(2); spawn.addKeyframe(key);
        model.setSelectedClip(a); model.setCurrentTime(2);
        assertTrue(ActorRootTimelineRows.selectedAt(model, spawn, key));
        assertFalse(ActorRootTimelineRows.selectedAt(model, transform, key));
        model.setCurrentTime(3); assertFalse(ActorRootTimelineRows.selectedAt(model, spawn, key));
    }
}
