package com.director.core.animation;

import com.director.core.container.visibility.BlockVisualStateManager.RenderEntry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaticMeshMembershipTest {
    @Test void sectionLocalVerticesMatchWorldSpacePositionsAndCameraChangesOnlyTheTransform() {
        var origin = new BlockPos(-320, 64, 1024);
        var local = new org.joml.Vector3f(3.25f, 4.5f, 7.75f);
        var view = new org.joml.Matrix4f().rotateY(0.3f);
        for (float cameraX : new float[] {0, -300, 1000}) {
            var world = new org.joml.Matrix4f().rotateX(0.2f).translate(-cameraX, -75, -1010);
            var expected = new org.joml.Vector3f(local).add(origin.getX(), origin.getY(), origin.getZ());
            world.transformPosition(expected); view.transformPosition(expected);
            var actual = StaticSectionMeshRenderer.sectionTransform(view, world, origin)
                    .transformPosition(new org.joml.Vector3f(local));
            assertEquals(expected.x, actual.x, 0.001f);
            assertEquals(expected.y, actual.y, 0.001f);
            assertEquals(expected.z, actual.z, 0.001f);
        }
    }

    private RenderEntry entry(int x) { return new RenderEntry(new BlockPos(x, 0, 0), null, null, null, null, null); }

    @Test void newFrameObjectsAndIterationOrderDoNotInvalidateUnchangedGeometry() {
        var membership = new StaticMeshMembership(Map.of(0L, List.of(entry(0), entry(1))));
        assertTrue(membership.matches(Map.of(0L, List.of(entry(1), entry(0)))));
    }

    @Test void equalCountsCannotHidePositionReplacementOrSectionChanges() {
        var membership = new StaticMeshMembership(Map.of(0L, List.of(entry(0), entry(1))));
        assertFalse(membership.matches(Map.of(0L, List.of(entry(0), entry(2)))));
        assertFalse(membership.matches(Map.of(1L, List.of(entry(0), entry(1)))));
        assertFalse(membership.matches(Map.of(0L, List.of(entry(0)))));
        assertFalse(membership.matches(Map.of()));
    }
}
