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
    private static double distance(Point<?> p,float x,float y) {
        double dx=(double)x-p.x(), dy=(double)y-p.y();
        return dx*dx+dy*dy;
    }
}
