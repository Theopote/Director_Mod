package com.director.actor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorEyeHeightTest {
    @Test void standingHeightsMatchVanillaBeforeScale() {
        assertEquals(1.62f, ActorEyeHeight.standing(null));
        assertEquals(1.62f, ActorEyeHeight.standing(ActorType.PLAYER));
        assertEquals(1.62f, ActorEyeHeight.standing(ActorType.VILLAGER));
        assertEquals(1.74f, ActorEyeHeight.standing(ActorType.ZOMBIE));
        assertEquals(1.74f, ActorEyeHeight.standing(ActorType.SKELETON));
        assertEquals(1.3f, ActorEyeHeight.standing(ActorType.COW));
        assertEquals(1.235f, ActorEyeHeight.standing(ActorType.SHEEP));
        assertEquals(1.7775f, ActorEyeHeight.standing(ActorType.CUSTOM));
    }

    @Test void nonPositiveScaleUsesOne() {
        assertEquals(2.6f, ActorEyeHeight.scaled(ActorType.COW, 2f), 1e-5f);
        assertEquals(1.3f, ActorEyeHeight.scaled(ActorType.COW, 0f), 1e-5f);
        assertEquals(1.3f, ActorEyeHeight.scaled(ActorType.COW, Float.NaN), 1e-5f);
    }
}
