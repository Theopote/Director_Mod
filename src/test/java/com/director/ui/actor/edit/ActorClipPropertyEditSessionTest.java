package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorClipPropertyEditSessionTest {
    TimelineModel model; TimelineTrack track; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(5);
        track = new TimelineTrack("Actor", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    void attach(EntityClip clip) { track.getActorTrackPayload().addClip(clip); track.addClip(new EntityTimelineClip(clip, 0)); }
    EntityTransformClip transform() {
        var clip = new EntityTransformClip("Earlier", 0, 1); clip.setFromPos(1,2,3); clip.setToPos(4,5,6);
        clip.setPathId("legacy"); clip.setFollowPath(true); attach(clip); return clip;
    }
    EntityLookAtClip look() { var clip = new EntityLookAtClip("Look", 0,1); attach(clip); return clip; }
    @Test void editsExplicitInactiveClipInsteadOfActiveClipAtPlayhead() {
        var source = transform(); var active = new EntityTransformClip("Active", 4,6); attach(active);
        var edit = new ActorClipPropertyEditSession(model, source);
        ((EntityTransformClip) edit.draft()).setToPos(10,20,30); assertTrue(edit.commit());
        assertEquals(new Vector3f(10,20,30), source.getToPos()); assertEquals(new Vector3f(), active.getToPos());
        assertEquals(1, history.getUndoSize()); history.undo(); assertEquals(new Vector3f(4,5,6), source.getToPos());
    }
    @Test void manyDraftUpdatesCommitOnceAndUndoRetainsWrapperAndMetadata() {
        var source = transform(); var wrapper = source.getTimelineClip(); var edit = new ActorClipPropertyEditSession(model, source);
        var draft = (EntityTransformClip) edit.draft();
        for (int i=0; i<100; i++) draft.setFromPos(i,2,3);
        assertEquals(1, source.getFromPos().x); assertEquals(0, history.getUndoSize());
        assertTrue(edit.commit()); assertEquals(1, history.getUndoSize());
        history.undo(); assertEquals(1, source.getFromPos().x); history.redo(); assertEquals(99, source.getFromPos().x);
        assertSame(wrapper, source.getTimelineClip()); assertEquals("legacy", source.getPathId()); assertTrue(source.isFollowPath());
        assertEquals(0, source.getStartTime()); assertEquals(1, source.getEndTime());
    }
    @Test void noOpCancelAndReturningToOriginalDoNotCreateHistory() {
        var source = transform(); assertTrue(new ActorClipPropertyEditSession(model, source).commit());
        var edit = new ActorClipPropertyEditSession(model, source); ((EntityTransformClip)edit.draft()).getFromPos().set(8);
        edit.cancel(); assertFalse(edit.commit());
        edit = new ActorClipPropertyEditSession(model, source); ((EntityTransformClip)edit.draft()).setFromPos(1,2,3);
        assertTrue(edit.commit()); assertEquals(0, history.getUndoSize());
    }
    @Test void invalidCoordinatesScaleAndEmptyEntityTargetRejectAtomically() {
        var source = transform(); var edit = new ActorClipPropertyEditSession(model, source);
        ((EntityTransformClip)edit.draft()).setToPos(Float.NaN,0,0); assertThrows(IllegalArgumentException.class, edit::commit);
        edit = new ActorClipPropertyEditSession(model, source); ((EntityTransformClip)edit.draft()).setToScale(0,1,1);
        assertThrows(IllegalArgumentException.class, edit::commit); assertEquals(new Vector3f(4,5,6), source.getToPos());
        var look = look(); edit = new ActorClipPropertyEditSession(model, look);
        ((EntityLookAtClip)edit.draft()).setMode(EntityLookAtClip.TargetMode.ENTITY_UUID);
        assertThrows(IllegalArgumentException.class, edit::commit); assertEquals(EntityLookAtClip.TargetMode.CAMERA, look.getMode());
        assertEquals(0, history.getUndoSize());
    }
    @Test void keyedTransformRejectsEndpointEditsWithoutTouchingKeys() {
        var source = transform(); var key = new EntityClipKeyframe(.5f); key.setDisplayName("Keep"); source.addKeyframe(key);
        var edit = new ActorClipPropertyEditSession(model, source); ((EntityTransformClip)edit.draft()).setToPos(8,0,0);
        assertThrows(IllegalArgumentException.class, edit::commit); assertSame(key, source.getKeyframes().getFirst());
        assertEquals("Keep", key.getDisplayName()); assertEquals(0, history.getUndoSize());
    }
    @Test void strengthPreservesEntityTargetAndExistingKeyIdentity() {
        var source = look(); var target = UUID.randomUUID(); source.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID);
        source.setTargetEntityUuid(target); var key = new EntityClipKeyframe(.5f); source.addKeyframe(key);
        var edit = new ActorClipPropertyEditSession(model, source); ((EntityLookAtClip)edit.draft()).setStrength(.3f);
        assertTrue(edit.commit()); history.undo(); assertEquals(1, source.getStrength()); history.redo();
        assertEquals(.3f, source.getStrength()); assertEquals(target, source.getTargetEntityUuid());
        assertEquals(EntityLookAtClip.TargetMode.ENTITY_UUID, source.getMode()); assertSame(key, source.getKeyframes().getFirst());
    }
    @Test void assigningEntityTargetIsOneAtomicUndo() {
        var source = look(); var edit = new ActorClipPropertyEditSession(model, source); var draft = (EntityLookAtClip)edit.draft();
        var target = UUID.randomUUID(); draft.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID); draft.setTargetEntityUuid(target);
        assertTrue(edit.commit()); assertEquals(target, source.getTargetEntityUuid());
        history.undo(); assertNull(source.getTargetEntityUuid()); assertEquals(EntityLookAtClip.TargetMode.CAMERA, source.getMode());
        history.redo(); assertEquals(target, source.getTargetEntityUuid()); assertEquals(1, history.getUndoSize());
    }
    @Test void playheadLockPlaybackMuteAndActorRebindInvalidatePendingEdits() {
        var source = transform(); var edit = new ActorClipPropertyEditSession(model, source);
        model.setCurrentTime(6); assertFalse(edit.commit()); model.setCurrentTime(5);
        edit = new ActorClipPropertyEditSession(model, source); track.setLocked(true); assertFalse(edit.commit()); track.setLocked(false);
        edit = new ActorClipPropertyEditSession(model, source); model.setPlaying(true); assertFalse(edit.commit()); model.setPlaying(false);
        edit = new ActorClipPropertyEditSession(model, source); source.setMuted(true); assertFalse(edit.commit()); source.setMuted(false);
        edit = new ActorClipPropertyEditSession(model, source); track.getActorTrackPayload().setActorRef(new ActorRef(UUID.randomUUID()));
        assertFalse(edit.commit()); assertEquals(0, history.getUndoSize());
    }
    @Test void sourceCommonAndKeyMetadataChangesInvalidateDraft() {
        var source = look(); var key = new EntityClipKeyframe(.5f); source.addKeyframe(key);
        var edit = new ActorClipPropertyEditSession(model, source); key.setActionSpeed(1.5f); assertFalse(edit.commit());
        edit = new ActorClipPropertyEditSession(model, source); source.setWeight(.5f); assertFalse(edit.commit());
        edit = new ActorClipPropertyEditSession(model, source); source.setName("Renamed"); assertFalse(edit.commit());
        assertEquals(0, history.getUndoSize());
    }
    @Test void removalPayloadAndWrapperReplacementInvalidateBinding() {
        var source = transform(); var edit = new ActorClipPropertyEditSession(model, source);
        track.removeClip(source.getTimelineClip()); assertFalse(edit.commit());
        source = transform(); edit = new ActorClipPropertyEditSession(model, source);
        new EntityTimelineClip(source, 0); assertFalse(edit.commit());
        source = transform(); edit = new ActorClipPropertyEditSession(model, source);
        track.setActorTrackPayload(new ActorTrack()); assertFalse(edit.commit());
    }
    @Test void undoAndRedoCheckSourceAndKeepHistoryOnFailure() {
        var source = transform(); var edit = new ActorClipPropertyEditSession(model, source);
        ((EntityTransformClip)edit.draft()).setToPos(10,20,30); assertTrue(edit.commit());
        source.setToPos(99,20,30); assertThrows(IllegalArgumentException.class, history::undo); assertEquals(1, history.getUndoSize());
        source.setToPos(10,20,30); model.setCurrentTime(8); history.undo();
        track.setLocked(true); assertThrows(IllegalArgumentException.class, history::redo); assertEquals(1, history.getRedoSize());
        track.setLocked(false); history.redo(); assertEquals(10, source.getToPos().x);
    }
    @Test void selectionAwayAndBackCannotReviveOldDraft() {
        var source=transform(); model.setSelectedClip(source.getTimelineClip());
        var edit=new ActorClipPropertyEditSession(model,source); ((EntityTransformClip)edit.draft()).setToPos(9,0,0);
        model.clearSelection(); model.setSelectedClip(source.getTimelineClip());
        assertFalse(edit.commit()); assertEquals(4,source.getToPos().x); assertEquals(0,history.getUndoSize());
    }
    @Test void playbackAndPlayheadRoundTripsInvalidateGesture() {
        var source=transform(); var edit=new ActorClipPropertyEditSession(model,source);
        model.setPlaying(true); model.setPlaying(false); assertFalse(edit.commit());
        edit=new ActorClipPropertyEditSession(model,source); model.setCurrentTime(6); model.setCurrentTime(5);
        assertFalse(edit.commit()); assertEquals(0,history.getUndoSize());
    }
    @Test void redundantSelectionAndTimeUpdatesDoNotCancelAndUndoSurvivesSelectionChange() {
        var source=transform(); model.setSelectedClip(source.getTimelineClip());
        var edit=new ActorClipPropertyEditSession(model,source); ((EntityTransformClip)edit.draft()).setToPos(9,0,0);
        model.setSelectedClip(source.getTimelineClip()); model.setCurrentTime(5); model.setPlaying(false);
        assertTrue(edit.commit()); model.clearSelection(); history.undo(); assertEquals(4,source.getToPos().x);
        history.redo(); assertEquals(9,source.getToPos().x);
    }
    @Test void spawnBlendGestureUndoesWithoutCreatingClipsOrLosingAnimationBinding() {
        var source=new EntitySpawnClip("Spawn",0,10);source.setBoneAnimationId("project:test");attach(source);
        var edit=new ActorClipPropertyEditSession(model,source);
        var settings=new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.ADDITIVE,.4f,2,false,java.util.Set.of("head"));
        ((EntitySpawnClip)edit.draft()).setBlendSettings(settings);
        assertTrue(edit.commit());assertEquals(settings,source.getBlendSettings());assertEquals(1,history.getUndoSize());
        history.undo();assertEquals(com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT,source.getBlendSettings());
        history.redo();assertEquals(settings,source.getBlendSettings());assertEquals("project:test",source.getBoneAnimationId());
        assertEquals(1,track.getClips().size());assertEquals(1,track.getActorTrackPayload().getClips().size());
    }
    @Test void explicitAnimationEditsAreUndoableAndRetainEvents() {
        var source=new EntityAnimationClip("Animation",0,10);attach(source);
        var event=new AnimationEvent(.2f,new com.director.timeline.playback.entity.EntityActionEvent(EntityActionClip.ActionType.ATTACK,java.util.Map.of()));source.addEvent(event);
        var edit=new ActorClipPropertyEditSession(model,source);var draft=(EntityAnimationClip)edit.draft();
        draft.setBlendSettings(new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.OVERRIDE,.5f,2,true,java.util.Set.of()));
        draft.setSpeed(2);assertTrue(edit.commit());assertEquals(2,source.getSpeed());
        history.undo();assertEquals(1,source.getSpeed());history.redo();assertEquals(.5f,source.getBlendSettings().weight());
        assertSame(event,source.getEvents().getFirst());
    }
    @Test void changingOwnedAnimationInvalidatesPendingSpawnBlendGesture() {
        var source=new EntitySpawnClip("Spawn",0,10);source.setBoneAnimationId("project:first");attach(source);
        var edit=new ActorClipPropertyEditSession(model,source);source.setBoneAnimationId("project:other");
        assertFalse(edit.commit());assertEquals("project:other",source.getBoneAnimationId());assertEquals(0,history.getUndoSize());
    }
    @Test void fadeSettingsCommitOnceAndUndoRedoWithoutChangingKeysOrClipCount() {
        var source=new EntitySpawnClip("Spawn",0,10);source.setBoneAnimationId("project:test");attach(source);
        var key=new EntityClipKeyframe(.5f);source.addKeyframe(key);
        var edit=new ActorClipPropertyEditSession(model,source);
        var settings=new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.ADDITIVE,.4f,2,false,java.util.Set.of("head"),.2f,.4f);
        ((EntitySpawnClip)edit.draft()).setBlendSettings(settings);
        assertEquals(0,source.getBlendSettings().fadeIn());
        assertTrue(edit.commit());assertEquals(settings,source.getBlendSettings());assertEquals(1,history.getUndoSize());
        history.undo();assertEquals(0,source.getBlendSettings().fadeIn());assertEquals(0,source.getBlendSettings().fadeOut());
        history.redo();assertEquals(settings,source.getBlendSettings());
        assertSame(key,source.getKeyframes().getFirst());assertEquals("project:test",source.getBoneAnimationId());
        assertEquals(1,track.getClips().size());
        var animation=new EntityAnimationClip("Animation",0,10);attach(animation);
        edit=new ActorClipPropertyEditSession(model,animation);((EntityAnimationClip)edit.draft()).setBlendSettings(settings);
        assertTrue(edit.commit());assertEquals(settings,animation.getBlendSettings());
        history.undo();assertEquals(0,animation.getBlendSettings().fadeIn());
        history.redo();assertEquals(settings,animation.getBlendSettings());
    }
}
