package com.director.actor.rig.skeleton;

/**
 * Declares one locomotor limb for route foot-contact / IK.
 * {@code lowerBone} may be null for single-segment legs (villager, quadruped).
 */
public record LegChain(String legBone, String lowerBone, String footBone) {
    public LegChain {
        if (legBone == null || legBone.isBlank()) throw new IllegalArgumentException("legBone must not be blank");
        if (lowerBone != null && lowerBone.isBlank()) lowerBone = null;
        if (footBone != null && footBone.isBlank()) footBone = null;
    }

    public static LegChain thigh(String legBone) {
        return new LegChain(legBone, null, null);
    }

    public static LegChain thighAndShin(String legBone, String shinBone) {
        return new LegChain(legBone, shinBone, null);
    }

    public boolean hasKnee() {
        return lowerBone != null;
    }
}
