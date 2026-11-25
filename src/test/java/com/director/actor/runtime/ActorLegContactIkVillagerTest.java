package com.director.actor.runtime;

import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.VillagerRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorLegContactIkVillagerTest {
    @Test
    void singleSegmentLegSolveMovesSoleTowardTarget() {
        var pose = new RigPose(VillagerRig.DEFINITION);
        var leg = VillagerRig.LEFT_LEG;
        var target = ActorRouteFootContact.solePosition(pose, leg).add(0f, .05f, .1f);
        float before = ActorRouteFootContact.solePosition(pose, leg).distance(target);
        assertDoesNotThrow(() -> ActorLegContactIk.solve(pose, leg, target));
        float after = ActorRouteFootContact.solePosition(pose, leg).distance(target);
        assertTrue(after <= before + 1e-4f);
        assertTrue(ActorRouteFootContact.solePosition(pose, leg).isFinite());
    }
}
