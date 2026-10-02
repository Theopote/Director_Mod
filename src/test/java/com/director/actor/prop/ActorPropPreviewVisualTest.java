package com.director.actor.prop;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorPropPreviewVisualTest {
    void near(Vector3f expected,Vector3f actual) { assertTrue(expected.distance(actual)<.0001f); }
    @Test void blockCenterIsExactlyAtSocket() {
        var socket=new Matrix4f().translate(3,4,5).rotateY(.7f).scale(2,3,4);
        var result=ActorPropPreviewVisual.modelTransform(PropVisualType.BLOCK,socket);
        near(new Vector3f(3,4,5),result.transformPosition(new Vector3f(.5f)));
    }
    @Test void blockDimensionsPreserveNonUniformScale() {
        var result=ActorPropPreviewVisual.modelTransform(PropVisualType.BLOCK,new Matrix4f().scale(2,3,4));
        near(new Vector3f(1,1.5f,2),result.getScale(new Vector3f()));
    }
    @Test void itemTransformIsUnmodifiedAndDefensive() {
        var socket=new Matrix4f().translate(1,2,3).rotateXYZ(.3f,.4f,.5f);
        var result=ActorPropPreviewVisual.modelTransform(PropVisualType.ITEM,socket);
        assertEquals(socket,result); result.identity(); near(new Vector3f(1,2,3),socket.getTranslation(new Vector3f()));
    }
    @Test void entityPositionPlusLocalMatrixReconstructsModelMatrix() {
        var socket=new Matrix4f().translate(5,6,7).rotateXYZ(.3f,.8f,.1f).scale(2,1,3);
        var model=ActorPropPreviewVisual.modelTransform(PropVisualType.BLOCK,socket);
        var origin=socket.getTranslation(new Vector3f()); var local=new Matrix4f(model);
        local.setTranslation(local.getTranslation(new Vector3f()).sub(origin));
        var reconstructed=new Matrix4f().translate(origin).mul(local);
        assertTrue(model.equals(reconstructed,.0001f));
    }
}
