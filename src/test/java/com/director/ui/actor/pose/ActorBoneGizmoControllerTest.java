package com.director.ui.actor.pose;

import com.director.client.gizmo.Axis;
import net.minecraft.util.math.Vec3d;
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

    @Test
    void appliesAccumulatedTranslationToGestureBase() {
        Vector3f result = ActorBoneGizmoController.translate(
                new Vector3f(1f, 2f, 3f), new Vec3d(0.5, -1.0, 2.0));

        assertEquals(1.5f, result.x, 1.0e-5f);
        assertEquals(1f, result.y, 1.0e-5f);
        assertEquals(5f, result.z, 1.0e-5f);
    }

    @Test
    void appliesMultiplicativeScaleAndClampsPositive() {
        Vector3f result = ActorBoneGizmoController.scale(
                new Vector3f(2f, 3f, 4f), new Vec3d(0.5, -0.5, -2.0));

        assertEquals(3f, result.x, 1.0e-5f);
        assertEquals(1.5f, result.y, 1.0e-5f);
        assertEquals(0.01f, result.z, 1.0e-5f);
    }
}
