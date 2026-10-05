package com.director.ui.timeline.render;

import java.util.*;

/** Keep all logical keys even when their markers occupy the same screen pixel. */
public final class ActorKeyframeHitCandidates {
    private ActorKeyframeHitCandidates() {}
    public record Point<T>(T value, float x, float y) {}
    public static <T> List<T> near(List<Point<T>> points, float x, float y, float radius) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius) || radius <= 0) return List.of();
        var ordered = new ArrayList<>(points);
        ordered.removeIf(p -> !Float.isFinite(p.x()) || !Float.isFinite(p.y()) || distance(p,x,y) >= radius*radius);
        ordered.sort(Comparator.comparingDouble(p -> distance(p,x,y)));
        var result = new LinkedHashSet<T>();
        for (var point : ordered) result.add(point.value());
        return List.copyOf(result);
    }
    public static <T> List<T> rectangle(List<Point<T>> points,float x1,float y1,float x2,float y2) {
        if(!Float.isFinite(x1)||!Float.isFinite(y1)||!Float.isFinite(x2)||!Float.isFinite(y2)) return List.of();
        float left=Math.min(x1,x2),right=Math.max(x1,x2),top=Math.min(y1,y2),bottom=Math.max(y1,y2);
        var result=new LinkedHashSet<T>();
        for(var p:points) if(Float.isFinite(p.x()) && Float.isFinite(p.y())
                && p.x()>=left && p.x()<=right && p.y()>=top && p.y()<=bottom) result.add(p.value());
        return List.copyOf(result);
    }
    private static double distance(Point<?> p,float x,float y) {
        double dx=(double)x-p.x(), dy=(double)y-p.y();
        return dx*dx+dy*dy;
    }
}
