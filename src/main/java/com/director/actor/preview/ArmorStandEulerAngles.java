package com.director.actor.preview;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** Converts rig quaternions to the pitch/yaw/roll convention used by ModelPart.rotationZYX. */
final class ArmorStandEulerAngles {
    private ArmorStandEulerAngles() {}

    static Vector3f fromQuaternion(Quaternionf rotation) {
        Objects.requireNonNull(rotation, "rotation");
        return rotation.getEulerAnglesZYX(new Vector3f());
    }
}
