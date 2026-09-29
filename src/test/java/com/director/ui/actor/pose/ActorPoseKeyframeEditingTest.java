package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;

class ActorPoseKeyframeEditingTest {
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); }

    @Test void fullPoseUndoRestoresExistingKeysAndRemovesNewTracks() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var asset = new AnimationAsset("project:test", "Test", HumanoidRig.DEFINITION, 5f, AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack(HumanoidRig.HEAD).rotation().put(1f, new Quaternionf().rotateX(.3f), InterpolationMode.STEP);
        var pose = new RigPose(HumanoidRig.DEFINITION);
        pose.setLocalTransform(HumanoidRig.HEAD, pose.localTransform(HumanoidRig.HEAD).withRotation(new Quaternionf().rotateY(.7f)));
        ActorPoseKeyframeEditing.key(model, asset, 1f, pose,
                asset.rig().bones().stream().map(BoneDefinition::id).toList(), InterpolationMode.SMOOTH);
        assertEquals(7, asset.boneTracks().size());
        assertTrue(asset.findBoneTrack(HumanoidRig.ROOT).orElseThrow().rotation().isEmpty());
        assertTrue(history.undo());
        assertEquals(1, asset.boneTracks().size());
        var restored = asset.findBoneTrack(HumanoidRig.HEAD).orElseThrow();
        assertTrue(restored.scale().isEmpty());
        assertEquals(InterpolationMode.STEP, restored.rotation().findAt(1f).orElseThrow().interpolation());
        assertEquals(1f, Math.abs(new Quaternionf().rotateX(.3f).dot(restored.rotation().findAt(1f).orElseThrow().value())), 1e-5f);
        assertTrue(history.redo());
        assertEquals(7, asset.boneTracks().size());
    }

    @Test void rotationOnlyKeyLeavesOtherChannelsUnauthoredAndSupportsUndo() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var asset = new AnimationAsset("project:sparse", "Sparse", HumanoidRig.DEFINITION, 5f, AnimationLoopMode.ONCE);
        var pose = new RigPose(HumanoidRig.DEFINITION);
        ActorPoseKeyframeEditing.keyChannels(model, asset, 1f, pose,
                java.util.List.of(HumanoidRig.HEAD), InterpolationMode.LINEAR, RigPose.ROTATION);
        var track = asset.findBoneTrack(HumanoidRig.HEAD).orElseThrow();
        assertTrue(track.translation().isEmpty());
        assertTrue(track.scale().isEmpty());
        assertTrue(track.rotation().findAt(1f).isPresent());
        assertTrue(history.undo());
        assertTrue(asset.boneTracks().isEmpty());
        assertTrue(history.redo());
        assertTrue(asset.findBoneTrack(HumanoidRig.HEAD).orElseThrow().translation().isEmpty());
    }

    @Test void unsupportedChannelDoesNotCreateTrackOrUndoEntry() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var asset = new AnimationAsset("project:root", "Root", HumanoidRig.DEFINITION, 5f, AnimationLoopMode.ONCE);
        ActorPoseKeyframeEditing.keyChannels(model, asset, 1f, new RigPose(HumanoidRig.DEFINITION),
                java.util.List.of(HumanoidRig.ROOT), InterpolationMode.LINEAR, RigPose.ROTATION);
        assertTrue(asset.boneTracks().isEmpty());
        assertFalse(history.undo());
    }
}
