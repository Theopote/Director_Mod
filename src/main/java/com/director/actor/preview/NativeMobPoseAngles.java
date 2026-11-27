package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.BoneTransform;
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
        Quaternionf rotation = rotation(pose, bone, followBody);
        Vector3f euler = ArmorStandEulerAngles.fromQuaternion(rotation);
        return new Euler(euler.x, euler.y, euler.z);
    }

    static Quaternionf rotation(RigPose pose, String bone, boolean followBody) {
        Quaternionf rotation = pose.localTransform(bone).rotation();
        var body = bodyParent(pose, bone, followBody);
        return body == null ? rotation : body.rotation().mul(rotation);
    }

    private static BoneTransform bodyParent(
            RigPose pose, String bone, boolean followBody) {
        if (!followBody) return null;
        int parent = pose.rig().bone(pose.rig().requireBoneIndex(bone)).parentIndex();
        return parent >= 0 && HumanoidRig.BODY.equals(pose.rig().bone(parent).id())
                ? pose.localTransform(parent) : null;
    }

    /** Villager has one arm mesh. Equal magnitude keeps the right arm so a symmetric carry still shows. */
    public static String dominantArm(RigPose pose) {
        Euler left = local(pose, HumanoidRig.LEFT_ARM, false);
        Euler right = local(pose, HumanoidRig.RIGHT_ARM, false);
        if (left == null) return HumanoidRig.RIGHT_ARM;
        if (right == null) return HumanoidRig.LEFT_ARM;
        if (magnitude(left) > magnitude(right)) return HumanoidRig.LEFT_ARM;
        return HumanoidRig.RIGHT_ARM;
    }

    private static float magnitude(Euler euler) {
        return euler.pitch * euler.pitch + euler.yaw * euler.yaw + euler.roll * euler.roll;
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

    /**
     * Extra ModelPart origin shift when a sibling mesh must follow body rotation around the torso pivot.
     * Vanilla head/arm parts are often not hierarchy children of body, so rotation alone leaves them behind.
     */
    static Vector3f offsetPixels(RigPose pose, String bone, Vector3f restFromBody, boolean followBody) {
        Vector3f offset = translationPixels(pose, bone);
        var body = bodyParent(pose, bone, followBody);
        if (body == null) return offset;
        offset.add(restFromBody).mul(body.scale());
        return body.rotation().transform(offset).sub(restFromBody)
                .add(translationPixels(pose, HumanoidRig.BODY));
    }

    static Vector3f scale(RigPose pose, String bone, boolean followBody) {
        Vector3f scale = pose.localTransform(bone).scale();
        var body = bodyParent(pose, bone, followBody);
        // ModelPart stores separate rotation and scale, so it cannot represent inherited shear.
        return body == null ? scale : scale.mul(body.scale());
    }
}
