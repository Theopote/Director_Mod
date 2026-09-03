package com.director.actor.animation.asset;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyframeTrackTest {
    @Test
    void sortsReplacesAndDefensivelyCopiesValues() {
        KeyframeTrack<Vector3f> track = new KeyframeTrack<>(ChannelValueAdapters.VECTOR3);
        Vector3f source = new Vector3f(2f, 0f, 0f);
        track.put(2f, source, InterpolationMode.LINEAR);
        track.put(0f, new Vector3f(), InterpolationMode.LINEAR);
        track.put(2f, new Vector3f(4f, 0f, 0f), InterpolationMode.LINEAR);
        source.x = 99f;

        assertEquals(2, track.size());
        assertEquals(2f, track.keyframes().get(1).time(), 1.0e-5f);
        assertEquals(4f, track.sample(2f, new Vector3f()).x, 1.0e-5f);
    }

    @Test
    void supportsStepLinearAndSmoothSegments() {
        KeyframeTrack<Vector3f> step = vectorTrack(InterpolationMode.STEP);
        KeyframeTrack<Vector3f> linear = vectorTrack(InterpolationMode.LINEAR);
        KeyframeTrack<Vector3f> smooth = vectorTrack(InterpolationMode.SMOOTH);

        assertEquals(0f, step.sample(0.25f, new Vector3f()).x, 1.0e-5f);
        assertEquals(0.25f, linear.sample(0.25f, new Vector3f()).x, 1.0e-5f);
        assertEquals(0.15625f, smooth.sample(0.25f, new Vector3f()).x, 1.0e-5f);
    }

    @Test
    void quaternionSlerpUsesShortestRotationPath() {
        KeyframeTrack<Quaternionf> track = new KeyframeTrack<>(ChannelValueAdapters.QUATERNION);
        track.put(0f, new Quaternionf().rotateY((float) Math.toRadians(170)), InterpolationMode.LINEAR);
        track.put(1f, new Quaternionf().rotateY((float) Math.toRadians(-170)), InterpolationMode.LINEAR);

        Vector3f forward = track.sample(0.5f, new Quaternionf()).transform(new Vector3f(0f, 0f, -1f));

        assertEquals(0f, forward.x, 1.0e-4f);
        assertEquals(1f, forward.z, 1.0e-4f);
    }

    private static KeyframeTrack<Vector3f> vectorTrack(InterpolationMode mode) {
        KeyframeTrack<Vector3f> track = new KeyframeTrack<>(ChannelValueAdapters.VECTOR3);
        track.put(0f, new Vector3f(), mode);
        track.put(1f, new Vector3f(1f, 0f, 0f), InterpolationMode.LINEAR);
        return track;
    }
}
