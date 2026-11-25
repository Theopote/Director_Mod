package com.director.actor.prop;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import com.director.timeline.model.entity.EntityActionClip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEventParametersTest {
    @Test
    void exposesNonPropEffects() {
        assertTrue(AnimationEventParameters.supports(EntityActionClip.ActionType.PLAY_SOUND));
        assertTrue(AnimationEventParameters.supports(EntityActionClip.ActionType.SPAWN_PARTICLE));
        assertTrue(AnimationEventParameters.supports(EntityActionClip.ActionType.CAMERA_SHAKE));
        assertTrue(AnimationEventParameters.supports(EntityActionClip.ActionType.SET_VISIBILITY));
    }

    @Test
    void validatesNewEventParameters() {
        var actor = new Actor("Player", ActorType.PLAYER);
        assertDoesNotThrow(() -> AnimationEventParameters.validateActor(actor,
                EntityActionClip.ActionType.PLAY_SOUND,
                Map.of("soundId", "minecraft:entity.player.attack.sweep", "volume", .8f, "pitch", 1f)));
        assertDoesNotThrow(() -> AnimationEventParameters.validateActor(actor,
                EntityActionClip.ActionType.SPAWN_PARTICLE,
                Map.of("particle", "CRIT", "count", 4, "spread", .2f, "speed", .05f)));
        assertThrows(IllegalArgumentException.class, () -> AnimationEventParameters.validateActor(actor,
                EntityActionClip.ActionType.PLAY_SOUND, Map.of("soundId", "not a namespace id")));
        assertThrows(IllegalArgumentException.class, () -> AnimationEventParameters.validateActor(actor,
                EntityActionClip.ActionType.SPAWN_PARTICLE, Map.of("particle", "UNKNOWN")));
        assertThrows(IllegalArgumentException.class, () -> AnimationEventParameters.validateActor(actor,
                EntityActionClip.ActionType.CAMERA_SHAKE, Map.of("duration", 0f)));
    }

    @Test
    void particleIdsMatchValidationSet() {
        assertEquals(AnimationEventParameters.PARTICLE_IDS.size(), AnimationEventParameters.PARTICLES.size());
        assertTrue(AnimationEventParameters.PARTICLES.containsAll(AnimationEventParameters.PARTICLE_IDS));
        assertTrue(AnimationEventParameters.PARTICLES.contains("CRIT"));
        assertTrue(AnimationEventParameters.PARTICLES.contains("POOF"));
        assertTrue(AnimationEventParameters.PARTICLES.contains("END_ROD"));
    }
}
