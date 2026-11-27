package com.director.actor.rig.skeleton;

import com.director.actor.rig.Pose;
import org.joml.Quaternionf;

import java.util.Objects;

/** Converts the legacy procedural pose into the native rig used by its actor model. */
public final class LegacyMobPoseAdapter {
    private LegacyMobPoseAdapter() {}

    public static RigPose convert(Pose source, RigDefinition rig) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(rig, "rig");
        if (HumanoidTopology.matches(rig)) return LegacyHumanoidPoseAdapter.convert(source, rig);
        if (VillagerRig.DEFINITION.id().equals(rig.id())) return villager(source, rig);
        if (QuadrupedRig.DEFINITION.id().equals(rig.id())) return quadruped(source, rig);
        return new RigPose(rig);
    }

    private static RigPose villager(Pose source, RigDefinition rig) {
        var result = new RigPose(rig);
        rotate(result, VillagerRig.BODY, source.bodyPitch, source.bodyYaw, source.bodyRoll);
        rotate(result, VillagerRig.HEAD, source.headPitch, source.headYaw, 0);
        // The folded arms are one model part. Keep a fixed source throughout the motion cycle.
        rotate(result, VillagerRig.ARMS, source.rightArmPitch, source.rightArmYaw, source.rightArmRoll);
        rotate(result, VillagerRig.LEFT_LEG, source.leftLegPitch, 0, 0);
        rotate(result, VillagerRig.RIGHT_LEG, source.rightLegPitch, 0, 0);
        return result;
    }

    private static RigPose quadruped(Pose source, RigDefinition rig) {
        var result = new RigPose(rig);
        rotate(result, QuadrupedRig.BODY, source.bodyPitch, source.bodyYaw, source.bodyRoll);
        rotate(result, QuadrupedRig.HEAD, source.headPitch, source.headYaw, 0);
        rotate(result, QuadrupedRig.LEFT_FRONT_LEG, source.leftLegPitch, 0, 0);
        rotate(result, QuadrupedRig.RIGHT_HIND_LEG, source.leftLegPitch, 0, 0);
        rotate(result, QuadrupedRig.RIGHT_FRONT_LEG, source.rightLegPitch, 0, 0);
        rotate(result, QuadrupedRig.LEFT_HIND_LEG, source.rightLegPitch, 0, 0);
        return result;
    }

    private static void rotate(RigPose target, String boneId, float pitch, float yaw, float roll) {
        var rest = target.localTransform(boneId);
        target.setLocalTransform(boneId, rest.withRotation(new Quaternionf().rotateXYZ(pitch, yaw, roll)));
    }
}
