package com.director.lighting;
import java.util.List;
/** Exact distance to a point, polyline, or two-sided rectangular emitter. */
public final class LightGeometry {
    private LightGeometry() {}
    public static List<LightPoint> vertices(PointLight light) {
        return switch(light.type()) {
            case POINT -> List.of(new LightPoint(light.x(),light.y(),light.z()));
            case LINE -> light.anchors().stream().map(p -> p.add(light.x(),light.y(),light.z())).toList();
            case AREA -> List.of(areaPoint(light,-1,-1),areaPoint(light,1,-1),areaPoint(light,1,1),areaPoint(light,-1,1));
        };
    }
    public static LightPoint areaPoint(PointLight l,double u,double v) {
        double y=v*l.height()*.5, x=u*l.width()*.5;
        double pitch=Math.toRadians(l.pitch()),yaw=Math.toRadians(l.yaw());
        double py=Math.cos(pitch)*y,pz=Math.sin(pitch)*y;
        return new LightPoint(l.x()+Math.cos(yaw)*x+Math.sin(yaw)*pz,l.y()+py,l.z()-Math.sin(yaw)*x+Math.cos(yaw)*pz);
    }
    public static double distanceSquared(PointLight l,double x,double y,double z) {
        double dx=x-l.x(),dy=y-l.y(),dz=z-l.z();
        if(l.type()==LightType.POINT) return dx*dx+dy*dy+dz*dz;
        if(l.type()==LightType.AREA) {
            double yaw=Math.toRadians(l.yaw()),pitch=Math.toRadians(l.pitch());
            double lx=Math.cos(yaw)*dx-Math.sin(yaw)*dz;
            double lz=Math.sin(yaw)*dx+Math.cos(yaw)*dz;
            double ly=Math.cos(pitch)*dy+Math.sin(pitch)*lz;
            double normal=-Math.sin(pitch)*dy+Math.cos(pitch)*lz;
            double ex=Math.max(0,Math.abs(lx)-l.width()*.5),ey=Math.max(0,Math.abs(ly)-l.height()*.5);
            return ex*ex+ey*ey+normal*normal;
        }
        double result=Double.POSITIVE_INFINITY;
        for(int i=1;i<l.anchors().size();i++) {
            var a=l.anchors().get(i-1);var b=l.anchors().get(i);
            double vx=b.x()-a.x(),vy=b.y()-a.y(),vz=b.z()-a.z(),den=vx*vx+vy*vy+vz*vz;
            double t=den<1e-12?0:Math.max(0,Math.min(1,((dx-a.x())*vx+(dy-a.y())*vy+(dz-a.z())*vz)/den));
            double ex=dx-a.x()-t*vx,ey=dy-a.y()-t*vy,ez=dz-a.z()-t*vz;
            result=Math.min(result,ex*ex+ey*ey+ez*ez);
        }
        return result;
    }
}
