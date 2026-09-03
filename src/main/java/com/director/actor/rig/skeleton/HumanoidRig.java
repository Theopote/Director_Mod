package com.director.actor.rig.skeleton;

import java.util.List;

/** Standard Director humanoid rig used by the first animation-editor milestone. */
public final class HumanoidRig {
    public static final String ROOT = "root";
    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_LEG = "left_leg";
    public static final String RIGHT_LEG = "right_leg";

    public static final RigDefinition DEFINITION = new RigDefinition(
            "director:humanoid",
            "Humanoid",
            List.of(
                    bone(ROOT, -1, BoneTransform.identity(), BoneCapabilities.full()),
                    bone(BODY, 0, BoneTransform.translation(0f, 1.25f, 0f), BoneCapabilities.rotationOnly()),
                    bone(HEAD, 1, BoneTransform.translation(0f, 0.375f, 0f), BoneCapabilities.rotationOnly()),
                    bone(LEFT_ARM, 1, BoneTransform.translation(0.375f, 0.125f, 0f), BoneCapabilities.rotationOnly()),
                    bone(RIGHT_ARM, 1, BoneTransform.translation(-0.375f, 0.125f, 0f), BoneCapabilities.rotationOnly()),
                    bone(LEFT_LEG, 0, BoneTransform.translation(0.125f, 0.75f, 0f), BoneCapabilities.rotationOnly()),
                    bone(RIGHT_LEG, 0, BoneTransform.translation(-0.125f, 0.75f, 0f), BoneCapabilities.rotationOnly())
            ));

    private HumanoidRig() {}

    private static BoneDefinition bone(String id, int parent, BoneTransform rest, BoneCapabilities capabilities) {
        return new BoneDefinition(id, displayName(id), parent, rest, capabilities);
    }

    private static String displayName(String id) {
        return switch (id) {
            case ROOT -> "Root";
            case BODY -> "Body";
            case HEAD -> "Head";
            case LEFT_ARM -> "Left Arm";
            case RIGHT_ARM -> "Right Arm";
            case LEFT_LEG -> "Left Leg";
            case RIGHT_LEG -> "Right Leg";
            default -> id;
        };
    }
}
