package com.director.ui.actor.lookat;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorLookAtEditSessionTest {
    @Test void defaultEditingUsesPlaybackWinnerButExplicitSelectionStillWins() {
        var payload=track.getActorTrackPayload();
        payload.addClip(new EntitySpawnClip("Spawn",0,10));
        var strong=new EntityLookAtClip("Strong",0,10);strong.setStrength(.9f);strong.setWorldPoint(10,0,0);
        var weak=new EntityLookAtClip("Weak",1,10);weak.setStrength(.2f);weak.setWorldPoint(20,0,0);
        payload.addClip(strong);track.addClip(new EntityTimelineClip(strong,0));
        payload.addClip(weak);track.addClip(new EntityTimelineClip(weak,0));
        var sample=new com.director.timeline.playback.entity.EntityClipSampler(payload).sampleSnapshot(2);
        assertEquals(strong.getWorldPoint(),sample.lookAtPoint);
        var session=new ActorLookAtEditSession(model,track);
        assertSame(strong,session.source());session.draft().setWorldPoint(12,0,0);session.commit();
        assertEquals(12,strong.getWorldPoint().x);assertEquals(20,weak.getWorldPoint().x);
        assertTrue(history.undo());assertEquals(10,strong.getWorldPoint().x);
        model.setSelectedClip(weak.getTimelineClip());
        assertSame(weak,new ActorLookAtEditSession(model,track).source());
        weak.setStrength(.9f);
        assertSame(weak,ActorLookAtEditSession.active(payload,2));
        weak.setMuted(true);assertSame(strong,ActorLookAtEditSession.active(payload,2));
    }
    @Test void explicitlySelectedLookReferenceWinsOverOverlapEvenOutsideItsInterval() {
        var first=create();
        var other=new EntityLookAtClip("Other",1,10);
        track.getActorTrackPayload().addClip(other);
        track.addClip(new EntityTimelineClip(other,0));
        model.setSelectedClip(first.getTimelineClip()); model.setCurrentTime(4);
        var session=new ActorLookAtEditSession(model,track);
        assertSame(first,session.source()); session.draft().setWorldPoint(8,9,10);
        assertSame(first,session.commit());
        assertEquals(2,track.getActorTrackPayload().getClips().size());
        assertEquals(8,first.getWorldPoint().x); assertNotEquals(8,other.getWorldPoint().x);
    }
    @Test void explicitlySelectedMutedLookDoesNotFallBackOrCreateAnother() {
        var first=create(); first.setMuted(true); model.setSelectedClip(first.getTimelineClip());
        var other=new EntityLookAtClip("Other",1,10); track.getActorTrackPayload().addClip(other);
        track.addClip(new EntityTimelineClip(other,0));
        assertSame(first,ActorLookAtEditSession.resolveSource(model,track));
        assertThrows(IllegalArgumentException.class,()->new ActorLookAtEditSession(model,track));
        assertEquals(2,track.getActorTrackPayload().getClips().size());
    }
    @Test void pendingCreationWritesNothingAndScrubbingCancelsIt() {
        var session=new ActorLookAtEditSession(model,track);
        assertNull(session.source()); assertTrue(track.getClips().isEmpty());
        model.setScrubbing(true); assertNull(session.commit());
        assertTrue(track.getActorTrackPayload().getClips().isEmpty()); assertEquals(0,history.getUndoSize());
    }
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
        assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        var clip = session.commit(); assertNotNull(clip); assertEquals(1, history.getUndoSize());
        assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
        assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        assertTrue(history.redo()); assertSame(clip, track.getActorTrackPayload().getClips().getFirst());
        assertSame(clip.getTimelineClip(), track.getClips().getFirst());
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
        track.getActorTrackPayload().addClip(new EntityLookAtClip("Other", 2.25f, 4));
        assertNull(session.commit()); assertEquals(1, history.getUndoSize());
    }
    @Test void nonFiniteDraftAndDeletedOwnerAreRejected() {
        var session = new ActorLookAtEditSession(model, track);
        session.draft().setWorldPoint(Float.NaN, 0, 0); assertNull(session.commit());
        assertTrue(track.getClips().isEmpty()); assertEquals(0, history.getUndoSize());
        session = new ActorLookAtEditSession(model, track); model.removeTrack(track);
        assertNull(session.commit()); assertTrue(track.getClips().isEmpty());
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
        track.removeClip(clip.getTimelineClip()); assertNull(session.commit());
        track.addClip(clip.getTimelineClip()); session = new ActorLookAtEditSession(model, track);
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
        var spawn = new EntitySpawnClip("Spawn", 0, 5);
        payload.addClip(spawn);
        track.addClip(new EntityTimelineClip(spawn, 0));
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
        var spawn = new EntitySpawnClip("Spawn", 0, 5);
        payload.addClip(spawn);
        track.addClip(new EntityTimelineClip(spawn, 0));
        var target = create();
        assertFalse(ActorLookIntervalEditing.resize(model, spawn, target, 3, 6));
        assertFalse(ActorLookIntervalEditing.resize(model, spawn, target, 4, 3));
        assertEquals(1, history.getUndoSize());
        assertTrue(ActorLookIntervalEditing.resize(model, spawn, target, 3, 4));
        assertNull(payload.findLookAtClipAt(2));
        assertSame(target, payload.findLookAtClipAt(3.5f));
        assertNull(payload.findLookAtClipAt(4.5f));
        track.setLocked(true);
        assertFalse(ActorLookIntervalEditing.remove(model, spawn, target));
        track.setLocked(false);
        assertTrue(ActorLookIntervalEditing.remove(model, spawn, target));
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
        var original = new ActorLookIntervalEditing.Range(2, 4);
        var bounds = new ActorLookIntervalEditing.Range(0, 6);
        assertEquals(new ActorLookIntervalEditing.Range(0, 2),
                ActorLookIntervalEditing.dragRange(original, bounds, ActorLookIntervalEditing.DragMode.MOVE, -4));
        assertEquals(new ActorLookIntervalEditing.Range(4, 6),
                ActorLookIntervalEditing.dragRange(original, bounds, ActorLookIntervalEditing.DragMode.MOVE, 4));
        assertEquals(new ActorLookIntervalEditing.Range(0, 4),
                ActorLookIntervalEditing.dragRange(original, bounds, ActorLookIntervalEditing.DragMode.START, -4));
        assertEquals(new ActorLookIntervalEditing.Range(2, 6),
                ActorLookIntervalEditing.dragRange(original, bounds, ActorLookIntervalEditing.DragMode.END, 4));
        var trimmed = ActorLookIntervalEditing.dragRange(original, bounds, ActorLookIntervalEditing.DragMode.START, 10);
        assertEquals(0.01f, trimmed.end() - trimmed.start(), 1e-5f);
    }
}
