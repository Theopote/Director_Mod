package com.director.actor.rig;

import com.director.actor.action.ActorActionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorActionStrengthTest {
    @Test void quadrupedRunHonorsStrength() {
        var rig = new QuadrupedRigProfile();
        var zero = rig.buildPose(ActorActionType.RUN, 0f, .3f).pose;
        assertEquals(0f, zero.leftLegPitch, 1e-6f);
        assertEquals(0f, zero.rightLegPitch, 1e-6f);
        assertNotEquals(0f, rig.buildPose(ActorActionType.RUN, 1f, .3f).pose.leftLegPitch);
    }
    @Test void danceHonorsStrengthAndTempo() {
        var rig = new HumanoidRigProfile();
        var zero = rig.buildPose(ActorActionType.DANCE, 0f, .3f).pose;
        assertEquals(0f, zero.bodyYaw, 1e-6f);
        assertEquals(0f, zero.leftArmPitch, 1e-6f);
        assertEquals(0f, zero.leftLegPitch, 1e-6f);
        float first = rig.buildPose(ActorActionType.DANCE, 1f, .3f).pose.leftArmPitch;
        rig.getStyle().tempo = 2f;
        assertNotEquals(first, rig.buildPose(ActorActionType.DANCE, 1f, .3f).pose.leftArmPitch);
    }
}
