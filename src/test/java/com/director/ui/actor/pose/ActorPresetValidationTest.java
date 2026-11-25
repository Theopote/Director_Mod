package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.PoseBlendSettings;
import com.director.actor.rig.skeleton.PoseLayer;
import com.director.actor.rig.skeleton.RigPose;
import com.director.timeline.model.entity.ActorPresetBinding;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorPresetValidationTest {
    @Test
    void rejectsSameActionOverrideEvenWhenPartial() {
        var secondary = new ActorPresetBinding("walk", false, 1f,
                new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE, 1f, RigPose.ROTATION, false, Set.of("head")));
        assertThrows(IllegalArgumentException.class,
                () -> ActorPresetValidation.requireDistinctFullOverride("walk", secondary));
    }

    @Test
    void allowsDifferentActionOverride() {
        var secondary = new ActorPresetBinding("wave", false, 1f, PoseBlendSettings.DEFAULT);
        assertDoesNotThrow(() -> ActorPresetValidation.requireDistinctFullOverride("walk", secondary));
    }

    @Test
    void allowsSameActionAdditive() {
        var secondary = new ActorPresetBinding("walk", false, 1f,
                new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE, .5f, RigPose.ROTATION, false, Set.of("head")));
        assertDoesNotThrow(() -> ActorPresetValidation.requireDistinctFullOverride("walk", secondary));
    }

    @Test
    void allowsZeroWeightSameActionOverride() {
        var secondary = new ActorPresetBinding("walk", false, 1f,
                new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE, 0f, RigPose.ALL_CHANNELS, true, Set.of()));
        assertDoesNotThrow(() -> ActorPresetValidation.requireDistinctFullOverride("walk", secondary));
    }
}
