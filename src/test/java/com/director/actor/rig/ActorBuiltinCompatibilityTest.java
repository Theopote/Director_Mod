package com.director.actor.rig;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import com.director.actor.animation.AnimationDataSampler;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.rig.skeleton.QuadrupedRig;
import com.director.actor.rig.skeleton.VillagerRig;
import com.director.ui.actor.pose.ActorPresetValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorBuiltinCompatibilityTest {
    @Test
    void villagerAcceptsWalkBuiltinAndSamplesSharedLegBones() {
        var rig = VillagerRig.DEFINITION;
        assertTrue(ActorRigCompatibility.acceptsBuiltin(rig, "walk"));
        var pose = AnimationDataSampler.sampleRig("walk", rig, .25f, true, 1f);
        assertNotNull(pose);
        assertTrue(pose.isAuthored(rig.requireBoneIndex("left_leg")));
        assertTrue(pose.isAuthored(rig.requireBoneIndex("right_leg")));
    }

    @Test
    void quadrupedRejectsWalkWhenNoSharedBones() {
        assertTrue(AnimationLibrary.getInstance().get("walk").isPresent());
        // walk controls left_arm/right_arm/left_leg/right_leg — none exist on quadruped
        org.junit.jupiter.api.Assertions.assertFalse(
                ActorRigCompatibility.acceptsBuiltin(QuadrupedRig.DEFINITION, "walk"));
    }

    @Test
    void presetValidationAllowsVillagerWalk() {
        var actor = new Actor("Villager", ActorType.VILLAGER);
        assertDoesNotThrow(() -> ActorPresetValidation.requireReference(actor,
                com.director.actor.animation.asset.AnimationAssetLibrary.getInstance(), "walk", null));
    }
}
