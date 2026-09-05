package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.HumanoidRig;

import java.util.UUID;

/** Owns bone-selection state while the Actor Pose tool is active. */
public final class ActorBoneSelectionController {
    private static final ActorBoneSelectionController INSTANCE = new ActorBoneSelectionController();

    private boolean active;
    private UUID actorId;
    private String selectedBoneId;
    private final java.util.LinkedHashSet<String> selectedBones = new java.util.LinkedHashSet<>();
    private String hoveredBoneId;

    private ActorBoneSelectionController() {}

    public static ActorBoneSelectionController getInstance() { return INSTANCE; }

    public void activate(UUID actorId) {
        active = actorId != null;
        this.actorId = actorId;
        selectedBoneId = active ? HumanoidRig.BODY : null;
        resetSelection();
        hoveredBoneId = null;
    }

    public void synchronizeActor(UUID actorId) {
        if (!active || java.util.Objects.equals(this.actorId, actorId)) return;
        this.actorId = actorId;
        selectedBoneId = actorId == null ? null : HumanoidRig.BODY;
        resetSelection();
        hoveredBoneId = null;
    }

    public void deactivate() {
        active = false;
        actorId = null;
        selectedBoneId = null;
        selectedBones.clear();
        hoveredBoneId = null;
    }

    public boolean selectBone(String boneId) {
        return selectBone(boneId, false);
    }

    /** Ctrl toggles membership; the most recently selected bone is the active Gizmo target. */
    public boolean selectBone(String boneId, boolean additive) {
        if (!active || actorId == null || HumanoidRig.DEFINITION.findBoneIndex(boneId) < 0) return false;
        if (!additive) selectedBones.clear();
        if (additive && selectedBones.remove(boneId)) {
            if (boneId.equals(selectedBoneId))
                selectedBoneId = selectedBones.isEmpty() ? null : selectedBones.getLast();
        } else {
            selectedBones.add(boneId);
            selectedBoneId = boneId;
        }
        return true;
    }

    private void resetSelection() {
        selectedBones.clear();
        if (selectedBoneId != null) selectedBones.add(selectedBoneId);
    }

    public java.util.List<String> getSelectedBoneIds() { return java.util.List.copyOf(selectedBones); }
    public boolean isBoneSelected(String boneId) { return selectedBones.contains(boneId); }

    public void setHoveredBone(String boneId) {
        hoveredBoneId = active && HumanoidRig.DEFINITION.findBoneIndex(boneId) >= 0 ? boneId : null;
    }

    public boolean isActive() { return active; }
    public UUID getActorId() { return actorId; }
    public String getSelectedBoneId() { return selectedBoneId; }
    public String getHoveredBoneId() { return hoveredBoneId; }
}
