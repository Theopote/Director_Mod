package com.director.actor.preview;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;
import java.util.*;

/** Rigid limb UV slicing and a separate exposed joint connector. */
public final class PlayerJointSeam {
    private static final Map<ModelPart,State> STATES=new WeakHashMap<>();
    private static final class State {
        final ModelPart.Cuboid template;
        final float pivot,dilation;
        Quaternionf rotation;
        Vector3f scale;
        List<Vector3f[]> triangles=List.of();
        State(ModelPart parent,ModelPart upper,ModelPart lower) {
            template=cuboids(parent).getFirst(); pivot=lower.originY;
            float min=Float.POSITIVE_INFINITY;
            for(var face:template.sides) for(var v:face.vertices()) min=java.lang.Math.min(min,v.x());
            dilation=template.minX-min;
            remap(upper,template,0); remap(lower,template,pivot);
        }
    }
    private PlayerJointSeam() {}
    private static List<ModelPart.Cuboid> cuboids(ModelPart part) {
        List<ModelPart.Cuboid> result=new ArrayList<>();
        part.forEachCuboid(new MatrixStack(),(entry,path,index,cuboid)->result.add(cuboid));
        return result;
    }
    public static void update(ModelPart parent,ModelPart upper,ModelPart lower,ModelPart connector,
                              Quaternionf rotation,Vector3f scale) {
        var state=STATES.computeIfAbsent(connector,key->new State(parent,upper,lower));
        if(state.rotation!=null && state.rotation.equals(rotation,1e-6f) && state.scale.equals(scale,1e-6f)) return;
        state.triangles=JointConnectorGeometry.build(state.template.minX,state.template.maxX-state.template.minX,
                state.dilation,rotation,scale);
        state.rotation=new Quaternionf(rotation); state.scale=new Vector3f(scale);
    }
    private static void remap(ModelPart part,ModelPart.Cuboid template,float pivot) {
        for(var cuboid:cuboids(part)) for(int i=0;i<cuboid.sides.length;i++) {
            var face=cuboid.sides[i]; var vertices=new ModelPart.Vertex[4];
            for(int j=0;j<4;j++) {
                var v=face.vertices()[j];
                var uv=uv(template,new Vector3f(v.x(),v.y()+pivot,v.z()),new Vector3f(face.direction()),false);
                vertices[j]=new ModelPart.Vertex(v.x(),v.y(),v.z(),uv.x,uv.y);
            }
            cuboid.sides[i]=new ModelPart.Quad(vertices,face.direction());
        }
    }
    /** Sample the original full limb face rather than inventing new cuboid UV layouts. */
    public static Vector2f uv(ModelPart.Cuboid template,Vector3f position,Vector3f normal,boolean joint) {
        var direction=new Vector3f(normal);
        if(joint) {
            if(java.lang.Math.abs(direction.x)>java.lang.Math.abs(direction.z)) direction.set(direction.x>=0?1:-1,0,0);
            else direction.set(0,0,direction.z>=0?1:-1);
        }
        ModelPart.Quad selected=template.sides[0]; float best=-Float.MAX_VALUE;
        for(var face:template.sides) if(face.direction().dot(direction)>best) { best=face.direction().dot(direction); selected=face; }
        var a=selected.vertices()[0]; var b=selected.vertices()[1]; var d=selected.vertices()[3];
        var edge1=new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z());
        var edge2=new Vector3f(d.x()-a.x(),d.y()-a.y(),d.z()-a.z());
        var offset=new Vector3f(position).sub(a.x(),a.y(),a.z());
        float s=java.lang.Math.max(0,java.lang.Math.min(1,offset.dot(edge1)/edge1.lengthSquared()));
        float t=java.lang.Math.max(0,java.lang.Math.min(1,offset.dot(edge2)/edge2.lengthSquared()));
        return new Vector2f(a.u()+s*(b.u()-a.u())+t*(d.u()-a.u()),a.v()+s*(b.v()-a.v())+t*(d.v()-a.v()));
    }
    public static boolean render(ModelPart part,MatrixStack.Entry entry,VertexConsumer consumer,int light,int overlay,int color) {
        var state=STATES.get(part); if(state==null) return false;
        for(var triangle:state.triangles) {
            var normal=new Vector3f(triangle[1]).sub(triangle[0]).cross(new Vector3f(triangle[2]).sub(triangle[0])).normalize();
            var worldNormal=entry.transformNormal(normal,new Vector3f());
            for(int i:new int[]{0,1,2,2}) {
                var p=triangle[i]; var uv=uv(state.template,new Vector3f(p).add(0,state.pivot,0),normal,true);
                var world=entry.getPositionMatrix().transformPosition(new Vector3f(p).div(16));
                consumer.vertex(world.x,world.y,world.z,color,uv.x,uv.y,overlay,light,worldNormal.x,worldNormal.y,worldNormal.z);
            }
        }
        return true;
    }
}
