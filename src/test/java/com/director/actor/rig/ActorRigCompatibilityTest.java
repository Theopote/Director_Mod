package com.director.actor.rig;

import com.director.actor.ActorType;
import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRigCompatibilityTest {
    @Test void everyTypeHasExplicitCompatibility() {
        for (var type : ActorType.values()) {
            boolean humanoid = type == ActorType.PLAYER || type == ActorType.ZOMBIE || type == ActorType.SKELETON;
            assertEquals(humanoid, ActorRigCompatibility.accepts(type, HumanoidRig.DEFINITION.id()), type.name());
            assertFalse(ActorRigCompatibility.accepts(type, "unknown:rig"));
            assertFalse(ActorRigCompatibility.explanation(type).isBlank());
        }
        assertFalse(ActorRigCompatibility.accepts(null, HumanoidRig.DEFINITION.id()));
    }
    @Test void distinctFamiliesDoNotDependOnProceduralProfileFallback() {
        assertEquals(ActorRigCompatibility.Family.VILLAGER, ActorRigCompatibility.family(ActorType.VILLAGER));
        assertEquals(ActorRigCompatibility.Family.QUADRUPED, ActorRigCompatibility.family(ActorType.COW));
        assertEquals(ActorRigCompatibility.Family.CUSTOM, ActorRigCompatibility.family(ActorType.CUSTOM));
    }
}
