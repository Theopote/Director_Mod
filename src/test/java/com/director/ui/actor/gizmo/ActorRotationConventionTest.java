package com.director.ui.actor.gizmo;

import com.director.client.gizmo.Axis;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRotationConventionTest {
    @Test void ringYawConvertsToMinecraftClockwiseYaw() {
        assertEquals(-90,ActorTransformApplier.rotationDelta(Axis.Y,90).x);
        assertEquals(90,ActorTransformApplier.rotationDelta(Axis.X,90).y);
        assertEquals(90,ActorTransformApplier.rotationDelta(Axis.Z,90).z);
    }
}
