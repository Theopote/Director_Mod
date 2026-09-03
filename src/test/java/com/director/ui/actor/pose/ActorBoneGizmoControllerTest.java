package com.director.ui.actor.pose;

import com.director.client.gizmo.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorBoneGizmoControllerTest {
    @Test
    void appliesAxisDeltaToGestureBaseRotation() {
        Quaternionf base = new Quaternionf().rotateY(0.25f);
        Quaternionf result = ActorBoneGizmoController.rotate(new Quaternionf(base), Axis.X, 0.5f);
        Vector3f expected = new Quaternionf(base).rotateX(0.5f).transform(new Vector3f(0f, 1f, 0f));
        Vector3f actual = result.transform(new Vector3f(0f, 1f, 0f));

        assertEquals(expected.x, actual.x, 1.0e-5f);
        assertEquals(expected.y, actual.y, 1.0e-5f);
        assertEquals(expected.z, actual.z, 1.0e-5f);
    }
}
