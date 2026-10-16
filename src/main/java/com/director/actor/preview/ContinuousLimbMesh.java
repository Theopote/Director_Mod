package com.director.actor.preview;
import com.director.actor.rig.skeleton.LimbBendTransform;
import net.minecraft.client.model.ModelPart;
import org.joml.*;
import java.util.*;

/** Fixed topology and original UVs, evaluated from rest vertices on every pose change. */
public final class ContinuousLimbMesh {
    public record Vertex(Vector3f position,Vector3f normal,float u,float v) {}
    private ContinuousLimbMesh() {}
    public static List<Vertex[]> build(ModelPart.Cuboid template,float pivot,Quaternionf rotation,Vector3f scale) {
        return build(template,pivot,rotation,scale,com.director.actor.rig.skeleton.LimbBendMode.SMOOTH);
    }
    public static List<Vertex[]> build(ModelPart.Cuboid template,float pivot,Quaternionf rotation,Vector3f scale,
                                      com.director.actor.rig.skeleton.LimbBendMode mode) {
        var bend=new LimbBendTransform(rotation,scale,mode);
        List<Vertex[]> result=new ArrayList<>();
        for(var face:template.sides) {
            var a=face.vertices()[0]; var b=face.vertices()[1]; var c=face.vertices()[2]; var d=face.vertices()[3];
            int slices=java.lang.Math.abs(d.y()-a.y())>.001f?48:1;
            var e1=new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()).normalize();
            var e2=new Vector3f(d.x()-a.x(),d.y()-a.y(),d.z()-a.z()).normalize();
            float sign=new Vector3f(e1).cross(e2).dot(face.direction())<0?-1:1;
            for(int i=0;i<slices;i++) {
                float t0=(float)i/slices,t1=(float)(i+1)/slices;
                int start=result.size();
                result.add(new Vertex[]{vertex(a,d,t0,pivot,bend,e1,e2,sign),vertex(b,c,t0,pivot,bend,e1,e2,sign),
                        vertex(b,c,t1,pivot,bend,e1,e2,sign),vertex(a,d,t1,pivot,bend,e1,e2,sign)});
                if(mode==com.director.actor.rig.skeleton.LimbBendMode.SHARP) {
                    var quad=result.get(start);
                    var normal=new Vector3f(quad[1].position()).sub(quad[0].position())
                            .cross(new Vector3f(quad[3].position()).sub(quad[0].position())).mul(sign).normalize();
                    for(int j=0;j<4;j++) quad[j]=new Vertex(quad[j].position(),new Vector3f(normal),quad[j].u(),quad[j].v());
                }
            }
        }
        return result;
    }
    private static Vertex vertex(ModelPart.Vertex a,ModelPart.Vertex b,float t,float pivot,LimbBendTransform bend,
                                 Vector3f e1,Vector3f e2,float sign) {
        var p=new Vector3f(a.x(),a.y()-pivot,a.z()).lerp(new Vector3f(b.x(),b.y()-pivot,b.z()),t);
        float h=.001f;
        var tangent1=bend.map(new Vector3f(p).fma(h,e1)).sub(bend.map(new Vector3f(p).fma(-h,e1)));
        var tangent2=bend.map(new Vector3f(p).fma(h,e2)).sub(bend.map(new Vector3f(p).fma(-h,e2)));
        var n=tangent1.cross(tangent2).mul(sign);
        if(n.lengthSquared()<1e-12f) n=new Vector3f(e1).cross(e2).mul(sign);
        n.normalize();
        return new Vertex(bend.map(p),n,a.u()+(b.u()-a.u())*t,a.v()+(b.v()-a.v())*t);
    }
}
