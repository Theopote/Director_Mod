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

    @Test
    void ctrlTogglePreservesOtherPartsAndMaintainsActiveBone() {
        controller.activate(UUID.randomUUID());
        controller.selectBone(HumanoidRig.LEFT_ARM);
        controller.selectBone(HumanoidRig.HEAD, true);
        var snapshot = controller.getSelectedBoneIds();
        assertEquals(java.util.List.of(HumanoidRig.LEFT_ARM, HumanoidRig.HEAD), snapshot);
        assertEquals(HumanoidRig.HEAD, controller.getSelectedBoneId());
        controller.selectBone(HumanoidRig.HEAD, true);
        assertEquals(HumanoidRig.LEFT_ARM, controller.getSelectedBoneId());
        assertEquals(2, snapshot.size());
        controller.selectBone(HumanoidRig.LEFT_ARM, true);
        assertTrue(controller.getSelectedBoneIds().isEmpty());
        assertNull(controller.getSelectedBoneId());
    }

    @Test
    void switchingActorClearsMultiSelection() {
        controller.activate(UUID.randomUUID());
        controller.selectBone(HumanoidRig.HEAD, true);
        controller.synchronizeActor(UUID.randomUUID());
        assertEquals(java.util.List.of(HumanoidRig.BODY), controller.getSelectedBoneIds());
        controller.deactivate();
        assertTrue(controller.getSelectedBoneIds().isEmpty());
    }
}
