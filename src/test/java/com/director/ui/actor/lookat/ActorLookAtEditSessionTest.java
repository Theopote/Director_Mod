package com.director.ui.actor.lookat;

import com.director.ui.actor.edit.ActorOwnedIntervalEditing;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorLookAtEditSessionTest {
    @Test void editingUsesStrongestOwnedLookTarget() {
        var strong = new EntityLookAtClip("Strong", 0, 5);
        strong.setStrength(.9f);
        strong.setWorldPoint(10, 0, 0);
        var weak = new EntityLookAtClip("Weak", 1, 5);
        weak.setStrength(.2f);
        spawn.addLookTarget(strong);
        spawn.addLookTarget(weak);
        var session = new ActorLookAtEditSession(model, track);
        assertSame(strong, session.source());
        session.draft().setWorldPoint(12, 0, 0);
        assertSame(strong, session.commit());
        assertTrue(history.undo());
        assertEquals(10f, strong.getWorldPoint().x);
        strong.setMuted(true);
        assertSame(weak, new ActorLookAtEditSession(model, track).source());
    }

    @Test void cannotCreateLookTargetInAnEmptyTrackOrGap() {
        model.setCurrentTime(6);
        assertThrows(IllegalArgumentException.class, () -> new ActorLookAtEditSession(model, track));
        track.removeClip(spawn.getTimelineClip());
        track.getActorTrackPayload().removeClip(spawn.getId());
        assertThrows(IllegalArgumentException.class, () -> new ActorLookAtEditSession(model, track));
        assertTrue(track.getClips().isEmpty());
        assertEquals(0, history.getUndoSize());
    }

    @Test void pendingCreationWritesNothingAndScrubbingCancelsIt() {
        var session=new ActorLookAtEditSession(model,track);
        assertNull(session.source()); assertTrue(spawn.getLookTargets().isEmpty());
        model.setScrubbing(true); assertNull(session.commit());
        assertTrue(spawn.getLookTargets().isEmpty()); assertEquals(0,history.getUndoSize());
    }
    EntitySpawnClip spawn;
    TimelineModel model;
    TimelineTrack track;
    TimelineCommandHistory history;

    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null);
        ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        spawn = new EntitySpawnClip("Spawn", 0, 5);
        payload.addClip(spawn);
        track.addClip(new EntityTimelineClip(spawn, 0));
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    EntityLookAtClip create() {
        var session = new ActorLookAtEditSession(model, track);
        session.draft().setMode(EntityLookAtClip.TargetMode.WORLD_POINT);
        session.draft().setWorldPoint(1, 2, 3);
        return session.commit();
    }
    @Test void newDraftStaysDetachedAndCreationIsOneUndo() {
        var session = new ActorLookAtEditSession(model, track);
        session.draft().setWorldPoint(4, 5, 6);
        assertTrue(spawn.getLookTargets().isEmpty());
        var clip = session.commit(); assertNotNull(clip); assertEquals(1, history.getUndoSize());
        assertTrue(history.undo()); assertTrue(spawn.getLookTargets().isEmpty());
        assertTrue(history.redo()); assertSame(clip, spawn.getLookTargets().getFirst());
        assertEquals(1, track.getClips().size());
    }
    @Test void repeatedDraftMovesDoNotWriteSourceAndCommitRestoresExactStateOnUndo() {
        var clip = create(); clip.setStrength(.4f);
        var uuid = java.util.UUID.randomUUID(); clip.setTargetEntityUuid(uuid);
        var session = new ActorLookAtEditSession(model, track);
        for (int i = 0; i < 100; i++) session.draft().setWorldPoint(i, 5, 6);
        assertEquals(1, clip.getWorldPoint().x); assertEquals(1, history.getUndoSize());
        assertSame(clip, session.commit()); assertEquals(99, clip.getWorldPoint().x);
        assertEquals(2, history.getUndoSize()); history.undo();
        assertEquals(1, clip.getWorldPoint().x); assertEquals(.4f, clip.getStrength());
        assertEquals(uuid, clip.getTargetEntityUuid()); history.redo(); assertEquals(99, clip.getWorldPoint().x);
    }
    @Test void cancelAndNoOpDoNotCreateUndoEntries() {
        var clip = create(); var session = new ActorLookAtEditSession(model, track);
        session.draft().setWorldPoint(9, 9, 9); session.cancel(); assertNull(session.commit());
        assertEquals(1, clip.getWorldPoint().x);
        new ActorLookAtEditSession(model, track).commit(); assertEquals(1, history.getUndoSize());
    }
    @Test void changedTimeLockMuteAndBindingInvalidateWithoutWriting() {
        var clip = create(); var session = new ActorLookAtEditSession(model, track);
        model.setCurrentTime(2.5f); assertNull(session.commit()); model.setCurrentTime(2);
        session = new ActorLookAtEditSession(model, track); track.setLocked(true); assertNull(session.commit());
        assertThrows(IllegalArgumentException.class, () -> new ActorLookAtEditSession(model, track));
        track.setLocked(false); session = new ActorLookAtEditSession(model, track);
        track.setMuted(true); assertNull(session.commit()); track.setMuted(false);
        session = new ActorLookAtEditSession(model, track);
        track.getActorTrackPayload().setActorRef(new ActorRef(java.util.UUID.randomUUID()));
        assertNull(session.commit()); assertEquals(1, clip.getWorldPoint().x); assertEquals(1, history.getUndoSize());
    }
    @Test void changedSourceOrWinningClipCancelsDraft() {
        var clip = create(); var session = new ActorLookAtEditSession(model, track);
        clip.setStrength(.5f); assertNull(session.commit()); assertEquals(.5f, clip.getStrength());
        model.setCurrentTime(2.5f);
        session = new ActorLookAtEditSession(model, track);
        spawn.addLookTarget(new EntityLookAtClip("Other", 2.25f, 4));
        assertNull(session.commit()); assertEquals(1, history.getUndoSize());
    }
    @Test void nonFiniteDraftAndDeletedOwnerAreRejected() {
        var session = new ActorLookAtEditSession(model, track);
        session.draft().setWorldPoint(Float.NaN, 0, 0); assertNull(session.commit());
        assertTrue(spawn.getLookTargets().isEmpty()); assertEquals(0, history.getUndoSize());
        session = new ActorLookAtEditSession(model, track); model.removeTrack(track);
        assertNull(session.commit()); assertTrue(spawn.getLookTargets().isEmpty());
    }
    @Test void playbackAndRemovedActorCancelDraft() {
        var session = new ActorLookAtEditSession(model, track);
        model.setPlaying(true); assertNull(session.commit()); model.setPlaying(false);
        session = new ActorLookAtEditSession(model, track);
        ActorRegistryManager.setRegistry(new ActorRegistry());
        assertNull(session.commit()); assertEquals(0, history.getUndoSize());
    }
    @Test void removedWrapperAndReplacedPayloadCancelDraft() {
        var clip = create(); var session = new ActorLookAtEditSession(model, track);
        track.removeClip(spawn.getTimelineClip()); assertNull(session.commit());
        track.addClip(spawn.getTimelineClip()); session = new ActorLookAtEditSession(model, track);
        var other = new ActorTrack(); other.setActorRef(track.getActorTrackPayload().getActorRef());
        track.setActorTrackPayload(other); assertNull(session.commit());
        assertEquals(1, clip.getWorldPoint().x); assertEquals(1, history.getUndoSize());
    }
    @Test void contextRoundTripCancelsWithoutUndoEntry() {
        var edit=new ActorLookAtEditSession(model,track); edit.draft().setWorldPoint(9,0,0); model.setScrubbing(true); model.setScrubbing(false); assertNull(edit.commit());
        assertEquals(0,history.getUndoSize());
    }

    @Test void newLookTargetBelongsToSpawnAndFollowsItsTime() {
        var payload = track.getActorTrackPayload();
        var target = create();
        assertNotNull(target);
        assertEquals(1, track.getClips().size());
        assertEquals(1, payload.getClips().size());
        assertSame(target, spawn.findLookTargetAt(2));
        assertEquals(5f, target.getEndTime());
        assertTrue(history.undo());
        assertNull(payload.findLookAtClipAt(2));
        assertTrue(history.redo());
        assertSame(target, payload.findLookAtClipAt(2));

        spawn.setTimeRange(10, 15);
        assertNull(payload.findLookAtClipAt(2));
        assertSame(target, payload.findLookAtClipAt(12));
        var sampler = new com.director.timeline.playback.entity.EntityClipSampler(payload);
        assertTrue(sampler.sampleSnapshot(12).lookAtEnabled);
        assertFalse(sampler.sampleSnapshot(2).lookAtEnabled);
        var dto = com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(spawn);
        var restored = (EntitySpawnClip) com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(target.getId(), restored.findLookTargetAt(12).getId());
        assertEquals(target.getMode(), restored.findLookTargetAt(12).getMode());

        var copy = (EntityTimelineClip) TimelineClipCopies.copy(spawn.getTimelineClip());
        var copiedSpawn = (EntitySpawnClip) copy.getEntityClip();
        assertNotEquals(target.getId(), copiedSpawn.findLookTargetAt(12).getId());
    }

    @Test void ownedLookRangeEditsAndDeletionUndoWithoutCreatingTimelineClips() {
        var payload = track.getActorTrackPayload();
        var target = create();
        assertFalse(ActorOwnedIntervalEditing.resize(model, spawn, target, 3, 6));
        assertFalse(ActorOwnedIntervalEditing.resize(model, spawn, target, 4, 3));
        assertEquals(1, history.getUndoSize());
        assertTrue(ActorOwnedIntervalEditing.resize(model, spawn, target, 3, 4));
        assertNull(payload.findLookAtClipAt(2));
        assertSame(target, payload.findLookAtClipAt(3.5f));
        assertNull(payload.findLookAtClipAt(4.5f));
        track.setLocked(true);
        assertFalse(ActorOwnedIntervalEditing.remove(model, spawn, target));
        track.setLocked(false);
        assertTrue(ActorOwnedIntervalEditing.remove(model, spawn, target));
        assertNull(payload.findLookAtClipAt(3.5f));
        assertEquals(1, track.getClips().size());
        assertTrue(history.undo());
        assertSame(target, payload.findLookAtClipAt(3.5f));
        assertTrue(history.undo());
        assertEquals(2f, target.getStartTime());
        assertEquals(5f, target.getEndTime());
        assertTrue(history.redo());
        assertTrue(history.redo());
        assertTrue(spawn.getLookTargets().isEmpty());
    }

    @Test void draggingAnOwnedLookRangeClampsAtItsSpawnEdges() {
        var original = new ActorOwnedIntervalEditing.Range(2, 4);
        var bounds = new ActorOwnedIntervalEditing.Range(0, 6);
        assertEquals(new ActorOwnedIntervalEditing.Range(0, 2),
                ActorOwnedIntervalEditing.dragRange(original, bounds, ActorOwnedIntervalEditing.DragMode.MOVE, -4));
        assertEquals(new ActorOwnedIntervalEditing.Range(4, 6),
                ActorOwnedIntervalEditing.dragRange(original, bounds, ActorOwnedIntervalEditing.DragMode.MOVE, 4));
        assertEquals(new ActorOwnedIntervalEditing.Range(0, 4),
                ActorOwnedIntervalEditing.dragRange(original, bounds, ActorOwnedIntervalEditing.DragMode.START, -4));
        assertEquals(new ActorOwnedIntervalEditing.Range(2, 6),
                ActorOwnedIntervalEditing.dragRange(original, bounds, ActorOwnedIntervalEditing.DragMode.END, 4));
        var trimmed = ActorOwnedIntervalEditing.dragRange(original, bounds, ActorOwnedIntervalEditing.DragMode.START, 10);
        assertEquals(0.01f, trimmed.end() - trimmed.start(), 1e-5f);
    }
}
