package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.HumanoidTopology;
import com.director.actor.rig.skeleton.QuadrupedRig;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.VillagerRig;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Writes a humanoid-topology rig onto vanilla model parts without dropping their rest pose. */
public final class NativeMobModelPose {
    private NativeMobModelPose() {}

    public static void applyBiped(RigPose pose, ModelPart body, ModelPart head, ModelPart rightArm, ModelPart leftArm,
                                  ModelPart rightLeg, ModelPart leftLeg) {
        if (!humanoid(pose)) return;
        apply(body, pose, HumanoidRig.BODY, null);
        apply(head, pose, HumanoidRig.HEAD, body);
        apply(rightArm, pose, HumanoidRig.RIGHT_ARM, body);
        apply(leftArm, pose, HumanoidRig.LEFT_ARM, body);
        apply(rightLeg, pose, HumanoidRig.RIGHT_LEG, null);
        apply(leftLeg, pose, HumanoidRig.LEFT_LEG, null);
    }

    public static void applyVillager(RigPose pose, ModelPart body, ModelPart head, ModelPart arms,
                                     ModelPart rightLeg, ModelPart leftLeg) {
        if (isRig(pose, VillagerRig.DEFINITION.id())) {
            apply(body, pose, VillagerRig.BODY, null);
            apply(head, pose, VillagerRig.HEAD, body);
            apply(arms, pose, VillagerRig.ARMS, body);
            apply(rightLeg, pose, VillagerRig.RIGHT_LEG, null);
            apply(leftLeg, pose, VillagerRig.LEFT_LEG, null);
        } else if (humanoid(pose)) {
            apply(body, pose, HumanoidRig.BODY, null);
            apply(head, pose, HumanoidRig.HEAD, body);
            apply(arms, pose, NativeMobPoseAngles.dominantArm(pose), body);
            apply(rightLeg, pose, HumanoidRig.RIGHT_LEG, null);
            apply(leftLeg, pose, HumanoidRig.LEFT_LEG, null);
        }
    }

    /**
     * The sampled quadruped pose only has two leg channels. They drive opposite diagonals,
     * matching the profile's alternating gait. Body and head stay on their own channels.
     */
    public static void applyQuadruped(RigPose pose, ModelPart body, ModelPart head, ModelPart rightHindLeg,
                                      ModelPart leftHindLeg, ModelPart rightFrontLeg, ModelPart leftFrontLeg) {
        if (isRig(pose, QuadrupedRig.DEFINITION.id())) {
            apply(body, pose, QuadrupedRig.BODY, null);
            apply(head, pose, QuadrupedRig.HEAD, body);
            apply(leftFrontLeg, pose, QuadrupedRig.LEFT_FRONT_LEG, body);
            apply(rightFrontLeg, pose, QuadrupedRig.RIGHT_FRONT_LEG, body);
            apply(leftHindLeg, pose, QuadrupedRig.LEFT_HIND_LEG, body);
            apply(rightHindLeg, pose, QuadrupedRig.RIGHT_HIND_LEG, body);
        } else if (humanoid(pose)) {
            apply(body, pose, HumanoidRig.BODY, null);
            apply(head, pose, HumanoidRig.HEAD, null);
            apply(leftFrontLeg, pose, HumanoidRig.LEFT_LEG, null);
            apply(rightHindLeg, pose, HumanoidRig.LEFT_LEG, null);
            apply(rightFrontLeg, pose, HumanoidRig.RIGHT_LEG, null);
            apply(leftHindLeg, pose, HumanoidRig.RIGHT_LEG, null);
        }
    }

    private static boolean humanoid(RigPose pose) {
        return pose != null && HumanoidTopology.matches(pose.rig());
    }

    private static boolean isRig(RigPose pose, String id) {
        return pose != null && id.equals(pose.rig().id());
    }

    private static void apply(ModelPart part, RigPose pose, String bone, ModelPart body) {
        if (part == null || pose.rig().findBoneIndex(bone) < 0) return;
        part.resetTransform();
        boolean followBody = body != null;
        Vector3f pivot = new Vector3f();
        if (followBody) {
            var rest = body.getDefaultTransform();
            pivot.set(part.originX - rest.x(), part.originY - rest.y(), part.originZ - rest.z());
        }
        var restRotation = new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch);
        var rotation = NativeMobPoseAngles.rotation(pose, bone, followBody).mul(restRotation);
        var angles = ArmorStandEulerAngles.fromQuaternion(rotation);
        part.pitch = angles.x;
        part.yaw = angles.y;
        part.roll = angles.z;
        Vector3f scale = NativeMobPoseAngles.scale(pose, bone, followBody);
        part.xScale *= scale.x;
        part.yScale *= scale.y;
        part.zScale *= scale.z;
        Vector3f offset = NativeMobPoseAngles.offsetPixels(pose, bone, pivot, followBody);
        part.originX += offset.x;
        part.originY += offset.y;
        part.originZ += offset.z;
    }
}
