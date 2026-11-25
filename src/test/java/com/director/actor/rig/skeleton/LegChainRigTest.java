package com.director.actor.rig.skeleton;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegChainRigTest {
    @Test
    void humanoidAndArticulatedDeclareTwoLegs() {
        assertEquals(2, HumanoidRig.DEFINITION.legs().size());
        assertFalse(HumanoidRig.DEFINITION.legs().getFirst().hasKnee());
        assertEquals(2, ArticulatedPlayerRig.DEFINITION.legs().size());
        assertTrue(ArticulatedPlayerRig.DEFINITION.legs().getFirst().hasKnee());
        assertEquals(ArticulatedPlayerRig.LEFT_SHIN, ArticulatedPlayerRig.DEFINITION.legs().getFirst().lowerBone());
    }

    @Test
    void villagerAndQuadrupedDeclareSingleSegmentLegs() {
        assertEquals(2, VillagerRig.DEFINITION.legs().size());
        assertFalse(VillagerRig.DEFINITION.legs().getFirst().hasKnee());
        assertEquals(4, QuadrupedRig.DEFINITION.legs().size());
        assertTrue(QuadrupedRig.DEFINITION.legs().stream().noneMatch(LegChain::hasKnee));
    }
}
