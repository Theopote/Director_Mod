package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBoneDraftTransactionTest {
    private ActorBoneGizmoController controller;
    private TimelineModel model;
    private TimelineCommandHistory history;
    private AnimationAsset asset;
    private ActorTrack payload;
    private TimelineTrack track;
    private EntityAnimationClip clip;
    private BoneTrack draft;
    @BeforeEach void setup() throws Exception {
        Constructor<ActorBoneGizmoController> ctor = ActorBoneGizmoController.class.getDeclaredConstructor();
        ctor.setAccessible(true); controller = ctor.newInstance();
        model = new TimelineModel(new TimelineViewportState());
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        asset = new AnimationAsset("test:draft", "Draft", HumanoidRig.DEFINITION, 5, AnimationLoopMode.ONCE);
        payload = new ActorTrack(); track = new TimelineTrack("Actor", TrackType.ACTOR);
        track.setActorTrackPayload(payload); model.addTrack(track);
        clip = new EntityAnimationClip("Animation", 0, 5);
        clip.setAnimationId(asset.id()); new EntityTimelineClip(clip, payload.getColorRgba());
        draft = new BoneTrack(); draft.rotation().put(2, new Quaternionf().rotateX(0.5f), InterpolationMode.LINEAR);
        set("editingAsset", asset); set("editingBoneTrack", draft); set("editingAnimationClip", clip);
        set("actorTrack", payload); set("timelineTrack", track); set("boneId", "head");
        set("animationKeyTime", 2f); set("animationBaseRotation", new Quaternionf());
    }
    @AfterEach void cleanup() {
        TimelineCommandBridge.bind(null); AnimationAssetLibrary.getInstance().remove("test:draft");
    }
    private void set(String name, Object value) throws Exception {
        Field field = ActorBoneGizmoController.class.getDeclaredField(name); field.setAccessible(true); field.set(controller, value);
    }
    private void commit() throws Exception {
        Method method = ActorBoneGizmoController.class.getDeclaredMethod("registerAnimationUndo", TimelineModel.class);
        method.setAccessible(true); method.invoke(controller, model);
    }
    @Test void detachedDraftCreatesOneUndoableAssetClipAndBoneTrack() throws Exception {
        set("createdAsset", true); set("createdAnimationClip", true);
        assertTrue(asset.boneTracks().isEmpty()); assertTrue(payload.getClips().isEmpty());
        assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id()));
        commit();
        assertSame(asset, AnimationAssetLibrary.getInstance().find(asset.id()).orElseThrow());
        assertTrue(payload.getClips().contains(clip)); assertTrue(track.getClips().contains(clip.getTimelineClip()));
        assertEquals(InterpolationMode.LINEAR, asset.findBoneTrack("head").orElseThrow().rotation().findAt(2).orElseThrow().interpolation());
        assertTrue(history.undo()); assertTrue(asset.boneTracks().isEmpty());
        assertTrue(payload.getClips().isEmpty()); assertTrue(track.getClips().isEmpty());
        assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id())); assertFalse(history.undo());
        assertTrue(history.redo()); assertTrue(asset.findBoneTrack("head").isPresent());
        assertSame(clip, payload.getClips().getFirst());
    }
    @Test void existingSparseTrackRestoresOriginalKeyAndPreservesOtherChannels() throws Exception {
        BoneTrack original = asset.getOrCreateBoneTrack("head");
        original.rotation().put(2, new Quaternionf().rotateY(0.3f), InterpolationMode.STEP);
        original.translation().put(1, new org.joml.Vector3f(1,2,3), InterpolationMode.SMOOTH);
        var before = original.rotation().findAt(2).orElseThrow();
        set("hadBoneTrack", true); set("beforeRotationKey", before);
        commit(); assertSame(original, asset.findBoneTrack("head").orElseThrow());
        assertTrue(history.undo()); assertEquals(before, original.rotation().findAt(2).orElseThrow());
        assertEquals(new org.joml.Vector3f(1,2,3), original.translation().findAt(1).orElseThrow().value());
        assertTrue(history.redo()); assertEquals(InterpolationMode.LINEAR, original.rotation().findAt(2).orElseThrow().interpolation());
    }
    @Test void undoRemovesOnlyNewBoneTrackFromExistingAsset() throws Exception {
        asset.getOrCreateBoneTrack("body").rotation().put(0, new Quaternionf(), InterpolationMode.LINEAR);
        commit(); assertTrue(history.undo()); assertFalse(asset.findBoneTrack("head").isPresent());
        assertTrue(asset.findBoneTrack("body").isPresent()); assertTrue(history.redo());
    }
    @Test void autoKeyNeverChangesAuthoringTarget() {
        var settings = ActorAnimationAuthoringSettings.getInstance();
        var old = settings.getEditTarget(); boolean auto = settings.isAutoKeyframe();
        try {
            for (var target : ActorAnimationAuthoringSettings.EditTarget.values()) {
                settings.setEditTarget(target); settings.setAutoKeyframe(true); assertEquals(target, settings.getEditTarget());
                settings.setAutoKeyframe(false); assertEquals(target, settings.getEditTarget());
            }
        } finally { settings.setEditTarget(old); settings.setAutoKeyframe(auto); }
    }
}
