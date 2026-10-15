package com.director.actor.preview;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerJointSeamTest {
    private ModelPart.Cuboid cuboid(float height,int v) {
        return new ModelPart.Cuboid(40,v,-2,0,-2,4,height,4,0,0,0,false,64,64,EnumSet.allOf(Direction.class));
    }
    @Test void rigidVerticesAndUvsRemainStableAcrossCompoundBendingAndScaling() {
        var full=cuboid(12,16); var up=cuboid(6,16); var down=cuboid(6,22);
        var upper=new ModelPart(List.of(up),Map.of()); var lower=new ModelPart(List.of(down),Map.of()); lower.originY=6;
        var connector=new ModelPart(List.of(cuboid(1,16)),Map.of());
        var parent=new ModelPart(List.of(full),Map.of("upper",upper,"lower",lower,"connector",connector));
        var positions=new ArrayList<Vector3f>();
        for(var cube:List.of(up,down)) for(var face:cube.sides) for(var v:face.vertices()) positions.add(new Vector3f(v.x(),v.y(),v.z()));
        for(int angle:new int[]{0,45,90,150}) {
            PlayerJointSeam.update(parent,upper,lower,connector,new Quaternionf().rotateX((float)Math.toRadians(angle)).rotateY(.4f).rotateZ(.2f),new Vector3f(1.2f,.8f,1.1f));
            int index=0;
            for(var cube:List.of(up,down)) for(var face:cube.sides) for(var v:face.vertices())
                assertEquals(positions.get(index++),new Vector3f(v.x(),v.y(),v.z()));
        }
        var sole=Arrays.stream(down.sides).filter(q->q.direction().y()>.9f).findFirst().orElseThrow();
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(var v:sole.vertices()) { min=Math.min(min,v.v()); max=Math.max(max,v.v()); }
        assertEquals(16f/64,min,1e-6f); assertEquals(20f/64,max,1e-6f);
        var north=Arrays.stream(down.sides).filter(q->q.direction().z()<-.9f).findFirst().orElseThrow();
        for(var v:north.vertices()) assertTrue(v.v()>=26f/64-1e-6f && v.v()<=32f/64+1e-6f);
    }
    @Test void connectorOnlyAddsExposedGeometryAndIsEmptyWhenStraight() {
        assertTrue(JointConnectorGeometry.build(-2,4,0,new Quaternionf(),new Vector3f(1)).isEmpty());
        assertTrue(JointConnectorGeometry.build(-2,4,.25f,new Quaternionf(),new Vector3f(1)).isEmpty());
        for(float dilation:new float[]{0,.25f}) for(int angle:new int[]{30,90,150}) {
            var triangles=JointConnectorGeometry.build(-2,4,dilation,new Quaternionf().rotateX((float)Math.toRadians(angle)).rotateY(.2f),new Vector3f(1.2f,.8f,1.1f));
            assertFalse(triangles.isEmpty());
            for(var triangle:triangles) for(var p:triangle) { assertTrue(p.isFinite()); assertTrue(p.length()<10); }
        }
    }
    @Test void ninetyDegreeGapHasAnOuterSideSurface() {
        var triangles=JointConnectorGeometry.build(-2,4,0,new Quaternionf().rotateX((float)Math.PI/2),new Vector3f(1));
        var target=new Vector3f(2,.7f,-.7f);
        assertTrue(triangles.stream().anyMatch(t->contains(t,target)));
    }
    private boolean contains(Vector3f[] t,Vector3f p) {
        var a=new Vector3f(t[1]).sub(t[0]); var b=new Vector3f(t[2]).sub(t[0]); var offset=new Vector3f(p).sub(t[0]);
        var normal=new Vector3f(a).cross(b);
        if(Math.abs(normal.dot(offset))>1e-4f) return false;
        float aa=a.dot(a),ab=a.dot(b),bb=b.dot(b),oa=offset.dot(a),ob=offset.dot(b),den=aa*bb-ab*ab;
        if(Math.abs(den)<1e-8f) return false;
        float u=(oa*bb-ob*ab)/den,v=(ob*aa-oa*ab)/den;
        return u>=-1e-4f && v>=-1e-4f && u+v<=1.0001f;
    }
    @Test void connectorRendererEmitsItsOwnMeshAndLeavesOrdinaryPartsAlone() {
        var full=cuboid(12,16); var up=cuboid(6,16); var down=cuboid(6,22);
        var upper=new ModelPart(List.of(up),Map.of()); var lower=new ModelPart(List.of(down),Map.of()); lower.originY=6;
        var connector=new ModelPart(List.of(cuboid(1,16)),Map.of());
        var parent=new ModelPart(List.of(full),Map.of("upper",upper,"lower",lower,"connector",connector));
        PlayerJointSeam.update(parent,upper,lower,connector,new Quaternionf().rotateX((float)Math.PI/2),new Vector3f(1));
        int[] count={0};
        var consumer=(net.minecraft.client.render.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                net.minecraft.client.render.VertexConsumer.class.getClassLoader(),new Class[]{net.minecraft.client.render.VertexConsumer.class},(proxy,method,args)->{
                    if(method.getName().equals("vertex") && args.length==11) {
                        count[0]++;
                        for(int j:new int[]{0,1,2,4,5,8,9,10}) assertTrue(Float.isFinite((Float)args[j]));
                        assertTrue((Float)args[4]>=0 && (Float)args[4]<=1);
                        assertTrue((Float)args[5]>=0 && (Float)args[5]<=1);
                    }
                    return method.getReturnType()==net.minecraft.client.render.VertexConsumer.class?proxy:null;
                });
        var entry=new net.minecraft.client.util.math.MatrixStack().peek();
        assertFalse(PlayerJointSeam.render(upper,entry,consumer,0,0,-1));
        assertTrue(PlayerJointSeam.render(connector,entry,consumer,0,0,-1));
        assertTrue(count[0]>0);
    }
}
