package com.director.actor.animation.asset;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BezierOvershootTest {
    @Test
    void permitsProgressOutsideEndpointsAndPreservesTimeBounds() {
        assertTrue(new BezierEase(.3f, -2f, .7f, -2f).map(.5f) < 0f);
        assertTrue(new BezierEase(.3f, 3f, .7f, 3f).map(.5f) > 1f);
        assertThrows(IllegalArgumentException.class, () -> new BezierEase(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BezierEase(0, Float.NaN, 1, 1));
    }

    @Test
    void extrapolatesTransformsWhileKeepingPositiveScale() {
        assertEquals(2f, ChannelValueAdapters.VECTOR3.interpolate(new Vector3f(), new Vector3f(1), 2).x);
        var rotation = ChannelValueAdapters.QUATERNION.interpolate(new Quaternionf(),
                new Quaternionf().rotateY(.5f), 2);
        assertTrue(rotation.isFinite());
        assertEquals(1f, rotation.angle(), .001f);
        var scale = ChannelValueAdapters.SCALE.interpolate(new Vector3f(1), new Vector3f(.1f), 2);
        assertTrue(scale.x > 0 && scale.y > 0 && scale.z > 0);
    }
}
