package com.director.ui.actor.pose;

import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.InterpolationMode;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
