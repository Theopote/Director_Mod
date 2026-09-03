package com.director.ui.actor.pose;

import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationLoopMode;
import com.director.actor.animation.asset.InterpolationMode;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorAnimationKeyframeEditingTest {
    @AfterEach
    void unbindHistory() {
        TimelineCommandBridge.bind(null);
    }

    @Test
    void movesRotationKeyframeWithUndoAndRedo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();
        track.rotation().put(1f, new Quaternionf().rotateX(0.5f), InterpolationMode.SMOOTH);

        assertTrue(ActorAnimationKeyframeEditing.moveRotation(model, track, 1f, 2f));
        assertTrue(track.rotation().findAt(2f).isPresent());
        assertTrue(history.undo());
        assertTrue(track.rotation().findAt(1f).isPresent());
        assertTrue(history.redo());
        assertTrue(track.rotation().findAt(2f).isPresent());
    }

    @Test
    void refusesToOverwriteExistingKeyframe() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        BoneTrack track = new BoneTrack();
        track.rotation().put(1f, new Quaternionf(), InterpolationMode.LINEAR);
        track.rotation().put(2f, new Quaternionf(), InterpolationMode.LINEAR);

        assertFalse(ActorAnimationKeyframeEditing.moveRotation(model, track, 1f, 2f));
        assertTrue(track.rotation().findAt(1f).isPresent());
        assertTrue(track.rotation().findAt(2f).isPresent());
    }

    @Test
    void deletesRotationKeyframeWithUndo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();
        track.rotation().put(1f, new Quaternionf(), InterpolationMode.STEP);

        assertTrue(ActorAnimationKeyframeEditing.deleteRotation(model, track, 1f));
        assertTrue(track.rotation().findAt(1f).isEmpty());
        assertTrue(history.undo());
        assertTrue(track.rotation().findAt(1f).isPresent());
    }

    @Test
    void movesMultipleKeysAsOneUndoTransaction() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack head = new BoneTrack();
        BoneTrack arm = new BoneTrack();
        head.rotation().put(1f, new Quaternionf(), InterpolationMode.LINEAR);
        arm.rotation().put(2f, new Quaternionf(), InterpolationMode.SMOOTH);

        assertTrue(ActorAnimationKeyframeEditing.moveRotations(model, List.of(
                new ActorAnimationKeyframeEditing.RotationKey(head, 1f),
                new ActorAnimationKeyframeEditing.RotationKey(arm, 2f)), 0.5f));
        assertTrue(head.rotation().findAt(1.5f).isPresent());
        assertTrue(arm.rotation().findAt(2.5f).isPresent());
        assertTrue(history.undo());
        assertTrue(head.rotation().findAt(1f).isPresent());
        assertTrue(arm.rotation().findAt(2f).isPresent());
    }

    @Test
    void batchMoveIsRejectedWhenAnyTargetIsOccupied() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        BoneTrack track = new BoneTrack();
        track.rotation().put(1f, new Quaternionf(), InterpolationMode.LINEAR);
        track.rotation().put(2f, new Quaternionf(), InterpolationMode.LINEAR);
        track.rotation().put(3f, new Quaternionf(), InterpolationMode.LINEAR);

        assertFalse(ActorAnimationKeyframeEditing.moveRotations(model, List.of(
                new ActorAnimationKeyframeEditing.RotationKey(track, 1f),
                new ActorAnimationKeyframeEditing.RotationKey(track, 2f)), 1f));
        assertTrue(track.rotation().findAt(1f).isPresent());
        assertTrue(track.rotation().findAt(2f).isPresent());
        assertTrue(track.rotation().findAt(3f).isPresent());
    }

    @Test
    void pastesMultipleKeysWithOneUndo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack head = new BoneTrack();
        BoneTrack arm = new BoneTrack();

        assertTrue(ActorAnimationKeyframeEditing.pasteRotations(model, List.of(
                new ActorAnimationKeyframeEditing.RotationPaste(
                        head, 4f, new Quaternionf().rotateY(0.5f), InterpolationMode.LINEAR),
                new ActorAnimationKeyframeEditing.RotationPaste(
                        arm, 5f, new Quaternionf().rotateX(0.25f), InterpolationMode.SMOOTH))));
        assertTrue(head.rotation().findAt(4f).isPresent());
        assertTrue(arm.rotation().findAt(5f).isPresent());
        assertTrue(history.undo());
        assertTrue(head.rotation().findAt(4f).isEmpty());
        assertTrue(arm.rotation().findAt(5f).isEmpty());
    }

    @Test
    void canvasDragSnapsToProjectFrameStep() {
        assertEquals(0.1f, ActorAnimationDopeSheet.snapDragDelta(12f, 100f, 0.05f), 1.0e-5f);
        assertEquals(-0.15f, ActorAnimationDopeSheet.snapDragDelta(-14f, 100f, 0.05f), 1.0e-5f);
        assertEquals(0f, ActorAnimationDopeSheet.snapDragDelta(20f, 0f, 0.05f), 1.0e-5f);
    }

    @Test
    void canvasZoomPreservesTimeUnderMouseAnchor() {
        float oldStart = 2f;
        float duration = 10f;
        float anchor = 0.25f;
        float pivotBefore = oldStart + ActorAnimationDopeSheet.visibleDuration(duration, 2f) * anchor;
        float newStart = ActorAnimationDopeSheet.zoomedStart(oldStart, duration, 2f, 4f, anchor);
        float pivotAfter = newStart + ActorAnimationDopeSheet.visibleDuration(duration, 4f) * anchor;

        assertEquals(pivotBefore, pivotAfter, 1.0e-5f);
        assertEquals(2.625f, newStart, 1.0e-5f);
    }

    @Test
    void canvasZoomStartIsClampedToAnimationBounds() {
        assertEquals(0f, ActorAnimationDopeSheet.zoomedStart(0f, 10f, 1f, 5f, 0f), 1.0e-5f);
        assertEquals(8f, ActorAnimationDopeSheet.zoomedStart(9f, 10f, 5f, 5f, 1f), 1.0e-5f);
    }

    @Test
    void quaternionMirrorKeepsPitchAndReversesYawAndRoll() {
        Quaternionf source = new Quaternionf().rotateXYZ(0.2f, 0.3f, -0.4f);
        Quaternionf mirrored = ActorAnimationKeyframeEditing.mirrorAcrossX(source);
        org.joml.Vector3f angles = mirrored.getEulerAnglesXYZ(new org.joml.Vector3f());

        assertEquals(0.2f, angles.x, 1.0e-4f);
        assertEquals(-0.3f, angles.y, 1.0e-4f);
        assertEquals(0.4f, angles.z, 1.0e-4f);
    }

    @Test
    void mirrorsAndSwapsHumanoidLimbsWithUndo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        AnimationAsset asset = new AnimationAsset("test:mirror", "Mirror", HumanoidRig.DEFINITION,
                5f, AnimationLoopMode.ONCE);
        Quaternionf leftBefore = new Quaternionf().rotateXYZ(0.2f, 0.3f, 0.4f);
        Quaternionf rightBefore = new Quaternionf().rotateXYZ(-0.1f, 0.5f, -0.2f);
        asset.getOrCreateBoneTrack(HumanoidRig.LEFT_ARM).rotation().put(1f, leftBefore, InterpolationMode.LINEAR);
        asset.getOrCreateBoneTrack(HumanoidRig.RIGHT_ARM).rotation().put(1f, rightBefore, InterpolationMode.SMOOTH);

        assertTrue(ActorAnimationKeyframeEditing.mirrorHumanoidPoses(
                model, asset, List.of(1f), InterpolationMode.SMOOTH));
        Quaternionf mirroredLeft = asset.findBoneTrack(HumanoidRig.LEFT_ARM).orElseThrow()
                .rotation().findAt(1f).orElseThrow().value();
        Quaternionf mirroredRight = asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow()
                .rotation().findAt(1f).orElseThrow().value();
        assertQuaternionEquivalent(ActorAnimationKeyframeEditing.mirrorAcrossX(rightBefore), mirroredLeft);
        assertQuaternionEquivalent(ActorAnimationKeyframeEditing.mirrorAcrossX(leftBefore), mirroredRight);

        assertTrue(history.undo());
        assertQuaternionEquivalent(leftBefore, asset.findBoneTrack(HumanoidRig.LEFT_ARM).orElseThrow()
                .rotation().findAt(1f).orElseThrow().value());
        assertQuaternionEquivalent(rightBefore, asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow()
                .rotation().findAt(1f).orElseThrow().value());
        assertTrue(asset.findBoneTrack(HumanoidRig.BODY).orElseThrow().rotation().findAt(1f).isEmpty());
    }

    @Test
    void movesTranslationKeysWithUndoAndConflictProtection() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();
        track.translation().put(1f, new Vector3f(1f, 2f, 3f), InterpolationMode.LINEAR);
        track.translation().put(3f, new Vector3f(3f, 2f, 1f), InterpolationMode.SMOOTH);

        assertTrue(ActorAnimationKeyframeEditing.moveTranslations(model, List.of(
                new ActorAnimationKeyframeEditing.TranslationKey(track, 1f)), 1f));
        assertTrue(track.translation().findAt(2f).isPresent());
        assertFalse(ActorAnimationKeyframeEditing.moveTranslations(model, List.of(
                new ActorAnimationKeyframeEditing.TranslationKey(track, 2f)), 1f));
        assertTrue(history.undo());
        assertTrue(track.translation().findAt(1f).isPresent());
    }

    @Test
    void pastesAndDeletesTranslationKeysWithUndo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();

        assertTrue(ActorAnimationKeyframeEditing.pasteTranslations(model, List.of(
                new ActorAnimationKeyframeEditing.TranslationPaste(
                        track, 2f, new Vector3f(4f, 5f, 6f), InterpolationMode.SMOOTH))));
        assertTrue(track.translation().findAt(2f).isPresent());
        assertTrue(ActorAnimationKeyframeEditing.deleteTranslations(model, List.of(
                new ActorAnimationKeyframeEditing.TranslationKey(track, 2f))));
        assertTrue(track.translation().findAt(2f).isEmpty());
        assertTrue(history.undo());
        assertTrue(track.translation().findAt(2f).isPresent());
    }

    @Test
    void movesScaleKeysWithUndoAndConflictProtection() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();
        track.scale().put(1f, new Vector3f(1f), InterpolationMode.LINEAR);
        track.scale().put(3f, new Vector3f(2f), InterpolationMode.SMOOTH);

        assertTrue(ActorAnimationKeyframeEditing.moveScales(model, List.of(
                new ActorAnimationKeyframeEditing.ScaleKey(track, 1f)), 1f));
        assertTrue(track.scale().findAt(2f).isPresent());
        assertFalse(ActorAnimationKeyframeEditing.moveScales(model, List.of(
                new ActorAnimationKeyframeEditing.ScaleKey(track, 2f)), 1f));
        assertTrue(history.undo());
        assertTrue(track.scale().findAt(1f).isPresent());
    }

    @Test
    void pastesAndDeletesScaleKeysWithUndo() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        BoneTrack track = new BoneTrack();

        assertTrue(ActorAnimationKeyframeEditing.pasteScales(model, List.of(
                new ActorAnimationKeyframeEditing.ScalePaste(
                        track, 2f, new Vector3f(1.5f, 2f, 0.5f), InterpolationMode.SMOOTH))));
        assertEquals(new Vector3f(1.5f, 2f, 0.5f), track.scale().findAt(2f).orElseThrow().value());
        assertTrue(ActorAnimationKeyframeEditing.deleteScales(model, List.of(
                new ActorAnimationKeyframeEditing.ScaleKey(track, 2f))));
        assertTrue(track.scale().findAt(2f).isEmpty());
        assertTrue(history.undo());
        assertTrue(track.scale().findAt(2f).isPresent());
    }

    private static void assertQuaternionEquivalent(Quaternionf expected, Quaternionf actual) {
        assertEquals(1f, Math.abs(expected.dot(actual)), 1.0e-5f);
    }
}
