package com.director.actor.rig.skeleton;

import org.joml.Vector3f;

import java.util.List;

/**
 * Ships one example custom skeleton so users can see the full
 * "custom rig -> keyframe animation -> render" pipeline without authoring
 * a rig file by hand first.
 */
public final class BuiltInCustomRigs {
    /** Same bone topology as {@link HumanoidRig}, but with chibi-style proportions (bigger head, shorter legs). */
    public static final RigDefinition CHIBI_HUMANOID = new RigDefinition(
            "director:custom_chibi_humanoid",
            "Chibi Humanoid (示例)",
            List.of(
                    bone(HumanoidRig.ROOT, -1, BoneTransform.identity(), BoneCapabilities.full()),
                    bone(HumanoidRig.BODY, 0, BoneTransform.translation(0f, 0.9f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HumanoidRig.HEAD, 1, new BoneTransform(new Vector3f(0f, 0.45f, 0f),
                            new org.joml.Quaternionf(), new Vector3f(1.3f)), BoneCapabilities.rotationAndScale()),
                    bone(HumanoidRig.LEFT_ARM, 1, BoneTransform.translation(0.4f, 0.1f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HumanoidRig.RIGHT_ARM, 1, BoneTransform.translation(-0.4f, 0.1f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HumanoidRig.LEFT_LEG, 0, BoneTransform.translation(0.125f, 0.5f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HumanoidRig.RIGHT_LEG, 0, BoneTransform.translation(-0.125f, 0.5f, 0f), BoneCapabilities.rotationAndScale())
            ));

    /** Non-humanoid-topology demo: proves arbitrary rigs (rendered via block-cuboid models) work too. */
    public static final RigDefinition SIMPLE_ROBOT = new RigDefinition(
            "director:custom_simple_robot",
            "Simple Robot (\u793a\u4f8b)",
            List.of(
                    bone("root", -1, BoneTransform.identity(), BoneCapabilities.full()),
                    bone("torso", 0, BoneTransform.translation(0f, 1.0f, 0f), BoneCapabilities.rotationAndScale()),
                    bone("head", 1, BoneTransform.translation(0f, 0.75f, 0f), BoneCapabilities.rotationAndScale()),
                    bone("left_arm", 1, BoneTransform.translation(0.45f, 0.6f, 0f), BoneCapabilities.rotationAndScale()),
                    bone("right_arm", 1, BoneTransform.translation(-0.45f, 0.6f, 0f), BoneCapabilities.rotationAndScale()),
                    bone("antenna", 2, BoneTransform.translation(0f, 0.3f, 0f), BoneCapabilities.rotationOnly())
            ));

    private BuiltInCustomRigs() {}

    /** Idempotent: registers built-in demo rigs into the project custom rig library if missing. */
    public static void registerDefaults() {
        CustomRigLibrary library = CustomRigLibrary.getInstance();
        if (!library.contains(CHIBI_HUMANOID.id())) {
            library.register(CHIBI_HUMANOID);
        }
        if (!library.contains(SIMPLE_ROBOT.id())) {
            library.register(SIMPLE_ROBOT);
        }
    }

    private static BoneDefinition bone(String id, int parent, BoneTransform rest, BoneCapabilities capabilities) {
        return new BoneDefinition(id, id, parent, rest, capabilities);
    }
}
