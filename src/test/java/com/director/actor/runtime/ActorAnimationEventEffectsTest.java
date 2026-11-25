package com.director.actor.runtime;

import com.director.timeline.model.entity.EntityActionClip;
import com.director.timeline.playback.camera.CameraSample;
import com.director.timeline.playback.entity.EntityActionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
