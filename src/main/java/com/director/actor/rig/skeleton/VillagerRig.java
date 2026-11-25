package com.director.actor.rig.skeleton;

import java.util.List;

/** Native villager topology: the crossed arms are one controlled model part. */
public final class VillagerRig {
    public static final String ROOT = "root";
    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String ARMS = "arms";
    public static final String LEFT_LEG = "left_leg";
    public static final String RIGHT_LEG = "right_leg";

    public static final RigDefinition DEFINITION = new RigDefinition(
            "director:villager",
            "Villager",
            List.of(
                    bone(ROOT, -1, BoneTransform.identity(), BoneCapabilities.full()),
                    bone(BODY, 0, BoneTransform.translation(0f, 1.05f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HEAD, 1, BoneTransform.translation(0f, .7f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(ARMS, 1, BoneTransform.translation(0f, .25f, -.2f), BoneCapabilities.rotationAndScale()),
                    bone(LEFT_LEG, 0, BoneTransform.translation(.125f, .55f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(RIGHT_LEG, 0, BoneTransform.translation(-.125f, .55f, 0f), BoneCapabilities.rotationAndScale())
            ));

    private VillagerRig() {}

    private static BoneDefinition bone(String id, int parent, BoneTransform rest, BoneCapabilities capabilities) {
        return new BoneDefinition(id, id, parent, rest, capabilities);
    }
}
