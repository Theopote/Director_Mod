package com.director.actor.rig.skeleton;

/**
 * Checks whether a rig shares the standard Director humanoid bone topology
 * (bone ids + parent structure), regardless of proportions (translation/scale).
 *
 * <p>This lets a custom-authored rig (different limb lengths, body scale, etc.)
 * reuse the existing humanoid animation UI and ArmorStand renderer, without
 * requiring the exact {@link HumanoidRig#DEFINITION} instance.
 */
public final class HumanoidTopology {
    private HumanoidTopology() {}

    public static boolean matches(RigDefinition rig) {
        if (rig == null || rig.boneCount() != HumanoidRig.DEFINITION.boneCount()) return false;
        return sameParent(rig, HumanoidRig.ROOT, null)
                && sameParent(rig, HumanoidRig.BODY, HumanoidRig.ROOT)
                && sameParent(rig, HumanoidRig.HEAD, HumanoidRig.BODY)
                && sameParent(rig, HumanoidRig.LEFT_ARM, HumanoidRig.BODY)
                && sameParent(rig, HumanoidRig.RIGHT_ARM, HumanoidRig.BODY)
                && sameParent(rig, HumanoidRig.LEFT_LEG, HumanoidRig.ROOT)
                && sameParent(rig, HumanoidRig.RIGHT_LEG, HumanoidRig.ROOT);
    }

    private static boolean sameParent(RigDefinition rig, String boneId, String expectedParentId) {
        int index = rig.findBoneIndex(boneId);
        if (index < 0) return false;
        int parentIndex = rig.bone(index).parentIndex();
        if (expectedParentId == null) return parentIndex == -1;
        int expectedParentIndex = rig.findBoneIndex(expectedParentId);
        return expectedParentIndex >= 0 && parentIndex == expectedParentIndex;
    }
}
