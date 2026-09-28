package com.director.client.gizmo.util;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DragSnapAccumulatorTest {
    @Test void slowDragMatchesSingleMouseSample() {
        var slow = new DragSnapAccumulator();
        Vec3d total = Vec3d.ZERO;
        for (int i = 0; i < 100; i++) total = total.add(slow.update(new Vec3d(0.01, 0, 0), 0.25));
        Vec3d single = new DragSnapAccumulator().update(new Vec3d(1, 0, 0), 0.25);
        assertEquals(single.x, total.x, 1e-9);
    }

    @Test void reverseAndResetDoNotLeaveRemainders() {
        var snap = new DragSnapAccumulator();
        Vec3d forward = snap.update(new Vec3d(0.3, -0.3, 0.6), 0.25);
        Vec3d back = snap.update(new Vec3d(-0.3, 0.3, -0.6), 0.25);
        assertEquals(0, forward.add(back).length(), 1e-9);
        snap.update(new Vec3d(0.1, 0, 0), 0.25);
        snap.reset();
        assertEquals(Vec3d.ZERO, snap.update(new Vec3d(0.1, 0, 0), 0.25));
    }

    @Test void unsnappedDragPreservesFineMovement() {
        var snap = new DragSnapAccumulator();
        assertEquals(0.0001, snap.update(new Vec3d(0.0001, 0, 0), 0).x, 1e-12);
        assertEquals(0.0001, snap.update(new Vec3d(0.0001, 0, 0), 0).x, 1e-12);
    }
}
