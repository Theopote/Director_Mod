package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.ActorRouteClip;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Actor 路径曲线采样：预览、弧长与沿路径位移。
 */
public final class ActorRouteSampler {

    private static final int CURVE_SAMPLES_PER_SEGMENT = 12;
    private static final float MIN_SEGMENT_LENGTH = 1.0e-6f;

    private ActorRouteSampler() {
    }

    public record RouteSample(Vector3f position, Vector3f direction) {
    }

    public static List<Vec3d> sampleCurvePositions(List<Vector3f> waypoints, ActorRouteClip.InterpolationMode mode) {
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        if (waypoints.size() == 1) {
            Vector3f p = waypoints.getFirst();
            return List.of(new Vec3d(p.x, p.y, p.z));
        }

        ActorRouteClip.InterpolationMode resolved = mode != null ? mode : ActorRouteClip.InterpolationMode.LINEAR;
        return switch (resolved) {
            case LINEAR -> sampleLinearPolyline(waypoints);
            case SMOOTH -> sampleSmoothPolyline(waypoints);
            case CATMULL_ROM -> sampleCatmullRomSpline(waypoints);
        };
    }

    public static float computeCurveLength(List<Vector3f> waypoints, ActorRouteClip.InterpolationMode mode) {
        List<Vec3d> curve = sampleCurvePositions(waypoints, mode);
        float total = 0.0f;
        for (int i = 0; i < curve.size() - 1; i++) {
            total += (float) curve.get(i).distanceTo(curve.get(i + 1));
        }
        return total;
    }

    /** Ground locomotion measures horizontal travel; raising control points must not slow it down. */
    public static float computeGroundCurveLength(List<Vector3f> waypoints, ActorRouteClip.InterpolationMode mode) {
        List<Vec3d> curve = sampleCurvePositions(waypoints, mode);
        double length = 0;
        for (int i = 1; i < curve.size(); i++) {
            Vec3d a = curve.get(i - 1), b = curve.get(i);
            length += Math.hypot(b.x - a.x, b.z - a.z);
        }
        return (float) length;
    }

    public static RouteSample sampleAtDistance(List<Vector3f> waypoints,
                                               float distance,
                                               ActorRouteClip.InterpolationMode mode) {
        List<Vec3d> curve = sampleCurvePositions(waypoints, mode);
        if (curve.isEmpty()) {
            return new RouteSample(new Vector3f(), new Vector3f(0.0f, 0.0f, 1.0f));
        }
        if (curve.size() == 1) {
            return new RouteSample(toVector3f(curve.getFirst()), new Vector3f(0.0f, 0.0f, 1.0f));
        }

        float remaining = Math.max(0.0f, distance);
        for (int i = 0; i < curve.size() - 1; i++) {
            Vec3d from = curve.get(i);
            Vec3d to = curve.get(i + 1);
            float segmentLength = (float) from.distanceTo(to);
            if (segmentLength <= MIN_SEGMENT_LENGTH) {
                continue;
            }
            if (remaining <= segmentLength) {
                float t = remaining / segmentLength;
                Vector3f pos = lerp(from, to, t);
                Vector3f dir = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
                if (dir.lengthSquared() > 1.0e-6f) {
                    dir.normalize();
                } else {
                    dir.set(0.0f, 0.0f, 1.0f);
                }
                return new RouteSample(pos, dir);
            }
            remaining -= segmentLength;
        }

        Vec3d last = curve.getLast();
        Vec3d prev = curve.get(curve.size() - 2);
        Vector3f dir = new Vector3f((float) (last.x - prev.x), (float) (last.y - prev.y), (float) (last.z - prev.z));
        if (dir.lengthSquared() > 1.0e-6f) {
            dir.normalize();
        } else {
            dir.set(0.0f, 0.0f, 1.0f);
        }
        return new RouteSample(toVector3f(last), dir);
    }

    private static List<Vec3d> sampleLinearPolyline(List<Vector3f> waypoints) {
        List<Vec3d> result = new ArrayList<>(waypoints.size());
        for (Vector3f point : waypoints) {
            result.add(new Vec3d(point.x, point.y, point.z));
        }
        return result;
    }

    private static List<Vec3d> sampleSmoothPolyline(List<Vector3f> waypoints) {
        List<Vec3d> result = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector3f from = waypoints.get(i);
            Vector3f to = waypoints.get(i + 1);
            int samples = i == waypoints.size() - 2 ? CURVE_SAMPLES_PER_SEGMENT + 1 : CURVE_SAMPLES_PER_SEGMENT;
            for (int s = 0; s < samples; s++) {
                if (i > 0 && s == 0) {
                    continue;
                }
                float t = s / (float) CURVE_SAMPLES_PER_SEGMENT;
                float eased = t * t * (3.0f - 2.0f * t);
                result.add(new Vec3d(
                        from.x + (to.x - from.x) * eased,
                        from.y + (to.y - from.y) * eased,
                        from.z + (to.z - from.z) * eased
                ));
            }
        }
        return result;
    }

    private static List<Vec3d> sampleCatmullRomSpline(List<Vector3f> waypoints) {
        List<Vec3d> result = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector3f p0 = waypoints.get(Math.max(0, i - 1));
            Vector3f p1 = waypoints.get(i);
            Vector3f p2 = waypoints.get(i + 1);
            Vector3f p3 = waypoints.get(Math.min(waypoints.size() - 1, i + 2));
            int samples = i == waypoints.size() - 2 ? CURVE_SAMPLES_PER_SEGMENT + 1 : CURVE_SAMPLES_PER_SEGMENT;
            for (int s = 0; s < samples; s++) {
                if (i > 0 && s == 0) {
                    continue;
                }
                float t = s / (float) CURVE_SAMPLES_PER_SEGMENT;
                result.add(catmullRom(p0, p1, p2, p3, t));
            }
        }
        return result;
    }

    private static Vec3d catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        float t2 = clamped * clamped;
        float t3 = t2 * clamped;

        double x = 0.5 * (
                2.0 * p1.x
                        + (-p0.x + p2.x) * clamped
                        + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                        + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3
        );
        double y = 0.5 * (
                2.0 * p1.y
                        + (-p0.y + p2.y) * clamped
                        + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                        + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3
        );
        double z = 0.5 * (
                2.0 * p1.z
                        + (-p0.z + p2.z) * clamped
                        + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2
                        + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3
        );
        return new Vec3d(x, y, z);
    }

    private static Vector3f lerp(Vec3d from, Vec3d to, float t) {
        return new Vector3f(
                (float) (from.x + (to.x - from.x) * t),
                (float) (from.y + (to.y - from.y) * t),
                (float) (from.z + (to.z - from.z) * t)
        );
    }

    private static Vector3f toVector3f(Vec3d point) {
        return new Vector3f((float) point.x, (float) point.y, (float) point.z);
    }
}
