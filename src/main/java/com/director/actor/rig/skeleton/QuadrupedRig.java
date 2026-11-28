package com.director.actor.rig.skeleton;

import java.util.List;

/** Native four-legged topology with independently authored front and hind legs. */
public final class QuadrupedRig {
    public static final String ROOT = "root";
    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String LEFT_FRONT_LEG = "left_front_leg";
    public static final String RIGHT_FRONT_LEG = "right_front_leg";
    public static final String LEFT_HIND_LEG = "left_hind_leg";
    public static final String RIGHT_HIND_LEG = "right_hind_leg";

    public static final RigDefinition DEFINITION = new RigDefinition(
            "director:quadruped",
            "Quadruped",
            List.of(
                    bone(ROOT, -1, BoneTransform.identity(), BoneCapabilities.full()),
                    bone(BODY, 0, BoneTransform.translation(0f, .8f, 0f), BoneCapabilities.rotationAndScale()),
                    bone(HEAD, 1, BoneTransform.translation(0f, .35f, -.55f), BoneCapabilities.rotationAndScale()),
                    bone(LEFT_FRONT_LEG, 1, BoneTransform.translation(.22f, -.1f, -.4f), BoneCapabilities.rotationAndScale()),
                    bone(RIGHT_FRONT_LEG, 1, BoneTransform.translation(-.22f, -.1f, -.4f), BoneCapabilities.rotationAndScale()),
                    bone(LEFT_HIND_LEG, 1, BoneTransform.translation(.22f, -.1f, .4f), BoneCapabilities.rotationAndScale()),
                    bone(RIGHT_HIND_LEG, 1, BoneTransform.translation(-.22f, -.1f, .4f), BoneCapabilities.rotationAndScale())
            ),
            List.of(
                    LegChain.thigh(LEFT_FRONT_LEG), LegChain.thigh(RIGHT_FRONT_LEG),
                    LegChain.thigh(LEFT_HIND_LEG), LegChain.thigh(RIGHT_HIND_LEG)));

    private QuadrupedRig() {}

    private static BoneDefinition bone(String id, int parent, BoneTransform rest, BoneCapabilities capabilities) {
        return new BoneDefinition(id, displayName(id), parent, rest, capabilities);
    }

    private static String displayName(String id) {
        return switch (id) {
            case ROOT           -> "Root";
            case BODY           -> "Body";
            case HEAD           -> "Head";
            case LEFT_FRONT_LEG  -> "Left Front Leg";
            case RIGHT_FRONT_LEG -> "Right Front Leg";
            case LEFT_HIND_LEG   -> "Left Hind Leg";
            case RIGHT_HIND_LEG  -> "Right Hind Leg";
            default             -> id;
        };
    }
}
