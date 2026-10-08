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
    @AfterEach void cleanup() { ActorRegistryManager.setRegistry(null); com.director.ui.timeline.commands.TimelineCommandBridge.bind(null); }
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
    @Test void additiveSelectionDeletesOneGroupAndToggleDoesNotDeleteDeselectedKey() {
        var registry = new ActorRegistry(); ActorRegistryManager.setRegistry(registry);
        var actor = registry.createActor("A", ActorType.PLAYER);
        var model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("A", TrackType.ACTOR);
        track.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(track);
        var clip = new EntitySpawnClip("A", 0, 5); track.getActorTrackPayload().addClip(clip);
        var wrapper = new EntityTimelineClip(clip, 0); track.addClip(wrapper); model.setSelectedClip(wrapper);
        var a = new EntityClipKeyframe(1); var b = new EntityClipKeyframe(2); clip.addKeyframe(a); clip.addKeyframe(b);
        var history = new com.director.ui.timeline.commands.TimelineCommandHistory();
        com.director.ui.timeline.commands.TimelineCommandBridge.bind(history);
        var rows = new ActorRootTimelineRows(); rows.focus(model, track, clip, a);
        rows.select(model, track, clip, b, true);
        assertTrue(rows.handleKeyCommand(model, "delete")); assertTrue(clip.getKeyframes().isEmpty());
        assertEquals(1, history.getUndoSize()); assertTrue(history.undo());
        rows.select(model, track, clip, b, true);
        assertTrue(rows.handleKeyCommand(model, "delete")); assertEquals(java.util.List.of(b), clip.getKeyframes());
        assertEquals(1, track.getClips().size());
    }
    @Test void focusedRootDeleteCannotFallThroughToItsClipEvenWhenLockedOrAlreadyDeleted() {
        var registry = new ActorRegistry(); ActorRegistryManager.setRegistry(registry);
        var actor = registry.createActor("A", ActorType.PLAYER);
        var model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("A", TrackType.ACTOR);
        track.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(track);
        var spawn = new EntitySpawnClip("A", 0, 5); track.getActorTrackPayload().addClip(spawn);
        var wrapper = new EntityTimelineClip(spawn, 0); track.addClip(wrapper);
        var key = new EntityClipKeyframe(2); spawn.addKeyframe(key);
        model.setSelectedClip(wrapper); model.setCurrentTime(2);
        var history = new com.director.ui.timeline.commands.TimelineCommandHistory();
        com.director.ui.timeline.commands.TimelineCommandBridge.bind(history);
        var rows = new ActorRootTimelineRows(); rows.focus(model, track, spawn, key);
        track.setLocked(true); assertTrue(rows.handleKeyCommand(model, "delete"));
        assertEquals(1, spawn.getKeyframes().size()); assertEquals(0, history.getUndoSize());
        track.setLocked(false); assertTrue(rows.handleKeyCommand(model, "delete"));
        assertTrue(spawn.getKeyframes().isEmpty()); assertEquals(1, track.getClips().size());
        assertTrue(rows.handleKeyCommand(model, "delete")); assertEquals(1, history.getUndoSize());
        assertTrue(history.undo()); assertEquals(java.util.List.of(key), spawn.getKeyframes());
        assertTrue(rows.handleKeyCommand(model, "copy"));
        key.setPosition(8, 0, 0);
        model.setCurrentTime(3); assertTrue(rows.handleKeyCommand(model, "paste"));
        assertEquals(2, spawn.getKeyframes().size());
        var pasted = spawn.getKeyframes().getLast(); assertEquals(0, pasted.getPosition().x);
        assertTrue(rows.handleKeyCommand(model, "cut")); assertEquals(java.util.List.of(key), spawn.getKeyframes());
        assertTrue(history.undo()); assertTrue(spawn.getKeyframes().contains(pasted));
        track.setLocked(true); assertTrue(rows.handleKeyCommand(model, "cut"));
        assertTrue(spawn.getKeyframes().contains(pasted));
        rows.clearFocus(); assertFalse(rows.handleKeyCommand(model, "delete"));
    }

}
