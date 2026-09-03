package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorBoneSelectionControllerTest {
    private final ActorBoneSelectionController controller = ActorBoneSelectionController.getInstance();

    @AfterEach
    void cleanUp() { controller.deactivate(); }

    @Test
    void activatesWithBodyAndRejectsUnknownBones() {
        UUID actorId = UUID.randomUUID();
        controller.activate(actorId);

        assertTrue(controller.isActive());
        assertEquals(HumanoidRig.BODY, controller.getSelectedBoneId());
        assertFalse(controller.selectBone("missing"));
        assertEquals(HumanoidRig.BODY, controller.getSelectedBoneId());
    }

    @Test
    void synchronizingActorResetsBoneContext() {
        controller.activate(UUID.randomUUID());
        assertTrue(controller.selectBone(HumanoidRig.LEFT_ARM));

        UUID replacement = UUID.randomUUID();
        controller.synchronizeActor(replacement);

        assertEquals(replacement, controller.getActorId());
        assertEquals(HumanoidRig.BODY, controller.getSelectedBoneId());
        assertNull(controller.getHoveredBoneId());
    }
}
