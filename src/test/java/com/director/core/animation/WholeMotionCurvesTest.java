package com.director.core.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeMotionCurvesTest {
    @Test void springHasRestingEndpointsAndIncreasingDampingReducesOvershoot() {
        for (int cycles = 1; cycles <= 8; cycles++) {
            assertEquals(0, WholeMotionCurves.spring(0, cycles, 6));
            assertEquals(1, WholeMotionCurves.spring(1, cycles, 6));
            assertEquals(0, WholeMotionCurves.spring(0.00001f, cycles, 6), 0.000001);
            assertEquals(1, WholeMotionCurves.spring(0.99999f, cycles, 6), 0.000001);
        }
        assertTrue(WholeMotionCurves.spring(1f / 6, 3, 2)
                > WholeMotionCurves.spring(1f / 6, 3, 10));
    }
    @Test void ballisticHopsShortenAndLoseHeightAfterFirstContact() {
        int cycles = 3;
        double r = Math.exp(-0.6);
        double sum = 1 + r + r * r;
        double start = 0.4;
        float previous = 2;
        double previousDuration = 1;
        assertEquals(0, WholeMotionCurves.bounce(0.3f, cycles, 6));
        assertEquals(1, WholeMotionCurves.arrival(0.4f));
        assertEquals(0.25, WholeMotionCurves.arrival(0.2f), 0.00001);
        for (int i = 0; i < cycles; i++) {
            double duration = 0.6 * Math.pow(r, i) / sum;
            float peak = WholeMotionCurves.bounce((float) (start + duration / 2), cycles, 6);
            assertEquals(Math.pow(r, 2 * i), peak, 0.00001);
            assertTrue(peak < previous);
            assertTrue(duration < previousDuration);
            assertEquals(0, WholeMotionCurves.bounce((float) start, cycles, 6), 0.00001);
            previous = peak; previousDuration = duration; start += duration;
        }
        assertEquals(0, WholeMotionCurves.bounce(1, cycles, 6));
        assertEquals(0, WholeMotionCurves.bounce(0.999999f, cycles, 6), 0.000001);
    }
    @Test void dampingAndDebrisValuesHaveLegacyDefaultsAndFiniteBounds() {
        var config = new WholeContainerAnimationConfig();
        assertEquals(6, config.getMotionDamping());
        assertEquals(0.8f, config.getDebrisRatio());
        config.setMotionDamping(Float.NaN); config.setDebrisRatio(Float.POSITIVE_INFINITY);
        assertEquals(6, config.getMotionDamping()); assertEquals(0.8f, config.getDebrisRatio());
        config.setMotionDamping(-1); config.setDebrisRatio(-1);
        assertEquals(0.5f, config.getMotionDamping()); assertEquals(0, config.getDebrisRatio());
        config.setMotionDamping(20); config.setDebrisRatio(20);
        assertEquals(12, config.getMotionDamping()); assertEquals(1, config.getDebrisRatio());
    }
}
