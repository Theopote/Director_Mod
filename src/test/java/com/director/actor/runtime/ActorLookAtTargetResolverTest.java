package com.director.actor.runtime;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorLookAtTargetResolverTest {
    @Test void dynamicCameraIgnoresSavedWorldPoint() {
        var current = new Vec3d(5, 6, 7);
        assertEquals(current, ActorLookAtTargetResolver.resolve("CAMERA", new Vector3f(99), null, null,
                () -> current, id -> { throw new AssertionError(); }));
    }
    @Test void entityUsesStableIdAndCurrentTarget() {
        var id = UUID.randomUUID(); var source = UUID.randomUUID();
        for (int time = 0; time < 5; time++) {
            var position = new Vec3d(time, 8, 0);
            assertEquals(position, ActorLookAtTargetResolver.resolve("ENTITY_UUID", new Vector3f(99), id, source,
                    () -> { throw new AssertionError(); }, requested -> { assertEquals(id, requested); return position; }));
        }
    }
    @Test void missingAndSelfTargetsNeverFallBackToWorldPoint() {
        var id = UUID.randomUUID();
        assertNull(ActorLookAtTargetResolver.resolve("ENTITY_UUID", new Vector3f(99), id, null, () -> null, target -> null));
        assertNull(ActorLookAtTargetResolver.resolve("ENTITY_UUID", new Vector3f(99), id, id, () -> null,
                target -> { throw new AssertionError(); }));
        assertNull(ActorLookAtTargetResolver.resolve("CAMERA", new Vector3f(99), null, null, () -> null, target -> null));
    }
    @Test void worldPointAndInvalidInputs() {
        assertEquals(new Vec3d(1, 2, 3), ActorLookAtTargetResolver.resolve("WORLD_POINT", new Vector3f(1, 2, 3), null, null,
                () -> { throw new AssertionError(); }, id -> { throw new AssertionError(); }));
        assertNull(ActorLookAtTargetResolver.resolve("UNKNOWN", new Vector3f(), null, null, () -> null, id -> null));
        assertNull(ActorLookAtTargetResolver.resolve("WORLD_POINT", new Vector3f(Float.NaN), null, null, () -> null, id -> null));
        assertNull(ActorLookAtTargetResolver.resolve("CAMERA", null, null, null, () -> new Vec3d(Double.POSITIVE_INFINITY, 0, 0), id -> null));
    }
}
