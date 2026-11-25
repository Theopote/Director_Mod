package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.HumanoidTopology;
import com.director.actor.rig.skeleton.QuadrupedRig;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.VillagerRig;
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
        if (isRig(pose, VillagerRig.DEFINITION.id())) {
            apply(body, pose, VillagerRig.BODY, false);
            apply(head, pose, VillagerRig.HEAD, true);
            apply(arms, pose, VillagerRig.ARMS, true);
            apply(rightLeg, pose, VillagerRig.RIGHT_LEG, false);
            apply(leftLeg, pose, VillagerRig.LEFT_LEG, false);
        } else if (humanoid(pose)) {
            apply(body, pose, HumanoidRig.BODY, false);
            apply(head, pose, HumanoidRig.HEAD, true);
            apply(arms, pose, NativeMobPoseAngles.dominantArm(pose), true);
            apply(rightLeg, pose, HumanoidRig.RIGHT_LEG, false);
            apply(leftLeg, pose, HumanoidRig.LEFT_LEG, false);
        }
    }

    /**
     * The sampled quadruped pose only has two leg channels. They drive opposite diagonals,
     * matching the profile's alternating gait. Body and head stay on their own channels.
     */
    public static void applyQuadruped(RigPose pose, ModelPart body, ModelPart head, ModelPart rightHindLeg,
                                      ModelPart leftHindLeg, ModelPart rightFrontLeg, ModelPart leftFrontLeg) {
        if (isRig(pose, QuadrupedRig.DEFINITION.id())) {
            apply(body, pose, QuadrupedRig.BODY, false);
            apply(head, pose, QuadrupedRig.HEAD, false);
            apply(leftFrontLeg, pose, QuadrupedRig.LEFT_FRONT_LEG, false);
            apply(rightFrontLeg, pose, QuadrupedRig.RIGHT_FRONT_LEG, false);
            apply(leftHindLeg, pose, QuadrupedRig.LEFT_HIND_LEG, false);
            apply(rightHindLeg, pose, QuadrupedRig.RIGHT_HIND_LEG, false);
        } else if (humanoid(pose)) {
            apply(body, pose, HumanoidRig.BODY, false);
            apply(head, pose, HumanoidRig.HEAD, false);
            apply(leftFrontLeg, pose, HumanoidRig.LEFT_LEG, false);
            apply(rightHindLeg, pose, HumanoidRig.LEFT_LEG, false);
            apply(rightFrontLeg, pose, HumanoidRig.RIGHT_LEG, false);
            apply(leftHindLeg, pose, HumanoidRig.RIGHT_LEG, false);
        }
    }

    private static boolean humanoid(RigPose pose) {
        return pose != null && HumanoidTopology.matches(pose.rig());
    }

    private static boolean isRig(RigPose pose, String id) {
        return pose != null && id.equals(pose.rig().id());
    }

    private static void apply(ModelPart part, RigPose pose, String bone, boolean followBody) {
        NativeMobPoseAngles.Euler euler = NativeMobPoseAngles.local(pose, bone, followBody);
        if (part == null || euler == null) return;
        write(part, pose, bone, euler);
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
