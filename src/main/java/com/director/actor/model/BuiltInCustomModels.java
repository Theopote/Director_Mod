package com.director.actor.model;

import com.director.actor.rig.skeleton.BuiltInCustomRigs;
import org.joml.Vector3f;

import java.util.List;

/** Ships one block-cuboid model for the non-humanoid {@link BuiltInCustomRigs#SIMPLE_ROBOT} demo rig. */
public final class BuiltInCustomModels {
    public static final CustomModelDefinition SIMPLE_ROBOT = new CustomModelDefinition(
            "director:custom_simple_robot_model",
            "Simple Robot (\u793a\u4f8b\u6a21\u578b)",
            BuiltInCustomRigs.SIMPLE_ROBOT.id(),
            List.of(
                    new BoneCuboid("torso", new Vector3f(-0.3f, -0.3f, -0.2f), new Vector3f(0.6f, 0.7f, 0.4f),
                            "minecraft:iron_block"),
                    new BoneCuboid("head", new Vector3f(-0.25f, -0.25f, -0.25f), new Vector3f(0.5f, 0.5f, 0.5f),
                            "minecraft:light_gray_concrete"),
                    new BoneCuboid("left_arm", new Vector3f(-0.15f, -0.5f, -0.15f), new Vector3f(0.3f, 0.5f, 0.3f),
                            "minecraft:gray_concrete"),
                    new BoneCuboid("right_arm", new Vector3f(-0.15f, -0.5f, -0.15f), new Vector3f(0.3f, 0.5f, 0.3f),
                            "minecraft:gray_concrete"),
                    new BoneCuboid("antenna", new Vector3f(-0.05f, 0f, -0.05f), new Vector3f(0.1f, 0.3f, 0.1f),
                            "minecraft:red_concrete")
            ));

    private BuiltInCustomModels() {}

    /** Idempotent: registers built-in demo models into the project custom model library if missing. */
    public static void registerDefaults() {
        CustomModelLibrary library = CustomModelLibrary.getInstance();
        if (!library.contains(SIMPLE_ROBOT.id())) {
            library.register(SIMPLE_ROBOT);
        }
    }
}
