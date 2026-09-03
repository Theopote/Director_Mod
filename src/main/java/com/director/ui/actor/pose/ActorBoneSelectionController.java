package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.HumanoidRig;

import java.util.UUID;

/** Owns bone-selection state while the Actor Pose tool is active. */
public final class ActorBoneSelectionController {
    private static final ActorBoneSelectionController INSTANCE = new ActorBoneSelectionController();

    private boolean active;
    private UUID actorId;
    private String selectedBoneId;
    private String hoveredBoneId;

    private ActorBoneSelectionController() {}

    public static ActorBoneSelectionController getInstance() { return INSTANCE; }

    public void activate(UUID actorId) {
        active = actorId != null;
        this.actorId = actorId;
        selectedBoneId = active ? HumanoidRig.BODY : null;
        hoveredBoneId = null;
    }

    public void synchronizeActor(UUID actorId) {
        if (!active || java.util.Objects.equals(this.actorId, actorId)) return;
        this.actorId = actorId;
        selectedBoneId = actorId == null ? null : HumanoidRig.BODY;
        hoveredBoneId = null;
    }

    public void deactivate() {
        active = false;
        actorId = null;
        selectedBoneId = null;
        hoveredBoneId = null;
    }

    public boolean selectBone(String boneId) {
        if (!active || actorId == null || HumanoidRig.DEFINITION.findBoneIndex(boneId) < 0) return false;
        selectedBoneId = boneId;
        return true;
    }

    public void setHoveredBone(String boneId) {
        hoveredBoneId = active && HumanoidRig.DEFINITION.findBoneIndex(boneId) >= 0 ? boneId : null;
    }

    public boolean isActive() { return active; }
    public UUID getActorId() { return actorId; }
    public String getSelectedBoneId() { return selectedBoneId; }
    public String getHoveredBoneId() { return hoveredBoneId; }
}
