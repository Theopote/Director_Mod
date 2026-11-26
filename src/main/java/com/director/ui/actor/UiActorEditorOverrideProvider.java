package com.director.ui.actor;

import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.runtime.ActorAnimationDraftView;
import com.director.actor.runtime.ActorEditorOverrideProvider;
import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.actor.lookat.MarkerDragController;
import com.director.ui.actor.pose.ActorBoneGizmoController;
import com.director.ui.actor.pose.ActorBonePropertiesPanel;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.UUID;

/** Bridges ui.actor singletons into the runtime override port. */
public final class UiActorEditorOverrideProvider implements ActorEditorOverrideProvider {
    @Override
    public boolean isDraggingActor(UUID actorId) {
        var gizmo = ActorGizmoController.getInstance();
        return gizmo.isDragging() && actorId != null && actorId.equals(gizmo.getTargetActorId());
    }

    @Override
    public boolean isDraggingBone(UUID actorId) {
        var gizmo = ActorBoneGizmoController.getInstance();
        return gizmo.isDragging() && actorId != null && actorId.equals(gizmo.getActorId());
    }

    @Override
    public boolean isMarkerDragging() {
        return MarkerDragController.getInstance().isDragging();
    }

    @Override
    public UUID gizmoTargetActorId() {
        return ActorGizmoController.getInstance().getTargetActorId();
    }

    @Override
    public UUID boneGizmoActorId() {
        return ActorBoneGizmoController.getInstance().getActorId();
    }

    @Override
    public Vector3f previewScale(UUID actorId) {
        return ActorGizmoController.getInstance().previewScale(actorId);
    }

    @Override
    public ActorAnimationDraftView boneDraft(UUID actorId, float timelineTime) {
        return ActorBoneGizmoController.getInstance().previewAnimationDraft(actorId, timelineTime);
    }

    @Override
    public RigPose previewAuthoredPose(UUID actorId, String animationId, RigPose sampled) {
        return ActorBonePropertiesPanel.previewAuthoredPose(actorId, animationId, sampled);
    }

    @Override
    public Vec3d previewLookAtPoint(UUID actorId) {
        return MarkerDragController.getInstance().previewPoint(actorId, null);
    }
}
