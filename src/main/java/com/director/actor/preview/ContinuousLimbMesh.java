package com.director.actor.preview;

import com.director.actor.rig.skeleton.LimbBendMode;
import com.director.actor.rig.skeleton.LimbBendTransform;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Rest topology/UVs are prepared once; pose evaluation only deforms the cached surface. */
public final class ContinuousLimbMesh {
    public record Vertex(Vector3f position, Vector3f normal, float u, float v) {}
    private record RestVertex(Vector3f position, float u, float v) {}
    private record RestQuad(RestVertex[] vertices, Vector3f e1, Vector3f e2, float sign) {}
    public static final class Template {
        private final List<RestQuad> quads;
        private Template(List<RestQuad> quads) { this.quads = List.copyOf(quads); }
        public float quadCenterY(int index) {
            float y=0; for(var v:quads.get(index).vertices) y+=v.position.y;
            return y/4;
        }
    }
    private ContinuousLimbMesh() {}

    public static Template prepare(ModelPart.Cuboid cuboid, float pivot) {
        List<RestQuad> quads = new ArrayList<>();
        for (var face : cuboid.sides) {
            var a=face.vertices()[0]; var b=face.vertices()[1];
            var c=face.vertices()[2]; var d=face.vertices()[3];
            boolean side=java.lang.Math.abs(d.y()-a.y())>.001f;
            var e1=new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()).normalize();
            var e2=new Vector3f(d.x()-a.x(),d.y()-a.y(),d.z()-a.z()).normalize();
            float sign=new Vector3f(e1).cross(e2).dot(face.direction())<0?-1:1;
            var cuts=new TreeSet<Float>(); cuts.add(0f); cuts.add(1f);
            if(side) {
                for(int i=1;i<48;i++) cuts.add((float)i/48);
                // Exact boundaries prevent quads straddling a hard crease, including dilated sleeves.
                for(float y:new float[]{-LimbBendTransform.SHARP_HALF,0,LimbBendTransform.SHARP_HALF}) {
                    float t=(y+pivot-a.y())/(d.y()-a.y());
                    if(t>0 && t<1 && cuts.stream().noneMatch(v->java.lang.Math.abs(v-t)<1e-6f)) cuts.add(t);
                }
            }
            var points=new ArrayList<>(cuts);
            for(int i=0;i<points.size()-1;i++) {
                float t0=points.get(i),t1=points.get(i+1);
                quads.add(new RestQuad(new RestVertex[]{rest(a,d,t0,pivot),rest(b,c,t0,pivot),
                        rest(b,c,t1,pivot),rest(a,d,t1,pivot)},e1,e2,sign));
            }
        }
        return new Template(quads);
    }
    private static RestVertex rest(ModelPart.Vertex a,ModelPart.Vertex b,float t,float pivot) {
        return new RestVertex(new Vector3f(a.x(),a.y()-pivot,a.z()).lerp(
                new Vector3f(b.x(),b.y()-pivot,b.z()),t),a.u()+(b.u()-a.u())*t,a.v()+(b.v()-a.v())*t);
    }
    public static List<Vertex[]> build(ModelPart.Cuboid cuboid,float pivot,Quaternionf rotation,Vector3f scale) {
        return build(cuboid,pivot,rotation,scale,LimbBendMode.SMOOTH);
    }
    public static List<Vertex[]> build(ModelPart.Cuboid cuboid,float pivot,Quaternionf rotation,Vector3f scale,LimbBendMode mode) {
        return deform(prepare(cuboid,pivot),rotation,scale,mode);
    }
    public static List<Vertex[]> deform(Template template,Quaternionf rotation,Vector3f scale,LimbBendMode mode) {
        var bend=new LimbBendTransform(rotation,scale,mode);
        List<Vertex[]> result=new ArrayList<>(template.quads.size());
        for(var face:template.quads) {
            var vertices=new Vertex[4];
            for(int i=0;i<4;i++) {
                var rest=face.vertices[i];
                vertices[i]=new Vertex(bend.map(rest.position),mode==LimbBendMode.SMOOTH
                        ?normal(rest.position,bend,face):new Vector3f(),rest.u,rest.v);
            }
            if(mode==LimbBendMode.SHARP) {
                var n=new Vector3f(vertices[1].position).sub(vertices[0].position)
                        .cross(new Vector3f(vertices[3].position).sub(vertices[0].position)).mul(face.sign);
                if(n.lengthSquared()<1e-12f) n.set(face.e1).cross(face.e2).mul(face.sign);
                n.normalize();
                for(int i=0;i<4;i++) vertices[i]=new Vertex(vertices[i].position,new Vector3f(n),vertices[i].u,vertices[i].v);
            }
            result.add(vertices);
        }
        return result;
    }
    /** Close a selected half at the joint; this face is only emitted into the outline mask. */
    public static Vertex[] selectionCap(ModelPart.Cuboid cube,Quaternionf rotation,Vector3f scale,LimbBendMode mode) {
        var bend=new LimbBendTransform(rotation,scale,mode);
        float minX=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(var side:cube.sides) for(var v:side.vertices()) {
            minX=java.lang.Math.min(minX,v.x());maxX=java.lang.Math.max(maxX,v.x());
            minZ=java.lang.Math.min(minZ,v.z());maxZ=java.lang.Math.max(maxZ,v.z());
        }
        var points=new Vector3f[]{new Vector3f(minX,0,minZ),new Vector3f(minX,0,maxZ),
                new Vector3f(maxX,0,maxZ),new Vector3f(maxX,0,minZ)};
        var face=cube.sides[0];
        for(var candidate:cube.sides) if(candidate.direction().y()>.5f) { face=candidate; break; }
        for(var point:points) bend.map(point).get(point);
        var n=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[3]).sub(points[0])).normalize();
        var result=new Vertex[4];
        for(int i=0;i<4;i++) result[i]=new Vertex(points[i],new Vector3f(n),face.vertices()[i].u(),face.vertices()[i].v());
        return result;
    }
    private static Vector3f normal(Vector3f p,LimbBendTransform bend,RestQuad face) {
        float h=.001f;
        var a=bend.map(new Vector3f(p).fma(h,face.e1)).sub(bend.map(new Vector3f(p).fma(-h,face.e1)));
        var b=bend.map(new Vector3f(p).fma(h,face.e2)).sub(bend.map(new Vector3f(p).fma(-h,face.e2)));
        var n=a.cross(b).mul(face.sign);
        if(n.lengthSquared()<1e-12f) n.set(face.e1).cross(face.e2).mul(face.sign);
        return n.normalize();
    }
}
