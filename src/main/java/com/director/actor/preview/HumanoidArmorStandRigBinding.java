package com.director.actor.preview;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** Maps the standard Director humanoid rig onto Minecraft's ArmorStand pose channels. */
public final class HumanoidArmorStandRigBinding {
    private HumanoidArmorStandRigBinding() {}

    public static void apply(ArmorStandEntity entity, RigPose pose) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(pose, "pose");
        if (pose.rig() != HumanoidRig.DEFINITION
                && !HumanoidRig.DEFINITION.id().equals(pose.rig().id())) {
            throw new IllegalArgumentException("Expected humanoid rig, got " + pose.rig().id());
        }

        entity.setBodyRotation(toEuler(pose, HumanoidRig.BODY));
        entity.setHeadRotation(toEuler(pose, HumanoidRig.HEAD));
        entity.setLeftArmRotation(toEuler(pose, HumanoidRig.LEFT_ARM));
        entity.setRightArmRotation(toEuler(pose, HumanoidRig.RIGHT_ARM));
        entity.setLeftLegRotation(toEuler(pose, HumanoidRig.LEFT_LEG));
        entity.setRightLegRotation(toEuler(pose, HumanoidRig.RIGHT_LEG));
    }

    private static EulerAngle toEuler(RigPose pose, String boneId) {
        Quaternionf rotation = pose.localTransform(boneId).rotation();
        Vector3f radians = ArmorStandEulerAngles.fromQuaternion(rotation);
        return new EulerAngle(
                (float) Math.toDegrees(radians.x),
                (float) Math.toDegrees(radians.y),
                (float) Math.toDegrees(radians.z));
    }
}
