package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.HumanoidTopology;
import com.director.actor.rig.skeleton.RigPose;
import net.minecraft.client.model.ModelPart;
import org.joml.Vector3f;

/** Writes a humanoid-topology rig onto vanilla model parts without dropping their rest pose. */
public final class NativeMobModelPose {
    private NativeMobModelPose() {}

    public static void applyBiped(RigPose pose, ModelPart body, ModelPart head, ModelPart rightArm, ModelPart leftArm,
                                  ModelPart rightLeg, ModelPart leftLeg) {
        if (!humanoid(pose)) return;
        apply(body, pose, HumanoidRig.BODY, false);
        apply(head, pose, HumanoidRig.HEAD, true);
        apply(rightArm, pose, HumanoidRig.RIGHT_ARM, true);
        apply(leftArm, pose, HumanoidRig.LEFT_ARM, true);
        apply(rightLeg, pose, HumanoidRig.RIGHT_LEG, false);
        apply(leftLeg, pose, HumanoidRig.LEFT_LEG, false);
    }

    public static void applyVillager(RigPose pose, ModelPart body, ModelPart head, ModelPart arms,
                                     ModelPart rightLeg, ModelPart leftLeg) {
        if (!humanoid(pose)) return;
        apply(body, pose, HumanoidRig.BODY, false);
        apply(head, pose, HumanoidRig.HEAD, true);
        applyAverage(arms, pose, HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, true);
        apply(rightLeg, pose, HumanoidRig.RIGHT_LEG, false);
        apply(leftLeg, pose, HumanoidRig.LEFT_LEG, false);
    }

    /**
     * The sampled quadruped pose only has two leg channels. They drive opposite diagonals,
     * matching the profile's alternating gait. Body and head stay on their own channels.
     */
    public static void applyQuadruped(RigPose pose, ModelPart body, ModelPart head, ModelPart rightHindLeg,
                                      ModelPart leftHindLeg, ModelPart rightFrontLeg, ModelPart leftFrontLeg) {
        if (!humanoid(pose)) return;
        apply(body, pose, HumanoidRig.BODY, false);
        apply(head, pose, HumanoidRig.HEAD, false);
        apply(leftFrontLeg, pose, HumanoidRig.LEFT_LEG, false);
        apply(rightHindLeg, pose, HumanoidRig.LEFT_LEG, false);
        apply(rightFrontLeg, pose, HumanoidRig.RIGHT_LEG, false);
        apply(leftHindLeg, pose, HumanoidRig.RIGHT_LEG, false);
    }

    private static boolean humanoid(RigPose pose) {
        return pose != null && HumanoidTopology.matches(pose.rig());
    }

    private static void apply(ModelPart part, RigPose pose, String bone, boolean followBody) {
        NativeMobPoseAngles.Euler euler = NativeMobPoseAngles.local(pose, bone, followBody);
        if (part == null || euler == null) return;
        write(part, pose, bone, euler);
    }

    private static void applyAverage(ModelPart part, RigPose pose, String left, String right, boolean followBody) {
        NativeMobPoseAngles.Euler euler = NativeMobPoseAngles.average(pose, left, right, followBody);
        if (part == null || euler == null) return;
        part.resetTransform();
        part.pitch += euler.pitch();
        part.yaw += euler.yaw();
        part.roll += euler.roll();
    }

    private static void write(ModelPart part, RigPose pose, String bone, NativeMobPoseAngles.Euler euler) {
        part.resetTransform();
        part.pitch += euler.pitch();
        part.yaw += euler.yaw();
        part.roll += euler.roll();
        Vector3f scale = NativeMobPoseAngles.scale(pose, bone);
        part.xScale *= scale.x;
        part.yScale *= scale.y;
        part.zScale *= scale.z;
        Vector3f offset = NativeMobPoseAngles.translationPixels(pose, bone);
        part.originX += offset.x;
        part.originY += offset.y;
        part.originZ += offset.z;
    }
}
