package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigDefinition;

import java.util.UUID;

/** Owns bone-selection state while the Actor Pose tool is active. */
public final class ActorBoneSelectionController {
    private static final ActorBoneSelectionController INSTANCE = new ActorBoneSelectionController();

    private long revision;
    public long getRevision() { return revision; }
    private void changed(boolean oldActive,UUID oldActor,String oldBone,java.util.List<String> oldBones) {
        if(oldActive!=active || !java.util.Objects.equals(oldActor,actorId) || !java.util.Objects.equals(oldBone,selectedBoneId)
                || !oldBones.equals(getSelectedBoneIds())) revision++;
    }
    private boolean active;
    private UUID actorId;
    private String selectedBoneId;
    private final java.util.LinkedHashSet<String> selectedBones = new java.util.LinkedHashSet<>();
    private String hoveredBoneId;

    private ActorBoneSelectionController() {}

    public static ActorBoneSelectionController getInstance() { return INSTANCE; }

    /** Rig currently bound to {@code actorId}, or the built-in humanoid rig as a safe fallback. */
    private RigDefinition rigFor(UUID actorId) {
        if (actorId == null) return HumanoidRig.DEFINITION;
        return com.director.actor.ActorRegistryManager.getInstance().getActor(actorId)
                .flatMap(com.director.actor.rig.ActorRigCompatibility::resolveRig)
                .orElse(HumanoidRig.DEFINITION);
    }

    private String defaultBone(UUID actorId) {
        RigDefinition rig = rigFor(actorId);
        return rig.findBoneIndex(HumanoidRig.BODY) >= 0 ? HumanoidRig.BODY : rig.bone(0).id();
    }

    public void activate(UUID actorId) {
        boolean oldActive=active; UUID oldActor=this.actorId; String oldBone=selectedBoneId;
        var oldBones=getSelectedBoneIds();
        try {
            active = actorId != null;
            this.actorId = actorId;
            selectedBoneId = active ? defaultBone(actorId) : null;
            resetSelection();
            hoveredBoneId = null;
        } finally { changed(oldActive,oldActor,oldBone,oldBones); }
    }

    public void synchronizeActor(UUID actorId) {
        boolean oldActive=active; UUID oldActor=this.actorId; String oldBone=selectedBoneId;
        var oldBones=getSelectedBoneIds();
        try {
            if (!active || java.util.Objects.equals(this.actorId, actorId)) return;
            this.actorId = actorId;
            selectedBoneId = actorId == null ? null : defaultBone(actorId);
            resetSelection();
            hoveredBoneId = null;
        } finally { changed(oldActive,oldActor,oldBone,oldBones); }
    }

    public void deactivate() {
        boolean oldActive=active; UUID oldActor=this.actorId; String oldBone=selectedBoneId;
        var oldBones=getSelectedBoneIds();
        try {
            active = false;
            actorId = null;
            selectedBoneId = null;
            selectedBones.clear();
            hoveredBoneId = null;
        } finally { changed(oldActive,oldActor,oldBone,oldBones); }
    }

    public boolean selectBone(String boneId) {
        return selectBone(boneId, false);
    }

    /** Ctrl toggles membership; the most recently selected bone is the active Gizmo target. */
    public boolean selectBone(String boneId, boolean additive) {
        boolean oldActive=active; UUID oldActor=this.actorId; String oldBone=selectedBoneId;
        var oldBones=getSelectedBoneIds();
        try {
            if (!active || actorId == null || rigFor(actorId).findBoneIndex(boneId) < 0) return false;
            if (!additive) selectedBones.clear();
            if (additive && selectedBones.remove(boneId)) {
                if (boneId.equals(selectedBoneId))
                    selectedBoneId = selectedBones.isEmpty() ? null : selectedBones.getLast();
            } else {
                selectedBones.add(boneId);
                selectedBoneId = boneId;
            }
            return true;
        } finally { changed(oldActive,oldActor,oldBone,oldBones); }
    }

    private void resetSelection() {
        selectedBones.clear();
        if (selectedBoneId != null) selectedBones.add(selectedBoneId);
    }

    public java.util.List<String> getSelectedBoneIds() { return java.util.List.copyOf(selectedBones); }
    public boolean isBoneSelected(String boneId) { return selectedBones.contains(boneId); }

    public void setHoveredBone(String boneId) {
        hoveredBoneId = active && rigFor(actorId).findBoneIndex(boneId) >= 0 ? boneId : null;
    }

    public boolean isActive() { return active; }
    public UUID getActorId() { return actorId; }
    public String getSelectedBoneId() { return selectedBoneId; }
    public String getHoveredBoneId() { return hoveredBoneId; }
}
