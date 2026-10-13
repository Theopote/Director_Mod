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
        int r=(int)Math.ceil(light.range())+1;
        int x=(int)Math.floor(light.x()),y=(int)Math.floor(light.y()),z=(int)Math.floor(light.z());
        bounds.add(new Bounds(x-r,y-r,z-r,x+r,y+r,z+r));
    }
}
