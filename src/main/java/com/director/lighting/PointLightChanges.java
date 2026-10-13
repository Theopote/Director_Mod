package com.director.lighting;
import java.util.ArrayList;
import java.util.List;
/** Dirty volumes include the old influence of moved, disabled and deleted lights. */
public final class PointLightChanges {
    private PointLightChanges() {}
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {}
    public static List<Bounds> affected(List<PointLight> before,List<PointLight> after) {
        List<Bounds> result=new ArrayList<>();
        for(var light:before) if(!after.contains(light)) add(result,light);
        for(var light:after) if(!before.contains(light)) add(result,light);
        return List.copyOf(result);
    }
    private static void add(List<Bounds> bounds,PointLight light) {
        if(!light.enabled() || light.brightness()==0) return;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(var p:LightGeometry.vertices(light)) {
            minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
        }
        double r=light.range()+1;
        bounds.add(new Bounds((int)Math.floor(minX-r),(int)Math.floor(minY-r),(int)Math.floor(minZ-r),
                (int)Math.ceil(maxX+r),(int)Math.ceil(maxY+r),(int)Math.ceil(maxZ+r)));
    }
}
