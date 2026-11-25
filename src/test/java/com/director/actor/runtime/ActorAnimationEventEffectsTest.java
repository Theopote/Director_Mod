package com.director.actor.runtime;

import com.director.timeline.model.entity.EntityActionClip;
import com.director.timeline.playback.camera.CameraSample;
import com.director.timeline.playback.entity.EntityActionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorAnimationEventEffectsTest {
    @AfterEach
    void reset() {
        ActorAnimationEventEffects.getInstance().reset();
    }

    @Test
    void cameraShakeOnlyAppliesThroughDecorate() {
        var effects = ActorAnimationEventEffects.getInstance();
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE,
                Map.of("duration", 1f, "intensity", .5f, "frequency", 10f)), 0f);

        CameraSample base = CameraSample.EMPTY;
        CameraSample decorated = effects.decorate(base, 0.1f);
        assertNotSame(base, decorated);
        assertNotEquals(base.yaw(), decorated.yaw());
    }

    @Test
    void decorateIsIdentityOutsideShakeWindow() {
        var effects = ActorAnimationEventEffects.getInstance();
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE,
                Map.of("duration", .2f, "intensity", .5f, "frequency", 10f)), 1f);

        CameraSample base = CameraSample.EMPTY;
        assertSame(base, effects.decorate(base, 0f));
        assertSame(base, effects.decorate(base, 2f));
    }

    @Test
    void multipleOwnersCombineWithEnergyNorm() {
        var effects = ActorAnimationEventEffects.getInstance();
        var params = Map.<String, Object>of("duration", 1f, "intensity", .5f, "frequency", 10f);
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE, params),
                0f, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        CameraSample one = effects.decorate(CameraSample.EMPTY, 0.1f);
        float oneYaw = Math.abs(one.yaw());

        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE, params),
                0f, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        CameraSample two = effects.decorate(CameraSample.EMPTY, 0.1f);
        float twoYaw = Math.abs(two.yaw());

        // 1/√n keeps two sources near one source rather than ~2× linear sum
        assertTrue(twoYaw < oneYaw * 1.6f);
        assertTrue(twoYaw > oneYaw * 0.9f);
    }

    @Test
    void sameOwnerReplacesRatherThanStacks() {
        var effects = ActorAnimationEventEffects.getInstance();
        var owner = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE,
                Map.of("duration", 1f, "intensity", .2f, "frequency", 10f)), 0f, owner);
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE,
                Map.of("duration", 1f, "intensity", .8f, "frequency", 10f)), 0f, owner);
        CameraSample decorated = effects.decorate(CameraSample.EMPTY, 0.1f);
        // Single remaining shake uses the replacement intensity (.8), not stacked.
        effects.reset();
        effects.dispatch(null, new EntityActionEvent(EntityActionClip.ActionType.CAMERA_SHAKE,
                Map.of("duration", 1f, "intensity", .8f, "frequency", 10f)), 0f, owner);
        CameraSample expected = effects.decorate(CameraSample.EMPTY, 0.1f);
        assertEquals(expected.yaw(), decorated.yaw(), 1e-5);
        assertEquals(expected.pitch(), decorated.pitch(), 1e-5);
    }
}
