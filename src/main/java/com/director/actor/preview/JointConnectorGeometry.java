package com.director.actor.preview;

import org.joml.*;
import java.util.*;

/** Exposed convex joint surfaces only. Neither rigid limb segment is deformed. */
public final class JointConnectorGeometry {
    private static final float EPS=1e-4f;
    private record Plane(Vector3f normal,float distance) {}
    private JointConnectorGeometry() {}

    public static List<Vector3f[]> build(float x,float width,float dilation,Quaternionf rotation,Vector3f scale) {
        if(!rotation.isFinite() || !scale.isFinite() || scale.x<=0 || scale.y<=0 || scale.z<=0) return List.of();
        var lower=new Matrix4f().rotate(rotation).scale(scale);
        var inverse=new Matrix4f(lower).invert();
        List<Vector3f> points=new ArrayList<>();
        for(float px:new float[]{x-dilation,x+width+dilation}) for(float z:new float[]{-2-dilation,2+dilation}) {
            points.add(new Vector3f(px,dilation,z));
            points.add(lower.transformPosition(new Vector3f(px,-dilation,z)));
        }
        List<Plane> planes=new ArrayList<>();
        for(int a=0;a<points.size();a++) for(int b=a+1;b<points.size();b++) for(int c=b+1;c<points.size();c++) {
            var n=new Vector3f(points.get(b)).sub(points.get(a)).cross(new Vector3f(points.get(c)).sub(points.get(a)));
            if(n.lengthSquared()<EPS*EPS) continue;
            n.normalize(); float distance=n.dot(points.get(a)); boolean positive=false,negative=false;
            for(var point:points) { float side=n.dot(point)-distance; positive|=side>EPS; negative|=side<-EPS; }
            if(positive&&negative) continue;
            if(!positive&&!negative) continue;
            if(positive) { n.negate(); distance=-distance; }
            boolean duplicate=false;
            for(var plane:planes) if(plane.normal.dot(n)>1-EPS && java.lang.Math.abs(plane.distance-distance)<EPS) { duplicate=true; break; }
            if(!duplicate) planes.add(new Plane(n,distance));
        }
        List<Vector3f[]> triangles=new ArrayList<>();
        float[] upperBox={x-dilation,-6-dilation,-2-dilation,x+width+dilation,dilation,2+dilation};
        float[] lowerBox={x-dilation,-dilation,-2-dilation,x+width+dilation,6+dilation,2+dilation};
        for(var plane:planes) {
            List<Vector3f> face=new ArrayList<>();
            for(var point:points) if(java.lang.Math.abs(plane.normal.dot(point)-plane.distance)<EPS) {
                boolean exists=false; for(var p:face) if(p.distanceSquared(point)<EPS*EPS) { exists=true; break; }
                if(!exists) face.add(point);
            }
            if(face.size()<3) continue;
            var center=new Vector3f(); for(var p:face) center.add(p); center.div(face.size());
            var u=new Vector3f(face.getFirst()).sub(center).normalize();
            var v=new Vector3f(plane.normal).cross(u);
            face.sort(Comparator.comparingDouble(p -> java.lang.Math.atan2(new Vector3f(p).sub(center).dot(v),new Vector3f(p).sub(center).dot(u))));
            // Clip away portions inside either rigid cuboid, including coincident surfaces.
            for(var outsideUpper:subtractBox(face,new Matrix4f(),upperBox))
                for(var polygon:subtractBox(outsideUpper,inverse,lowerBox))
                    for(int i=1;i+1<polygon.size();i++) {
                        var a=polygon.getFirst(); var b=polygon.get(i); var c=polygon.get(i+1);
                        if(new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).lengthSquared()>EPS*EPS)
                            triangles.add(new Vector3f[]{new Vector3f(a),new Vector3f(b),new Vector3f(c)});
                    }
        }
        return triangles;
    }
    private static List<List<Vector3f>> subtractBox(List<Vector3f> polygon,Matrix4f inverse,float[] box) {
        List<List<Vector3f>> outside=new ArrayList<>(); List<Vector3f> remaining=polygon;
        for(int axis=0;axis<3 && !remaining.isEmpty();axis++) for(int side=0;side<2 && !remaining.isEmpty();side++) {
            List<Vector3f> inside=new ArrayList<>(),fragment=new ArrayList<>();
            for(int i=0;i<remaining.size();i++) {
                var a=remaining.get(i); var b=remaining.get((i+1)%remaining.size());
                float da=planeDistance(a,inverse,axis,side,box)-EPS,db=planeDistance(b,inverse,axis,side,box)-EPS;
                boolean ai=da<=0,bi=db<=0;
                (ai?inside:fragment).add(a);
                if(ai!=bi) {
                    var hit=new Vector3f(a).lerp(b,da/(da-db));
                    inside.add(hit); fragment.add(hit);
                }
            }
            if(fragment.size()>=3) outside.add(fragment);
            remaining=inside;
        }
        return outside;
    }
    private static float planeDistance(Vector3f p,Matrix4f inverse,int axis,int side,float[] box) {
        float value=inverse.transformPosition(new Vector3f(p)).get(axis);
        return side==0?box[axis]-value:value-box[axis+3];
    }
}
