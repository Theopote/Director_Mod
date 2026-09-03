package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.RigWorldPose;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Stateless ray-to-bone handle picking math. */
public final class BoneRayPicker {
    private BoneRayPicker() {}

    public static String pick(RigWorldPose pose, Vec3d origin, Vec3d direction) {
        if (pose == null || origin == null || direction == null || direction.lengthSquared() < 1.0e-10) {
            return null;
        }
        Vec3d ray = direction.normalize();
        String best = null;
        double bestRayDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < pose.rig().boneCount(); i++) {
            Vector3f p = pose.position(i);
            Vec3d point = new Vec3d(p.x, p.y, p.z);
            Vec3d offset = point.subtract(origin);
            double alongRay = offset.dotProduct(ray);
            if (alongRay < 0.0 || alongRay >= bestRayDistance) continue;
            Vec3d closest = origin.add(ray.multiply(alongRay));
            double threshold = 0.08 + Math.min(0.22, Math.sqrt(origin.squaredDistanceTo(point)) * 0.012);
            if (closest.squaredDistanceTo(point) <= threshold * threshold) {
                best = pose.rig().bone(i).id();
                bestRayDistance = alongRay;
            }
        }
        return best;
    }
}
