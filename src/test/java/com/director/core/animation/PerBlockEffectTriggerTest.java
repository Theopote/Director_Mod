package com.director.core.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerBlockEffectTriggerTest {
    @Test void nonzeroTriggersObserveProgressBeforeTheirWindow() {
        assertFalse(PerBlockEffectTrigger.crossed(Float.NaN, 0.1f, 0.6f));
        assertFalse(PerBlockEffectTrigger.crossed(0.1f, 0.5f, 0.6f));
        assertTrue(PerBlockEffectTrigger.crossed(0.5f, 0.7f, 0.6f));
        assertFalse(PerBlockEffectTrigger.crossed(0.7f, 0.8f, 0.6f));
    }
    @Test void skippingEntireEffectWindowStillFiresOnceAndSeekingIntoMiddleDoesNotBurst() {
        assertTrue(PerBlockEffectTrigger.crossed(0.1f, 0.95f, 0.6f));
        assertFalse(PerBlockEffectTrigger.crossed(Float.NaN, 0.95f, 0.6f));
        assertFalse(PerBlockEffectTrigger.crossed(0.95f, 0.95f, 0.6f));
    }
    @Test void rewindingRearmsWithoutFiringDuringBackwardPlayback() {
        assertFalse(PerBlockEffectTrigger.crossed(0.9f, 0.2f, 0.6f));
        assertTrue(PerBlockEffectTrigger.crossed(0.2f, 0.7f, 0.6f));
        assertTrue(PerBlockEffectTrigger.crossed(1, 0, 0));
        assertFalse(PerBlockEffectTrigger.crossed(0, 0, 0));
    }
}
