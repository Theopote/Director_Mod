package com.director.ui.actor.track;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.action.ActorActionType;
import com.director.ui.actor.edit.ActorOwnedIntervalEditing;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorTrackClipAuthoringServiceTest {
    @Test void configuredActionIsOneCommandAndRejectsOverlap() {
        var wrapper=service.createAction(model,track,ActorActionType.WALK,4,.6f,true);assertNotNull(wrapper);
        var action=(com.director.timeline.model.entity.ActorActionClip)wrapper.getEntityClip();
        assertEquals(4,action.getLength());assertEquals(.6f,action.getIntensity());assertTrue(action.isLoop());
        assertNull(service.createAction(model,track,ActorActionType.WALK,1,1,true));
        assertEquals(1,history.getUndoSize());assertTrue(history.undo());assertTrue(track.getClips().isEmpty());
        assertTrue(history.redo());assertSame(wrapper,track.getClips().getFirst());
    }
    private TimelineModel model;
    private TimelineTrack track;
    private TimelineCommandHistory history;
    private final ActorTrackClipAuthoringService service = ActorTrackClipAuthoringService.getInstance();
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(3);
        track = new TimelineTrack("Player", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    @Test void menuCreationIsOneUndoAndRedoRestoresSameClipAndWrapper() {
        var wrapper = service.createClip(model, track, ActorTrackClipKind.POSE, null);
        assertNotNull(wrapper); assertEquals(3, wrapper.getStartTime());
        var clip = track.getActorTrackPayload().getClips().getFirst();
        assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
        assertTrue(track.getActorTrackPayload().getClips().isEmpty()); assertFalse(history.undo());
        assertFalse(model.isSelected(wrapper)); assertTrue(history.redo());
        assertSame(wrapper, track.getClips().getFirst()); assertSame(clip, track.getActorTrackPayload().getClips().getFirst());
        assertSame(track, wrapper.getParentTrack());
    }
    @Test void lockedDetachedAndPlayingTracksNeverCreateClips() {
        track.setLocked(true); assertNull(service.createClip(model, track, ActorTrackClipKind.STATE, null));
        track.setLocked(false); model.setPlaying(true); assertNull(service.createClip(model, track, ActorTrackClipKind.STATE, null));
        model.setPlaying(false);
        var other = new TimelineModel(new TimelineViewportState()); assertNull(service.createClip(other, track, ActorTrackClipKind.STATE, null));
        assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty()); assertFalse(history.undo());
    }
    @Test void unsupportedActorActionsAndLegacyPoseAreRejected() {
        var cow = new Actor("Cow", ActorType.COW); ActorRegistryManager.getInstance().registerActor(cow);
        track.getActorTrackPayload().setActorRef(new ActorRef(cow.getActorId()));
        assertNull(service.createClip(model, track, ActorTrackClipKind.POSE, null));
        assertNull(service.createClip(model, track, ActorTrackClipKind.ACTION, ActorActionType.DANCE));
        assertTrue(track.getClips().isEmpty()); assertFalse(history.undo());
        assertNotNull(service.createClip(model, track, ActorTrackClipKind.ACTION, ActorActionType.WALK));
    }
    @Test void creationBlockReasonExplainsWhyCreationIsUnavailable() {
        assertNull(service.creationBlockReason(model, track));
        track.setLocked(true);
        assertEquals("gui.director.actor.track.create_locked", service.creationBlockReason(model, track));
        track.setLocked(false); track.setMuted(true);
        assertEquals("gui.director.actor.track.create_muted", service.creationBlockReason(model, track));
        track.setMuted(false); model.setPlaying(true);
        assertEquals("gui.director.actor.track.create_busy", service.creationBlockReason(model, track));
        model.setPlaying(false);
        assertEquals("gui.director.actor.track.create_no_project", service.creationBlockReason(null, track));
        assertEquals("gui.director.actor.track.create_no_track", service.creationBlockReason(model, null));
        assertEquals("gui.director.actor.track.create_no_track",
                service.creationBlockReason(new TimelineModel(new TimelineViewportState()), track));
    }

    @Test void stateIsOwnedByActiveSpawnAndKeepsFieldWiseLegacyOverlay() {
        var payload = track.getActorTrackPayload();
        assertNull(service.createClip(model, track, ActorTrackClipKind.STATE, null));
        var spawn = new EntitySpawnClip("Performance", 0, 5);
        var wrapper = new EntityTimelineClip(spawn, 0);
        payload.addClip(spawn);
        track.addClip(wrapper);
        var legacy = new EntityStateClip("Legacy", 0, 5);
        legacy.setGlowing(true);
        legacy.setVisibilityOverride(.5f);
        payload.addClip(legacy);
        assertSame(wrapper, service.createClip(model, track, ActorTrackClipKind.STATE, null));
        var state = spawn.getStateIntervals().getFirst();
        assertEquals(1, track.getClips().size());
        assertSame(wrapper, model.getSelectedClip());
        assertTrue(history.undo());
        assertTrue(spawn.getStateIntervals().isEmpty());
        assertTrue(history.redo());
        assertSame(state, spawn.getStateIntervals().getFirst());

        var edit = new com.director.ui.actor.edit.ActorClipPropertyEditSession(model, state, spawn);
        ((EntityStateClip) edit.draft()).setGlowing(false);
        assertTrue(edit.commit());
        var sampler = new com.director.timeline.playback.entity.EntityClipSampler(payload);
        assertFalse(sampler.sampleSnapshot(3).glowing);
        assertEquals(.5f, sampler.sampleSnapshot(3).visibility);
        assertTrue(history.undo());
        assertTrue(sampler.sampleSnapshot(3).glowing);
        assertTrue(history.redo());

        spawn.setTimeRange(10, 15);
        assertEquals(13f, state.getStartTime());
        var dto = com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(spawn);
        var loaded = (EntitySpawnClip) com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(Boolean.FALSE, loaded.getStateIntervals().getFirst().getGlowingOverride());
        var copied = (EntityTimelineClip) TimelineClipCopies.copy(wrapper);
        assertNotEquals(state.getId(), ((EntitySpawnClip) copied.getEntityClip()).getStateIntervals().getFirst().getId());
    }

    @Test void ownedStateRangeCanBeEditedAndDeletedWithUndo() {
        var spawn = new EntitySpawnClip("Performance", 0, 6);
        var wrapper = new EntityTimelineClip(spawn, 0);
        var payload = track.getActorTrackPayload();
        payload.addClip(spawn);
        track.addClip(wrapper);
        assertSame(wrapper, service.createClip(model, track, ActorTrackClipKind.STATE, null));
        var state = spawn.getStateIntervals().getFirst();
        state.setGlowing(true);
        var editing = new com.director.timeline.playback.entity.EntityClipSampler(payload);
        assertFalse(ActorOwnedIntervalEditing.resize(model, spawn, state, 4, 7));
        assertTrue(ActorOwnedIntervalEditing.resize(model, spawn, state, 4, 5));
        assertFalse(editing.sampleSnapshot(3).glowing);
        assertTrue(editing.sampleSnapshot(4.5f).glowing);
        assertTrue(ActorOwnedIntervalEditing.remove(model, spawn, state));
        assertFalse(editing.sampleSnapshot(4.5f).glowing);
        assertTrue(history.undo());
        assertTrue(editing.sampleSnapshot(4.5f).glowing);
        assertTrue(history.undo());
        assertTrue(editing.sampleSnapshot(3).glowing);
        assertEquals(1, track.getClips().size());
    }
}
