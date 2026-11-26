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
        var parent = addSpawn(0, 10);
        var spawn = (EntitySpawnClip) parent.getEntityClip();
        assertSame(parent, service.createAction(model, track, ActorActionType.WALK, 4, .6f, true));
        var action = spawn.getActionIntervals().getFirst();
        assertEquals(4, action.getLength());
        assertEquals(.6f, action.getIntensity());
        assertTrue(action.isLoop());
        assertNull(service.createAction(model, track, ActorActionType.WALK, 1, 1, true));
        assertEquals(1, history.getUndoSize());
        assertTrue(history.undo());
        assertTrue(spawn.getActionIntervals().isEmpty());
        assertTrue(history.redo());
        assertSame(action, spawn.getActionIntervals().getFirst());
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
    @Test void menuCreationIsOneUndoAndRedoRestoresSamePose() {
        var parent = addSpawn(0, 10);
        var spawn = (EntitySpawnClip) parent.getEntityClip();
        assertSame(parent, service.createClip(model, track, ActorTrackClipKind.POSE, null));
        var pose = spawn.getPoseIntervals().getFirst();
        assertEquals(3f, pose.getStartTime());
        assertTrue(history.undo());
        assertTrue(spawn.getPoseIntervals().isEmpty());
        assertFalse(history.undo());
        assertTrue(history.redo());
        assertSame(pose, spawn.getPoseIntervals().getFirst());
        assertSame(parent, track.getClips().getFirst());
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
        var parent = addSpawn(0, 10);
        assertSame(parent, service.createClip(model, track, ActorTrackClipKind.ACTION, ActorActionType.WALK));
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

    @Test void runIntervalFollowsItsSpawnAndKeepsItsActionClock() {
        var payload = track.getActorTrackPayload();
        var spawn = new EntitySpawnClip("Performance", 0, 5);
        var wrapper = new EntityTimelineClip(spawn, 0);
        payload.addClip(spawn);
        track.addClip(wrapper);
        assertSame(wrapper, service.createAction(model, track, ActorActionType.RUN, 4, .6f, true));
        var run = spawn.getActionIntervals().getFirst();
        assertEquals(5f, run.getEndTime());
        assertEquals(1, track.getClips().size());
        assertNull(service.createAction(model, track, ActorActionType.RUN, 1, 1, true));
        assertTrue(history.undo());
        assertTrue(spawn.getActionIntervals().isEmpty());
        assertTrue(history.redo());
        var edit = new com.director.ui.actor.edit.ActorClipPropertyEditSession(model, run, spawn);
        ((ActorActionClip) edit.draft()).setIntensity(.75f);
        assertTrue(edit.commit());
        spawn.setTimeRange(10, 15);
        var sampler = new com.director.timeline.playback.entity.EntityClipSampler(payload);
        var sample = sampler.sampleSnapshot(14f);
        assertEquals(ActorActionType.RUN, sample.action);
        assertEquals(.75f, sample.actionIntensity);
        assertEquals(1f, sample.actionLocalTime);
        assertFalse(sampler.sampleSnapshot(12f).actionExplicit);
        var dto = com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(spawn);
        var loaded = (EntitySpawnClip) com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(run.getId(), loaded.getActionIntervals().getFirst().getId());
        var copy = (EntityTimelineClip) TimelineClipCopies.copy(wrapper);
        assertNotEquals(run.getId(), ((EntitySpawnClip) copy.getEntityClip()).getActionIntervals().getFirst().getId());
    }

    @Test void poseIntervalsBlendWithinTheirSpawnAndMoveWithIt() {
        var payload = track.getActorTrackPayload();
        var spawn = new EntitySpawnClip("Performance", 0, 8);
        var wrapper = new EntityTimelineClip(spawn, 0);
        payload.addClip(spawn);
        track.addClip(wrapper);
        assertSame(wrapper, service.createPose(model, track, 1, pose -> {
            pose.setOverrideHead(true);
            pose.setHeadYaw(.2f);
        }));
        model.setCurrentTime(5);
        assertSame(wrapper, service.createPose(model, track, 2, pose -> {
            pose.setOverrideHead(true);
            pose.setHeadYaw(.8f);
        }));
        assertEquals(1, track.getClips().size());
        var sampler = new com.director.timeline.playback.entity.EntityClipSampler(payload);
        assertEquals(.5f, sampler.sampleSnapshot(6).poseHeadYaw, 1e-5f);
        assertTrue(history.undo());
        assertFalse(sampler.sampleSnapshot(6).poseOverrideEnabled);
        assertTrue(history.redo());
        assertEquals(.5f, sampler.sampleSnapshot(6).poseHeadYaw, 1e-5f);

        spawn.setTimeRange(10, 18);
        assertFalse(sampler.sampleSnapshot(6).poseOverrideEnabled);
        assertEquals(.5f, sampler.sampleSnapshot(16).poseHeadYaw, 1e-5f);
        var dto = com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(spawn);
        var restored = (EntitySpawnClip) com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(spawn.getPoseIntervals().getLast().getId(), restored.getPoseIntervals().getLast().getId());
        var copied = (EntityTimelineClip) TimelineClipCopies.copy(wrapper);
        assertNotEquals(restored.getPoseIntervals().getFirst().getId(),
                ((EntitySpawnClip) copied.getEntityClip()).getPoseIntervals().getFirst().getId());
    }

    @Test void behaviorCreationRequiresAnActiveSpawnAndLookAtUsesItsInterval() {
        for (var kind : java.util.List.of(ActorTrackClipKind.LOOK_AT_CAMERA, ActorTrackClipKind.STATE,
                ActorTrackClipKind.ACTION, ActorTrackClipKind.POSE)) {
            assertNull(service.createClip(model, track, kind, ActorActionType.WALK));
            assertEquals("gui.director.actor.track.behavior_requires_spawn", service.lastRefusal());
        }
        assertTrue(track.getClips().isEmpty());
        assertEquals(0, history.getUndoSize());

        var parent = addSpawn(2, 3.5f);
        assertSame(parent, service.createClip(model, track, ActorTrackClipKind.LOOK_AT_CAMERA, null));
        var spawn = (EntitySpawnClip) parent.getEntityClip();
        assertEquals(3.5f, spawn.getLookTargets().getFirst().getEndTime());
        assertEquals(1, track.getClips().size());
        assertTrue(history.undo());
        assertTrue(spawn.getLookTargets().isEmpty());
    }

    private EntityTimelineClip addSpawn(float start, float end) {
        var spawn = new EntitySpawnClip("Performance", start, end);
        track.getActorTrackPayload().addClip(spawn);
        var wrapper = new EntityTimelineClip(spawn, 0);
        track.addClip(wrapper);
        return wrapper;
    }
}
