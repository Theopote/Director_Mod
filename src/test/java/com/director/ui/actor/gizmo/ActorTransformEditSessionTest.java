package com.director.ui.actor.gizmo;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorTransformEditSessionTest {
    @Test void repeatedTransformsAtSameTimeReuseOneClipAndNeverCreateAnotherTrack() {
        var first=new ActorTransformEditSession(model,track,null);
        first.arm(); first.draft().setToPos(2,3,4); var clip=first.commit();
        assertNotNull(clip);
        var second=new ActorTransformEditSession(model,track,
                ActorTransformEditSession.resolveSource(track.getActorTrackPayload(),model.getCurrentTime()));
        second.arm(); second.draft().setToRot(45,0,0); assertSame(clip,second.commit());
        assertEquals(1,model.getAllTracks().size());
        assertEquals(1,track.getClips().size()); assertEquals(1,track.getActorTrackPayload().getClips().size());
        assertTrue(history.undo()); assertEquals(0,clip.getToRot().x);
        assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
    }
    @Test void explicitlySelectedReferenceWinsOutsideItsRange() {
        var selected = source();
        var other = new EntityTransformClip("Other", 20, 30);
        track.getActorTrackPayload().addClip(other);
        track.addClip(new EntityTimelineClip(other, 0));
        model.selectItem(selected.getTimelineClip(), false);
        model.setCurrentTime(25);
        assertSame(selected, ActorTransformEditSession.resolveSource(model, track));
        var edit = session(selected);
        edit.arm(); edit.draft().setToPos(10, 20, 30);
        assertSame(selected, edit.commit());
        assertEquals(2, track.getClips().size());
        assertEquals(new Vector3f(), other.getToPos());
    }

    @Test void mutedExplicitReferenceCannotBecomeAnImplicitNewClip() {
        var clip = source();
        model.selectItem(clip.getTimelineClip(), false);
        clip.setMuted(true);
        assertSame(clip, ActorTransformEditSession.resolveSource(model, track));
        assertThrows(IllegalArgumentException.class, () -> session(clip));
        assertEquals(1, track.getClips().size());
    }

    @Test void initialTransformsUpdateOnlySpawnAndUndoAllChannels() {
        var spawn = new EntitySpawnClip("Actor", 0, 5);
        spawn.setPosition(1, 2, 3);
        track.getActorTrackPayload().addClip(spawn);
        var wrapper = new EntityTimelineClip(spawn, 0); track.addClip(wrapper);
        var edit = session(null);
        edit.draft().setToPos(1, 2, 3); edit.arm();
        edit.draft().setToPos(4, 5, 6); edit.draft().setToRot(90, 10, 20); edit.draft().setToScale(2, 3, 4);
        assertNotNull(edit.commit());
        assertEquals(java.util.List.of(wrapper), track.getClips());
        assertEquals(java.util.List.of(spawn), track.getActorTrackPayload().getClips());
        var sample = new com.director.timeline.playback.entity.EntityClipSampler(track.getActorTrackPayload()).sampleAt(2);
        assertEquals(new Vector3f(4, 5, 6), sample.position);
        assertEquals(new Vector3f(90, 10, 20), sample.rotation);
        assertEquals(new Vector3f(2, 3, 4), sample.scale);
        assertTrue(history.undo()); assertEquals(new Vector3f(1, 2, 3), spawn.getPosition());
        assertEquals(0, spawn.getRollDeg()); assertEquals(new Vector3f(1), spawn.getScale());
        assertTrue(history.redo()); assertEquals(new Vector3f(2, 3, 4), spawn.getScale());
        assertEquals(1, track.getClips().size());
    }

    @Test void spawnTransformPersistenceAndLegacyDefaults() {
        var spawn = new EntitySpawnClip("Actor", 0, 5);
        spawn.setRollDeg(25); spawn.setScale(2, 3, 4);
        track.getActorTrackPayload().addClip(spawn);
        track.addClip(new EntityTimelineClip(spawn, 0));
        var viewport = new TimelineViewportState();
        var dto = com.director.timeline.persistence.TimelineModelPersistence.toDTO(model, viewport);
        var restored = new TimelineModel(viewport); restored.setSuppressPersistenceWrites(true);
        com.director.timeline.persistence.TimelineModelPersistence.fromDTO(dto, restored, viewport);
        var loaded = (EntitySpawnClip) restored.getAllTracks().getFirst().getActorTrackPayload().getClips().getFirst();
        assertEquals(25, loaded.getRollDeg()); assertEquals(new Vector3f(2, 3, 4), loaded.getScale());
        var old = dto.tracks.getFirst().actorTrackPayload.clips.getFirst();
        old.spawnRoll = null; old.spawnScaleX = null; old.spawnScaleY = null; old.spawnScaleZ = null;
        com.director.timeline.persistence.TimelineModelPersistence.fromDTO(dto, restored, viewport);
        loaded = (EntitySpawnClip) restored.getAllTracks().getFirst().getActorTrackPayload().getClips().getFirst();
        assertEquals(0, loaded.getRollDeg()); assertEquals(new Vector3f(1), loaded.getScale());
    }

    @Test void keyedSpawnGestureWritesCurrentKeyWithoutChangingBaseOrCreatingClip() {
        var spawn = new EntitySpawnClip("Actor", 0, 5); spawn.setPosition(1, 2, 3);
        track.getActorTrackPayload().addClip(spawn); track.addClip(new EntityTimelineClip(spawn, 0));
        var key = com.director.ui.actor.pose.ActorRootKeyframeEditing.insert(model, track, spawn);
        var edit = session(null);
        edit.draft().setToPos(1, 2, 3); edit.arm(); edit.draft().setToPos(6, 2, 3);
        assertNotNull(edit.commit());
        assertEquals(java.util.List.of(key), spawn.getKeyframes());
        assertEquals(new Vector3f(6, 2, 3), key.getPosition());
        assertEquals(new Vector3f(1, 2, 3), spawn.getPosition()); assertEquals(1, track.getClips().size());
        assertTrue(history.undo()); assertEquals(new Vector3f(1, 2, 3), key.getPosition());
        assertTrue(history.redo()); assertEquals(new Vector3f(6, 2, 3), key.getPosition());
    }

    TimelineModel model;
    TimelineTrack track;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    EntityTransformClip source() {
        var clip = new EntityTransformClip("Existing", 1, 3);
        clip.setFromPos(1, 2, 3); clip.setToPos(4, 5, 6);
        clip.setFromRot(10, 20, 30); clip.setToRot(40, 50, 60);
        clip.setFromScale(1, 2, 3); clip.setToScale(4, 5, 6);
        clip.setInterpolation(TimelineInterpolation.LINEAR); clip.setPathId("path"); clip.setFollowPath(true);
        track.getActorTrackPayload().addClip(clip);
        track.addClip(new EntityTimelineClip(clip, track.getActorTrackPayload().getColorRgba()));
        return clip;
    }
    ActorTransformEditSession session(EntityTransformClip clip) { return new ActorTransformEditSession(model, track, clip); }
    @Test void creationIsDetachedUntilOneCommitAndBothListsUndoTogether() {
        var edit = session(null);
        for (int i = 0; i < 100; i++) edit.draft().setToPos(i, 0, 0);
        assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        var result = edit.commit(); assertNotNull(result); assertEquals(1, history.getUndoSize());
        history.undo(); assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        history.redo(); assertSame(result, track.getActorTrackPayload().getClips().getFirst());
        assertSame(result.getTimelineClip(), track.getClips().getFirst());
    }
    @Test void initializationAndZeroMotionDoNotWriteOrCreateHistory() {
        var clip = source(); var edit = session(clip);
        edit.draft().setFromPos(2, 3, 4); edit.draft().setToPos(2, 3, 4); edit.arm();
        assertNull(edit.commit()); assertEquals(new Vector3f(1,2,3), clip.getFromPos()); assertEquals(0, history.getUndoSize());
        assertThrows(IllegalArgumentException.class, () -> session(null));
    }
    @Test void cancelDoesNotExtendOrRewriteSource() {
        var clip = source(); model.setCurrentTime(3.5f); var edit = session(clip);
        edit.draft().setTimeRange(1, 4.5f); edit.draft().setToPos(9,9,9); edit.cancel();
        assertNull(edit.commit()); assertEquals(3, clip.getEndTime()); assertEquals(new Vector3f(4,5,6), clip.getToPos());
        assertEquals(0, history.getUndoSize());
    }
    @Test void extensionAndTransformUndoPreserveIdentityAndOtherChannels() {
        var clip = source(); var wrapper = clip.getTimelineClip(); model.setCurrentTime(3.5f);
        var edit = session(clip); edit.draft().setTimeRange(1, 4.5f); edit.arm(); edit.draft().setToPos(8,9,10);
        assertSame(clip, edit.commit()); assertSame(wrapper, clip.getTimelineClip()); assertEquals(4.5f, wrapper.getEndTime());
        assertEquals(TimelineInterpolation.LINEAR, clip.getInterpolation()); assertEquals("path", clip.getPathId());
        assertTrue(clip.isFollowPath()); assertEquals(new Vector3f(40,50,60), clip.getToRot());
        assertEquals(new Vector3f(4,5,6), clip.getToScale());
        history.undo(); assertEquals(3, clip.getEndTime()); assertEquals(3, wrapper.getEndTime());
        assertEquals(new Vector3f(4,5,6), clip.getToPos()); history.redo(); assertEquals(4.5f, clip.getEndTime());
    }
    @Test void returnToInitializedStateAndUntouchedNewGestureAreNoOps() {
        var edit = session(null); edit.draft().setFromPos(2,3,4); edit.draft().setToPos(2,3,4); edit.arm();
        edit.draft().setToPos(9,9,9); edit.draft().setToPos(2,3,4); assertNull(edit.commit());
        assertNull(session(null).commit()); assertEquals(0, history.getUndoSize()); assertTrue(track.getClips().isEmpty());
    }
    @Test void locksMutePlaybackAndTimeChangesRejectWithoutCreatingData() {
        var edit = session(null); track.setLocked(true); assertNull(edit.commit());
        assertThrows(IllegalArgumentException.class, () -> session(null)); track.setLocked(false);
        edit = session(null); track.setMuted(true); assertNull(edit.commit()); track.setMuted(false);
        edit = session(null); model.setPlaying(true); assertNull(edit.commit()); model.setPlaying(false);
        edit = session(null); model.setCurrentTime(3); assertNull(edit.commit()); assertTrue(track.getClips().isEmpty());
    }
    @Test void sourceMutationOrNewWinningClipInvalidatesFrozenTarget() {
        var clip = source(); var edit = session(clip); clip.setInterpolation(TimelineInterpolation.SMOOTH);
        assertNull(edit.commit()); assertEquals(TimelineInterpolation.SMOOTH, clip.getInterpolation());
        edit = session(clip); track.getActorTrackPayload().addClip(new EntityTransformClip("Other", 1.5f, 4));
        assertNull(edit.commit()); assertEquals(0, history.getUndoSize());
    }
    @Test void actorRemovalRebindingAndWrapperRemovalReject() {
        var clip = source(); var edit = session(clip); track.removeClip(clip.getTimelineClip()); assertNull(edit.commit());
        track.addClip(clip.getTimelineClip()); edit = session(clip);
        var ref = track.getActorTrackPayload().getActorRef();
        track.getActorTrackPayload().setActorRef(new ActorRef(java.util.UUID.randomUUID())); assertNull(edit.commit());
        track.getActorTrackPayload().setActorRef(ref); edit = session(clip);
        ActorRegistryManager.setRegistry(new ActorRegistry()); assertNull(edit.commit());
    }
    @Test void invalidValuesAreRejectedAndCannotLeakIntoSource() {
        var clip = source(); var edit = session(clip); edit.draft().setToPos(Float.NaN, 0, 0);
        assertNull(edit.commit()); edit = session(clip); edit.draft().setToScale(0, 1, 1); assertNull(edit.commit());
        assertEquals(new Vector3f(4,5,6), clip.getToScale()); assertEquals(0, history.getUndoSize());
    }
    @Test void distantExistingTransformIsReusedAndActiveClipStillWins() {
        var clip = source(); model.setCurrentTime(3.5f);
        assertSame(clip, ActorTransformEditSession.resolveSource(track.getActorTrackPayload(), model.getCurrentTime()));
        model.setCurrentTime(4); assertSame(clip, ActorTransformEditSession.resolveSource(track.getActorTrackPayload(), 4));
        var other = new EntityTransformClip("Other", 3.25f, 5); track.getActorTrackPayload().addClip(other);
        assertSame(other, ActorTransformEditSession.resolveSource(track.getActorTrackPayload(), 4));
    }
    @Test void noMotionAtExtensionDoesNotExtendPersistentRange() {
        var clip = source(); model.setCurrentTime(3.5f); var edit = session(clip);
        edit.draft().setTimeRange(1, 4.5f); edit.arm(); assertNull(edit.commit());
        assertEquals(3, clip.getEndTime()); assertEquals(0, history.getUndoSize());
    }
    @Test void partialWrapperInsertionFailureRollsBackBothLists() {
        var payload = track.getActorTrackPayload(); model.removeTrack(track);
        track = new TimelineTrack("Failing", TrackType.ACTOR) {
            @Override public boolean addClip(TimelineClip clip) {
                super.addClip(clip); throw new IllegalStateException("Injected insertion failure");
            }
        };
        track.setActorTrackPayload(payload); model.addTrack(track);
        var edit = session(null); edit.draft().setToPos(2, 3, 4);
        assertThrows(IllegalStateException.class, edit::commit);
        assertTrue(payload.getClips().isEmpty()); assertTrue(track.getClips().isEmpty());
        assertEquals(0, history.getUndoSize());
    }
    @Test void keyedClipUpdatesTheCurrentKeyAndPreservesUneditedChannelsAndMetadata() {
        var clip = source(); var key = new EntityClipKeyframe(2, TimelineInterpolation.LINEAR);
        key.setPosition(7,8,9); key.setRotation(11,12,13); key.setScale(2,3,4);
        key.setDisplayName("Metadata"); key.setActionSpeed(1.5f); clip.addKeyframe(key);
        var edit = session(clip); edit.draft().setFromPos(7,8,9); edit.draft().setToPos(7,8,9); edit.arm();
        edit.draft().setFromPos(15,8,9); edit.draft().setToPos(15,8,9); edit.commit();
        assertSame(key, clip.getKeyframes().getFirst()); assertEquals(new Vector3f(15,8,9), key.getPosition());
        assertEquals(new Vector3f(11,12,13), key.getRotation()); assertEquals(new Vector3f(2,3,4), key.getScale());
        assertEquals("Metadata", key.getDisplayName()); assertEquals(1.5f, key.getActionSpeed());
        assertEquals(new Vector3f(1,2,3), clip.getFromPos()); assertEquals(new Vector3f(4,5,6), clip.getToPos());
        var sampled = new com.director.timeline.playback.entity.EntityClipSampler(track.getActorTrackPayload()).sampleAt(2);
        assertEquals(new Vector3f(15,8,9), sampled.position);
        history.undo(); assertSame(key, clip.getKeyframes().getFirst()); assertEquals(new Vector3f(7,8,9), key.getPosition());
        history.redo(); assertEquals(new Vector3f(15,8,9), key.getPosition());
    }
    @Test void keyedClipInsertsAtPlayheadAndUndoRemovesOnlyInsertedKey() {
        var clip = source(); var key = new EntityClipKeyframe(1);
        key.setPosition(7,8,9); key.setRotation(10,20,30); key.setScale(2,3,4); clip.addKeyframe(key);
        var edit = session(clip); edit.arm(); edit.draft().setToRot(50,60,70); edit.commit();
        assertEquals(2, clip.getKeyframes().size()); var inserted = clip.findKeyframeAt(2, 1e-4f);
        assertNotNull(inserted); assertEquals(new Vector3f(7,8,9), inserted.getPosition());
        assertEquals(new Vector3f(2,3,4), inserted.getScale()); assertEquals(new Vector3f(50,60,70), inserted.getRotation());
        assertEquals(2, clip.getTimelineClip().getClipKeyframes().size());
        history.undo(); assertEquals(1, clip.getKeyframes().size()); assertSame(key, clip.getKeyframes().getFirst());
        assertEquals(1, clip.getTimelineClip().getClipKeyframes().size());
        history.redo(); assertSame(inserted, clip.findKeyframeAt(2, 1e-4f));
    }
    @Test void sourceKeyChangesInvalidateDraft() {
        var clip = source(); var key = new EntityClipKeyframe(2); clip.addKeyframe(key);
        var edit = session(clip); edit.draft().setToPos(9,9,9); key.setScale(2,2,2);
        assertNull(edit.commit()); assertEquals(0, history.getUndoSize()); assertEquals(new Vector3f(2), key.getScale());
    }
    @Test void keyedValueCanReturnToEndpointValueWithoutBeingMistakenForNoOp() {
        var clip = source(); clip.setFromPos(1,1,1); clip.setToPos(1,1,1);
        var key = new EntityClipKeyframe(2); key.setPosition(2,2,2); clip.addKeyframe(key);
        var edit = session(clip); edit.draft().setFromPos(2,2,2); edit.draft().setToPos(2,2,2); edit.arm();
        edit.draft().setFromPos(1,1,1); edit.draft().setToPos(1,1,1);
        assertNotNull(edit.commit()); assertEquals(new Vector3f(1), key.getPosition());
        history.undo(); assertEquals(new Vector3f(2), key.getPosition());
    }
    @Test void contextRoundTripCancelsWithoutUndoEntry() {
        var edit=session(source()); edit.draft().setToPos(9,0,0); model.setCurrentTime(3); model.setCurrentTime(2); assertNull(edit.commit());
        assertEquals(0,history.getUndoSize());
    }
}
