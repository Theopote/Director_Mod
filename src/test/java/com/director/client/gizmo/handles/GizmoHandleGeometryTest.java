package com.director.client.gizmo.handles;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.util.GizmoVisualScale;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GizmoHandleGeometryTest {
    @Test void planesMatchScaledVisiblePositionAndLeaveCenterClear() {
        for(var origin:new Vec3d[]{new Vec3d(0,0,5),new Vec3d(0,0,50),new Vec3d(-10,-10,10)}) {
            var plane=new PlaneHandle("xy",Vec3d.ZERO,Axis.X,Axis.Y,.5);
            double size=.5*GizmoVisualScale.distanceMultiplier(Vec3d.ZERO,origin);
            var center=GizmoVisualScale.planeCenter(Vec3d.ZERO,origin,Axis.X,Axis.Y,size);
            assertTrue(plane.intersectRay(origin,center.subtract(origin).normalize()).isHit());
            assertFalse(plane.intersectRay(origin,Vec3d.ZERO.subtract(origin).normalize()).isHit());
        }
    }
    @Test void freeMoveIncludesSmallPerFrameDeltasAndHasNoInitialJump() {
        var center=new CenterHandle("center",Vec3d.ZERO,.15); var origin=new Vec3d(0,0,5);
        center.onDragStart(origin,new Vec3d(0,0,-1)); assertEquals(Vec3d.ZERO,center.onDragUpdate(origin,new Vec3d(0,0,-1)));
        var delta=center.onDragUpdate(origin,new Vec3d(.001,0,0).subtract(origin).normalize());
        assertEquals(.001,delta.x,1e-8);
    }
    @Test void centerHitBoxScalesWithVisibleCube() {
        var center=new CenterHandle("center",Vec3d.ZERO,.15);
        var origin=new Vec3d(0,0,50);
        assertTrue(center.intersectRay(origin,new Vec3d(.2,0,0).subtract(origin).normalize()).isHit());
        assertFalse(center.intersectRay(origin,new Vec3d(.4,0,0).subtract(origin).normalize()).isHit());
    }
}
