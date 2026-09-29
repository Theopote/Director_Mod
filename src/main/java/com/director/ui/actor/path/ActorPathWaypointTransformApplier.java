package com.director.ui.actor.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.DirectorServices;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class ActorPathWaypointTransformApplier extends TransformApplier {

    private final ActorPathController controller;

    ActorPathWaypointTransformApplier(ActorPathController controller) {
        super(DirectorServices.get().selection());
        this.controller = controller;
    }

    @Override public void resetAccumulators() {
        super.resetAccumulators();
        controller.finishAnchorDrag();
    }

    @Override
    public void applyMove(Vec3d delta) {
        if (delta == null || delta.lengthSquared() < 1.0e-12) {
            return;
        }
        int index = controller.getSelectedAnchorIndex();
        if (index < 0) {
            return;
        }

        Vec3d current = controller.getAnchorPosition(index);
        if (current == null) {
            return;
        }

        Vec3d next = current.add(delta);
        controller.setAnchorPosition(index, next);

        Box currentBox = GizmoManager.get().getTargetBox();
        Vec3d currentPivot = GizmoManager.get().getPivot();
        if (currentBox != null) {
            GizmoManager.get().setTargetBox(currentBox.offset(delta));
        }
        if (currentPivot != null) {
            GizmoManager.get().setPivot(currentPivot.add(delta));
        }
    }
}
