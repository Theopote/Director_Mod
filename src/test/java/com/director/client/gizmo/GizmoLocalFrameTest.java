package com.director.client.gizmo;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GizmoLocalFrameTest {
    @Test void rayFrameRotatesAboutPivotWithoutChangingDistance() {
        var context=new GizmoContext(GizmoType.TRANSFORM,new Box(0,0,0,1,1,1),new Vec3d(10,20,30));
        context.setOrientation(new Quaternionf().rotateY((float)Math.PI/2));
        var local=context.toHandlePoint(new Vec3d(10,20,29));
        assertEquals(11,local.x,1e-5); assertEquals(20,local.y,1e-5); assertEquals(30,local.z,1e-5);
        var direction=context.toHandleDirection(new Vec3d(0,0,-1));
        assertEquals(1,direction.x,1e-5); assertEquals(1,direction.length(),1e-5);
        assertEquals(1,local.distanceTo(context.getPivot()),1e-5);
    }
    @Test void defaultFrameLeavesWorldToolsUnchangedAndCopiesOrientation() {
        var context=new GizmoContext(GizmoType.TRANSFORM,new Box(0,0,0,1,1,1),Vec3d.ZERO);
        var point=new Vec3d(1,2,3);
        assertEquals(point,context.toHandlePoint(point));
        context.getOrientation().rotateX(1);
        assertEquals(point,context.toHandlePoint(point));
    }
}
