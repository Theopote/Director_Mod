package com.director.actor.preview;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorStandEulerAnglesTest {
    @Test
    void roundTripsCompoundRotationThroughModelPartConvention() {
        Quaternionf expected = new Quaternionf()
                .rotateX((float)Math.toRadians(45f))
                .rotateY((float)Math.toRadians(45f))
                .rotateZ((float)Math.toRadians(-25f));

        Vector3f euler = ArmorStandEulerAngles.fromQuaternion(expected);
        Quaternionf rendered = new Quaternionf().rotationZYX(euler.z, euler.y, euler.x);

        assertEquals(1f, Math.abs(expected.dot(rendered)), 1.0e-5f);
    }

    @Test
    void previousXyzDecompositionDoesNotRoundTripCompoundRotation() {
        Quaternionf expected = new Quaternionf()
                .rotateX((float)Math.toRadians(45f))
                .rotateY((float)Math.toRadians(45f));
        Vector3f oldEuler = expected.getEulerAnglesXYZ(new Vector3f());
        Quaternionf rendered = new Quaternionf().rotationZYX(oldEuler.z, oldEuler.y, oldEuler.x);

        assertTrue(Math.abs(expected.dot(rendered)) < 0.999f);
    }
}
