package com.director.actor.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLibraryTest {
    @Test
    void providesUsableBuiltInAnimations() {
        AnimationLibrary library = AnimationLibrary.getInstance();
        assertTrue(library.get("idle").isPresent());
        assertTrue(library.get("walk").isPresent());
        assertTrue(library.get("wave").isPresent());
        assertTrue(library.get("attack").isPresent());
    }
}
