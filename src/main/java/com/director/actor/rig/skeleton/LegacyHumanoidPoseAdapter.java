package com.director.actor.rig.skeleton;

import com.director.actor.rig.Pose;
import org.joml.Quaternionf;

import java.util.Objects;

/** Bridges the fixed-field legacy Pose into the standard indexed humanoid rig. */
public final class LegacyHumanoidPoseAdapter {
    private LegacyHumanoidPoseAdapter() {}

    public static RigPose convert(Pose source) {
        Objects.requireNonNull(source, "source");
        RigPose result = new RigPose(HumanoidRig.DEFINITION);
        rotate(result, HumanoidRig.BODY, source.bodyPitch, source.bodyYaw, source.bodyRoll);
        rotate(result, HumanoidRig.HEAD, source.headPitch, source.headYaw, 0f);
        rotate(result, HumanoidRig.LEFT_ARM, source.leftArmPitch, source.leftArmYaw, source.leftArmRoll);
        rotate(result, HumanoidRig.RIGHT_ARM, source.rightArmPitch, source.rightArmYaw, source.rightArmRoll);
        rotate(result, HumanoidRig.LEFT_LEG, source.leftLegPitch, 0f, 0f);
        rotate(result, HumanoidRig.RIGHT_LEG, source.rightLegPitch, 0f, 0f);
        return result;
    }

    private static void rotate(RigPose target, String boneId, float pitch, float yaw, float roll) {
        BoneTransform rest = target.localTransform(boneId);
        Quaternionf rotation = new Quaternionf().rotateXYZ(pitch, yaw, roll);
        target.setLocalTransform(boneId, rest.withRotation(rotation));
    }
}
