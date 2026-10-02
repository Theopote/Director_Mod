package com.director.client.gizmo;
import com.director.client.gizmo.handles.*;
import com.director.client.gizmo.logic.TransformApplier;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GizmoInteractionControllerTest {
    GizmoContext context=new GizmoContext(GizmoType.TRANSFORM,new Box(-1,-1,-1,1,1,1),Vec3d.ZERO);
    GizmoInteractionController controller=new GizmoInteractionController(context,null);
    Vec3d origin=new Vec3d(0,0,5);
    Vec3d ray(double x,double y) { return new Vec3d(x,y,0).subtract(origin).normalize(); }
    void rings() { controller.setRotationHandles(List.of(new RotationHandle(Vec3d.ZERO,2,Axis.Z))); }
    @Test void translateAndScaleDoNotHoverOrPromoteRotationRings() {
        rings();
        for(var mode:List.of(GizmoMode.TRANSLATE,GizmoMode.SCALE,GizmoMode.LOOK_AT)) {
            context.setMode(mode); controller.update(origin,ray(2,0),false,false);
            assertNull(controller.getHoveredRotationHandle());
            controller.update(origin,ray(2,0),true,false); assertNull(controller.getActiveRotationHandle());
            assertEquals(mode,context.getMode()); controller.update(origin,ray(2,0),false,true);
        }
    }
    @Test void rotateIgnoresMoveHandlesAndStartsTheHoveredRing() {
        rings(); var center=new CenterHandle("center",Vec3d.ZERO,.15); controller.setHandles(List.of(center));
        context.setMode(GizmoMode.ROTATE); controller.update(origin,ray(0,0),false,false); assertNull(controller.getHoveredHandle());
        controller.update(origin,ray(2,0),false,false); var hovered=controller.getHoveredRotationHandle(); assertNotNull(hovered);
        controller.update(origin,ray(2,0),true,false); assertSame(hovered,controller.getActiveRotationHandle());
        controller.update(origin,ray(2,0),false,true); assertNull(controller.getActiveRotationHandle());
    }
    @Test void holdingOutsideThenMovingOntoHandleCannotStartDrag() {
        var center=new CenterHandle("center",Vec3d.ZERO,.15); controller.setHandles(List.of(center));
        controller.update(origin,ray(4,4),true,false); controller.update(origin,ray(0,0),true,false);
        assertNull(controller.getActiveHandle());
        controller.update(origin,ray(0,0),false,true); controller.update(origin,ray(0,0),true,false);
        assertSame(center,controller.getActiveHandle());
    }
    @Test void newlyActivatedGizmoDoesNotStealExistingMousePress() {
        controller.setHandles(List.of(new CenterHandle("center",Vec3d.ZERO,.15)));
        controller.update(origin,ray(0,0),true,false,false); assertNull(controller.getActiveHandle());
    }
    @Test void centerWinsOverAxisToleranceAndScaleUsesUniformResize() {
        var center=new CenterHandle("center",Vec3d.ZERO,.15);
        controller.setHandles(List.of(new AxisHandle("x",Vec3d.ZERO,Axis.X,2,.15),center));
        class Capture extends TransformApplier {
            Vec3d scale; int moves; Capture() { super(null); }
            @Override public void applyResize(Vec3d delta) { scale=delta; }
            @Override public void applyMove(Vec3d delta) { moves++; }
        }
        var capture=new Capture(); controller.setTransformApplier(capture); context.setMode(GizmoMode.SCALE);
        controller.update(origin,ray(0,0),true,false); assertSame(center,controller.getActiveHandle());
        controller.update(origin,ray(.01,0),true,false);
        assertNotNull(capture.scale); assertTrue(capture.scale.x>0);
        assertEquals(capture.scale.x,capture.scale.y); assertEquals(capture.scale.y,capture.scale.z); assertEquals(0,capture.moves);
    }
    @Test void rotationReturningToStartStillAppliesZeroDelta() {
        rings(); context.setMode(GizmoMode.ROTATE);
        class Capture extends TransformApplier {
            double angle; Capture() { super(null); }
            @Override public void applyRotation(double next,Axis axis) { angle=next; }
        }
        var capture=new Capture(); controller.setTransformApplier(capture);
        controller.update(origin,ray(2,0),true,false); controller.update(origin,ray(0,2),true,false);
        assertEquals(Math.PI/2,capture.angle,1e-6); controller.update(origin,ray(2,0),true,false);
        assertEquals(0,capture.angle,1e-6);
    }
    @Test void uiCaptureClearsHoverWithoutDiscardingActiveGesture() {
        controller.setHandles(List.of(new CenterHandle("center",Vec3d.ZERO,.15)));
        controller.update(origin,ray(0,0),false,false); assertNotNull(controller.getHoveredHandle());
        controller.clearHover(); assertNull(controller.getHoveredHandle());
        controller.update(origin,ray(0,0),true,false); var active=controller.getActiveHandle();
        controller.clearHover(); assertSame(active,controller.getActiveHandle());
        controller.update(origin,ray(0,0),false,true,false); assertNull(controller.getActiveHandle());
    }
    @Test void planeCenterIsNotStolenByBroadAxisHitAreas() {
        var plane=new PlaneHandle("xy",Vec3d.ZERO,Axis.X,Axis.Y,.5);
        controller.setHandles(List.of(new AxisHandle("x",Vec3d.ZERO,Axis.X,2,.15),
                new AxisHandle("y",Vec3d.ZERO,Axis.Y,2,.15),plane));
        double size=.5*com.director.client.gizmo.util.GizmoVisualScale.distanceMultiplier(Vec3d.ZERO,origin);
        controller.update(origin,ray(size,size),false,false); assertSame(plane,controller.getHoveredHandle());
    }
}
