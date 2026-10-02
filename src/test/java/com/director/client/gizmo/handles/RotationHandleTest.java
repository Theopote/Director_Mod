package com.director.client.gizmo.handles;
import com.director.client.gizmo.Axis;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotationHandleTest {
    Vec3d origin=new Vec3d(0,0,5);
    Vec3d ray(double degrees) { double r=Math.toRadians(degrees); return new Vec3d(2*Math.cos(r),2*Math.sin(r),0).subtract(origin).normalize(); }
    @Test void seamCrossingIsContinuousInBothDirections() {
        var ring=new RotationHandle(Vec3d.ZERO,2,Axis.Z); ring.beginRotation(origin,ray(170)); ring.updateRotation(origin,ray(-170));
        assertEquals(20,ring.getDeltaAngleDegrees(),1e-5); ring.updateRotation(origin,ray(170)); assertEquals(0,ring.getDeltaAngleDegrees(),1e-5);
        ring.endRotation(); ring.beginRotation(origin,ray(-170)); ring.updateRotation(origin,ray(170)); assertEquals(-20,ring.getDeltaAngleDegrees(),1e-5);
    }
    @Test void multipleTurnsAccumulateAndReleaseResets() {
        var ring=new RotationHandle(Vec3d.ZERO,2,Axis.Z); ring.beginRotation(origin,ray(0));
        for(int i=90;i<=720;i+=90) ring.updateRotation(origin,ray(i));
        assertEquals(720,ring.getDeltaAngleDegrees(),1e-5); ring.endRotation(); assertEquals(0,ring.getDeltaAngleDegrees());
    }
    @Test void ringCenterAndBroadAnnulusDoNotCountAsRing() {
        var ring=new RotationHandle(Vec3d.ZERO,2,Axis.Z);
        assertTrue(ring.isHit(origin,ray(0)));
        assertFalse(ring.isHit(origin,new Vec3d(0,0,-1)));
        assertFalse(ring.isHit(origin,new Vec3d(1.7,0,0).subtract(origin).normalize()));
    }
}
