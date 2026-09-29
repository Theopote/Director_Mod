package com.director.actor.path;

import com.director.timeline.model.entity.ActorRouteClip;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Deterministic terrain projection. Unsupported terrain truncates the route, never creates flight. */
public final class GroundRoute {
    public interface Terrain {
        OptionalDouble floor(double x, double z, double referenceY, double up, double down);
        boolean clear(Vec3d feet);
    }
    public record Sample(Vec3d position, Vec3d direction, boolean moving) {}
    private final List<Vec3d> points;
    private final double[] distances;
    private final String warning;

    private GroundRoute(List<Vec3d> points, String warning) {
        this.points = List.copyOf(points);
        this.warning = warning;
        distances = new double[points.size()];
        for (int i = 1; i < points.size(); i++) distances[i] = distances[i - 1] + horizontal(points.get(i - 1), points.get(i));
    }

    public static GroundRoute compile(List<Vec3d> curve, Terrain terrain) {
        var points = new ArrayList<Vec3d>();
        if (curve.isEmpty()) return new GroundRoute(points, "路径没有锚点");
        for (Vec3d point : curve) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
                return new GroundRoute(points, "路径坐标无效");
        }
        Vec3d first = curve.getFirst();
        var floor = terrain.floor(first.x, first.z, first.y, 0.6, 64);
        if (floor.isEmpty()) return new GroundRoute(points, "路径起点下方没有可站立地面，或地形尚未加载");
        Vec3d start = new Vec3d(first.x, floor.getAsDouble(), first.z);
        if (!terrain.clear(start)) return new GroundRoute(points, "路径起点空间不足");
        points.add(start);
        for (int i = 1; i < curve.size(); i++) {
            Vec3d a = curve.get(i - 1), b = curve.get(i);
            double length = horizontal(a, b);
            if (!Double.isFinite(length)) return new GroundRoute(points, "路径坐标无效");
            int steps = (int) Math.ceil(length / 0.2);
            if (steps == 0) continue;
            for (int j = 1; j <= steps; j++) {
                if (points.size() >= 8192) return new GroundRoute(points, "路径过长，请拆成多个片段");
                double t = (double) j / steps;
                double x = a.x + (b.x - a.x) * t, z = a.z + (b.z - a.z) * t;
                Vec3d previous = points.getLast();
                var ground = terrain.floor(x, z, previous.y, 0.6, 1.25);
                if (ground.isEmpty()) return new GroundRoute(points, "前方有陡坎、断路或未加载地形，演员停在可达位置");
                Vec3d next = new Vec3d(x, ground.getAsDouble(), z);
                if (!terrain.clear(next)) return new GroundRoute(points, "前方空间不足，演员停在障碍物前");
                points.add(next);
            }
        }
        return new GroundRoute(points, "");
    }

    public boolean valid() { return !points.isEmpty(); }
    public String warning() { return warning; }
    public double length() { return distances.length == 0 ? 0 : distances[distances.length - 1]; }

    public Sample sample(double time, double speed, ActorRouteClip.LoopMode loop) {
        if (!valid()) throw new IllegalStateException("No supported route");
        double length = length();
        if (length <= 1e-8) return new Sample(points.getFirst(), Vec3d.ZERO, false);
        double distance = Math.max(0, time) * speed;
        boolean reverse = false;
        // A blocked route must not teleport back and attempt the obstacle repeatedly.
        if (!warning.isEmpty()) loop = ActorRouteClip.LoopMode.ONCE;
        if (loop == ActorRouteClip.LoopMode.LOOP) distance %= length;
        if (loop == ActorRouteClip.LoopMode.PING_PONG) {
            distance %= length * 2;
            reverse = distance >= length;
            if (reverse) distance = 2 * length - distance;
        }
        boolean moving = loop != ActorRouteClip.LoopMode.ONCE || distance < length;
        distance = Math.min(distance, length);
        int index = java.util.Arrays.binarySearch(distances, distance);
        if (index < 0) index = -index - 1;
        index = Math.max(1, Math.min(points.size() - 1, index));
        Vec3d a = points.get(index - 1), b = points.get(index);
        double t = (distance - distances[index - 1]) / (distances[index] - distances[index - 1]);
        Vec3d position = a.lerp(b, t);
        Vec3d direction = new Vec3d(b.x - a.x, 0, b.z - a.z).normalize();
        return new Sample(position, reverse ? direction.negate() : direction, moving);
    }

    /** One short hop per clip, independent of route looping and render frame history. */
    public static double jumpOffset(double time, double start, double duration, double height) {
        duration = Math.max(0.1, Math.min(0.8, duration));
        height = Math.max(0, Math.min(1.25, height));
        double t = (time - start) / duration;
        return t <= 0 || t >= 1 ? 0 : 4 * height * t * (1 - t);
    }

    private static double horizontal(Vec3d a, Vec3d b) { return Math.hypot(b.x - a.x, b.z - a.z); }
}
