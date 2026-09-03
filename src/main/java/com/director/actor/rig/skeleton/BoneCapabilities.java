package com.director.actor.rig.skeleton;

/** Declares which local transform channels an editor may author for a bone. */
public record BoneCapabilities(boolean translation, boolean rotation, boolean scale) {
    public static BoneCapabilities rotationOnly() {
        return new BoneCapabilities(false, true, false);
    }

    public static BoneCapabilities rotationAndScale() {
        return new BoneCapabilities(false, true, true);
    }

    public static BoneCapabilities full() {
        return new BoneCapabilities(true, true, true);
    }
}
