package com.director.actor.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationLibraryTest {
    @Test
    void providesUsableBuiltInAnimations() {
        AnimationLibrary library = AnimationLibrary.getInstance();
        assertTrue(library.get("idle").isPresent());
        assertTrue(library.get("walk").isPresent());
        assertTrue(library.get("wave").isPresent());
        assertTrue(library.get("attack").isPresent());
    }

    @Test
    void timelineSeekingWrapsLoopingAnimationsDeterministically() {
        DefaultAnimationPlayer player = new DefaultAnimationPlayer();
        player.play("walk", true, 1f);

        player.setCurrentTime(1.25f);

        assertEquals(0.25f, player.getCurrentTime(), 1.0e-5f);
        assertTrue(player.getCurrentPose() != null);
    }
}
