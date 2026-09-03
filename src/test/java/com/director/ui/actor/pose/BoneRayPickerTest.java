package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.RigWorldPose;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoneRayPickerTest {
    @Test
    void picksClosestBoneHandleAlongRay() {
        RigWorldPose pose = RigWorldPose.resolve(new RigPose(HumanoidRig.DEFINITION), new Matrix4f());
        Vec3d origin = new Vec3d(0.375, 1.375, 3.0);

        assertEquals(HumanoidRig.LEFT_ARM, BoneRayPicker.pick(pose, origin, new Vec3d(0, 0, -1)));
    }

    @Test
    void returnsNullWhenRayMissesSkeleton() {
        RigWorldPose pose = RigWorldPose.resolve(new RigPose(HumanoidRig.DEFINITION), new Matrix4f());

        assertNull(BoneRayPicker.pick(pose, new Vec3d(5, 5, 5), new Vec3d(1, 0, 0)));
    }
}
