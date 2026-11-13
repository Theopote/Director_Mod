package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local rig rotation in the ModelPart pitch/yaw/roll convention. */
public final class NativeMobPoseAngles {
    private NativeMobPoseAngles() {}

    public record Euler(float pitch, float yaw, float roll) {}

    /** Body rotation is included when the bone hangs from the body and the mesh part does not. */
    public static Euler local(RigPose pose, String bone, boolean followBody) {
        if (pose == null || bone == null) return null;
        int index = pose.rig().findBoneIndex(bone);
        if (index < 0) return null;
        Quaternionf rotation = pose.localTransform(index).rotation();
        if (followBody) {
            int parent = pose.rig().bone(index).parentIndex();
            if (parent >= 0 && HumanoidRig.BODY.equals(pose.rig().bone(parent).id())) {
                rotation = pose.localTransform(parent).rotation().mul(rotation);
            }
        }
        Vector3f euler = ArmorStandEulerAngles.fromQuaternion(rotation);
        return new Euler(euler.x, euler.y, euler.z);
    }

    public static Euler average(RigPose pose, String left, String right, boolean followBody) {
        Euler a = local(pose, left, followBody);
        Euler b = local(pose, right, followBody);
        if (a == null) return b;
        if (b == null) return a;
        return new Euler((a.pitch + b.pitch) * 0.5f, (a.yaw + b.yaw) * 0.5f, (a.roll + b.roll) * 0.5f);
    }

    /** Authored offset from the rest translation, in model pixels. Vanilla model Y points down. */
    public static Vector3f translationPixels(RigPose pose, String bone) {
        Vector3f delta = new Vector3f();
        if (pose == null || bone == null) return delta;
        int index = pose.rig().findBoneIndex(bone);
        if (index < 0) return delta;
        pose.localTransform(index).translation().sub(pose.rig().bone(index).restLocal().translation(), delta);
        delta.y = -delta.y;
        return delta.mul(16f);
    }

    public static Vector3f scale(RigPose pose, String bone) {
        if (pose == null || bone == null) return new Vector3f(1);
        int index = pose.rig().findBoneIndex(bone);
        if (index < 0) return new Vector3f(1);
        return pose.localTransform(index).scale();
    }
}
