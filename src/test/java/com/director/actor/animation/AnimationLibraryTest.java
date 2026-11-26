package com.director.actor.animation;

import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void clearRestoresBuiltIns() {
        AnimationLibrary library = AnimationLibrary.getInstance();
        library.clear();
        assertTrue(library.get("idle").isPresent());
        assertTrue(library.get("walk").isPresent());
    }

    @Test
    void timelineSeekingWrapsLoopingAnimationsDeterministically() {
        DefaultAnimationPlayer player = new DefaultAnimationPlayer();
        player.play("walk", true, 1f);

        player.setCurrentTime(1.25f);

        assertEquals(0.25f, player.getCurrentTime(), 1.0e-5f);
        var result = player.getCurrentPose();
        assertTrue(result != null);
        assertTrue(result.mask.arms);
        assertTrue(result.mask.legs);
        assertFalse(result.mask.body);
        assertFalse(result.mask.head);
        assertTrue(AnimationLibrary.getInstance().get("walk").orElseThrow()
                .controlledBones().contains(HumanoidRig.LEFT_LEG));
    }
}
