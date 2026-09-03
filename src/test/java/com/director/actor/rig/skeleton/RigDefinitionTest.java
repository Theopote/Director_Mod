package com.director.actor.rig.skeleton;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigDefinitionTest {
    @Test
    void resolvesStableBoneIds() {
        RigDefinition rig = HumanoidRig.DEFINITION;

        assertEquals(7, rig.boneCount());
        assertEquals(HumanoidRig.HEAD, rig.bone(rig.requireBoneIndex(HumanoidRig.HEAD)).id());
        assertEquals(-1, rig.findBoneIndex("missing"));
    }

    @Test
    void humanoidPartsExposeScaleAuthoring() {
        RigDefinition rig = HumanoidRig.DEFINITION;
        for (String boneId : List.of(HumanoidRig.ROOT, HumanoidRig.BODY, HumanoidRig.HEAD,
                HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG)) {
            assertTrue(rig.bone(rig.requireBoneIndex(boneId)).capabilities().scale(), boneId);
        }
    }

    @Test
    void rejectsCyclesAndDuplicateIds() {
        List<BoneDefinition> cycle = List.of(
                bone("root", -1),
                new BoneDefinition("a", "A", 2, BoneTransform.identity(), BoneCapabilities.rotationOnly()),
                new BoneDefinition("b", "B", 1, BoneTransform.identity(), BoneCapabilities.rotationOnly())
        );
        assertThrows(IllegalArgumentException.class, () -> new RigDefinition("test:cycle", "Cycle", cycle));

        List<BoneDefinition> duplicate = List.of(bone("root", -1), bone("root", 0));
        assertThrows(IllegalArgumentException.class, () -> new RigDefinition("test:duplicate", "Duplicate", duplicate));
    }

    private static BoneDefinition bone(String id, int parent) {
        return new BoneDefinition(id, id, parent, BoneTransform.identity(), BoneCapabilities.rotationOnly());
    }
}
