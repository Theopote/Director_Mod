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
        if (pose.rig().id().equals(com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION.id()))
            return pickPlayerVolumes(pose,origin,ray);
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
    private static String pickPlayerVolumes(RigWorldPose pose,Vec3d origin,Vec3d ray) {
        String best=null; double nearest=Double.POSITIVE_INFINITY;
        for(int i=0;i<pose.rig().boneCount();i++) {
            String id=pose.rig().bone(i).id(); float[] bounds=bounds(id);
            if(bounds==null) continue;
            var inverse=pose.transform(i).invert();
            var o=inverse.transformPosition(new Vector3f((float)origin.x,(float)origin.y,(float)origin.z));
            var d=inverse.transformDirection(new Vector3f((float)ray.x,(float)ray.y,(float)ray.z));
            if(!o.isFinite() || !d.isFinite()) continue;
            double near=0,far=Double.POSITIVE_INFINITY;
            for(int axis=0;axis<3;axis++) {
                double v=o.get(axis),speed=d.get(axis),min=bounds[axis]-.0125,max=bounds[axis+3]+.0125;
                if(Math.abs(speed)<1e-9) { if(v<min || v>max) { far=-1; break; } }
                else {
                    double a=(min-v)/speed,b=(max-v)/speed;
                    near=Math.max(near,Math.min(a,b)); far=Math.min(far,Math.max(a,b));
                }
            }
            if(near<=far && near<nearest) { nearest=near; best=id; }
        }
        return best;
    }
    private static float[] bounds(String id) {
        return switch(id) {
            case "body" -> new float[]{-.25f,-.75f,-.125f,.25f,0,.125f};
            case "head" -> new float[]{-.25f,-.5f,-.25f,.25f,0,.25f};
            case "left_arm" -> new float[]{-.0625f,-.125f,-.125f,.1875f,.25f,.125f};
            case "right_arm" -> new float[]{-.1875f,-.125f,-.125f,.0625f,.25f,.125f};
            case "left_forearm" -> new float[]{-.0625f,0,-.125f,.1875f,.375f,.125f};
            case "right_forearm" -> new float[]{-.1875f,0,-.125f,.0625f,.375f,.125f};
            case "left_leg","right_leg","left_shin","right_shin" -> new float[]{-.125f,0,-.125f,.125f,.375f,.125f};
            default -> null;
        };
    }
}
