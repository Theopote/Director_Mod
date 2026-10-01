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

class ActorNumericEditSessionTest {
    TimelineModel model; TimelineTrack track; TimelineCommandHistory history; UUID actorId;
    ActorTransformEditService.TransformSnapshot sample;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); actorId = actor.getActorId();
        ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actorId)); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        sample = new ActorTransformEditService.TransformSnapshot(new Vector3f(1,2,3), new Vector3f(10,20,30), new Vector3f(1));
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    ActorNumericEditSession session(ActorNumericEditSession.Kind kind) { return new ActorNumericEditSession(model, track, kind, sample); }
    EntityTransformClip transform() {
        var clip = new EntityTransformClip("Transform", 1, 3);
        clip.setFromPos(1,2,3); clip.setToPos(1,2,3); clip.setFromRot(10,20,30); clip.setToRot(10,20,30);
        attach(clip); return clip;
    }
    void attach(EntityClip clip) { track.getActorTrackPayload().addClip(clip); track.addClip(new EntityTimelineClip(clip, 0)); }
    @Test void repeatedTransformInputCommitsOnceWithUndoAndRedo() {
        var source = transform(); var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        for (int i = 0; i < 50; i++) edit.updateTransform(new Vector3f(i,2,3), sample.rotation(), sample.scale());
        assertEquals(1, source.getToPos().x); assertEquals(0, history.getUndoSize());
        edit.commit(); assertEquals(49, source.getToPos().x); assertEquals(1, history.getUndoSize());
        assertEquals(sample.rotation(), source.getToRot()); history.undo(); assertEquals(1, source.getToPos().x);
        history.redo(); assertEquals(49, source.getToPos().x);
    }
    @Test void strengthPreservesEntityTargetAndCommitsOnlyOnce() {
        var source = new EntityLookAtClip("Look", 1,3); var target = UUID.randomUUID();
        source.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID); source.setTargetEntityUuid(target); source.setStrength(.8f); attach(source);
        var edit = session(ActorNumericEditSession.Kind.LOOK_AT);
        for (int i = 0; i < 50; i++) edit.updateStrength(i / 100f);
        assertEquals(.8f, source.getStrength()); edit.commit(); assertEquals(.49f, source.getStrength());
        assertEquals(EntityLookAtClip.TargetMode.ENTITY_UUID, source.getMode()); assertEquals(target, source.getTargetEntityUuid());
        assertEquals(1, history.getUndoSize()); history.undo(); assertEquals(.8f, source.getStrength());
        history.redo(); assertEquals(.49f, source.getStrength()); assertEquals(target, source.getTargetEntityUuid());
    }
    @Test void noOpAndReturningToInitialValuesNeverCreateClips() {
        var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        edit.updateTransform(new Vector3f(8), sample.rotation(), sample.scale());
        edit.updateTransform(sample.position(), sample.rotation(), sample.scale()); edit.commit();
        edit = session(ActorNumericEditSession.Kind.LOOK_AT); edit.updateStrength(.5f); edit.updateStrength(1); edit.commit();
        assertEquals(0, history.getUndoSize()); assertTrue(track.getClips().isEmpty());
    }
    @Test void explicitCameraTargetCreatesClipButMereActivationDoesNot() {
        session(ActorNumericEditSession.Kind.LOOK_AT).commit(); assertTrue(track.getClips().isEmpty());
        var edit = session(ActorNumericEditSession.Kind.LOOK_AT);
        edit.updateLookTarget(EntityLookAtClip.TargetMode.CAMERA, new Vector3f()); edit.commit();
        assertEquals(1, history.getUndoSize()); assertEquals(1, track.getClips().size());
        history.undo(); assertTrue(track.getClips().isEmpty());
    }
    @Test void cancelAndContextChangesNeverWritePendingValues() {
        var source = transform(); var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        edit.updateTransform(new Vector3f(8), sample.rotation(), sample.scale()); edit.cancel(); edit.commit();
        assertEquals(1, source.getToPos().x);
        edit = session(ActorNumericEditSession.Kind.TRANSFORM); edit.updateTransform(new Vector3f(8), sample.rotation(), sample.scale());
        model.setCurrentTime(3); assertFalse(edit.isCurrent(model, track, actorId)); edit.commit();
        assertEquals(1, source.getToPos().x); assertEquals(0, history.getUndoSize());
    }
    @Test void differentOwnerActorPlaybackAndLockInvalidateGesture() {
        var edit = session(ActorNumericEditSession.Kind.LOOK_AT);
        assertFalse(edit.isCurrent(model, track, UUID.randomUUID()));
        assertFalse(edit.isCurrent(model, new TimelineTrack("Other", TrackType.ACTOR), actorId));
        model.setPlaying(true); assertFalse(edit.isCurrent(model, track, actorId)); model.setPlaying(false);
        track.setLocked(true); assertFalse(edit.isCurrent(model, track, actorId)); track.setLocked(false);
        track.getActorTrackPayload().setActorRef(new ActorRef(UUID.randomUUID())); assertFalse(edit.isCurrent(model, track, actorId));
    }
    @Test void invalidNumericInputCannotMutateSourceAndScaleIsBounded() {
        var source = transform(); var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        assertThrows(IllegalArgumentException.class, () -> edit.updateTransform(new Vector3f(Float.NaN), sample.rotation(), sample.scale()));
        assertEquals(1, source.getToPos().x);
        edit.updateTransform(sample.position(), sample.rotation(), new Vector3f(-1, 1000, 2)); edit.commit();
        assertEquals(new Vector3f(.01f,100,2), source.getToScale());
    }
    @Test void sourceChangeInvalidatesDraftAndMissingEntityTargetIsRejected() {
        var source = transform(); var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        edit.updateTransform(new Vector3f(8), sample.rotation(), sample.scale()); source.setToRot(30,40,50);
        assertFalse(edit.isCurrent(model, track, actorId)); edit.commit(); assertEquals(1, source.getToPos().x);
        var look = session(ActorNumericEditSession.Kind.LOOK_AT);
        assertThrows(IllegalArgumentException.class, () -> look.updateLookTarget(EntityLookAtClip.TargetMode.ENTITY_UUID, new Vector3f()));
        assertThrows(IllegalArgumentException.class, () -> look.updateStrength(Float.NaN));
        assertEquals(0, history.getUndoSize());
    }
    @Test void positionInputPreservesAnimatedRotationAndScaleEndpoints() {
        var source = transform(); source.setToRot(80,90,100); source.setToScale(3,4,5);
        var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        edit.updateTransform(new Vector3f(8,2,3), sample.rotation(), sample.scale()); edit.commit();
        assertEquals(new Vector3f(80,90,100), source.getToRot()); assertEquals(new Vector3f(3,4,5), source.getToScale());
        history.undo(); assertEquals(new Vector3f(80,90,100), source.getToRot());
    }
    @Test void returningOneChannelToSampleRestoresItsOriginalEndpoint() {
        var source = transform(); source.setToPos(10,20,30);
        var edit = session(ActorNumericEditSession.Kind.TRANSFORM);
        edit.updateTransform(new Vector3f(8), sample.rotation(), sample.scale());
        edit.updateTransform(sample.position(), new Vector3f(50), sample.scale()); edit.commit();
        assertEquals(new Vector3f(10,20,30), source.getToPos()); assertEquals(new Vector3f(50), source.getToRot());
    }
}
