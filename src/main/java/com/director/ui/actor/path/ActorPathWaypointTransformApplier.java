package com.director.ui.actor.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.DirectorServices;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class ActorPathWaypointTransformApplier extends TransformApplier {

    private final ActorPathController controller;
    private boolean accepted;

    ActorPathWaypointTransformApplier(ActorPathController controller) {
        super(DirectorServices.get().selection());
        this.controller = controller;
    }

    @Override public void resetAccumulators() {
        super.resetAccumulators();
        controller.finishAnchorDrag();
        accepted = false;
    }

    @Override public void beginDragSession() { accepted = controller.beginAnchorDrag(); }

    // Path anchors only translate. Never inherit block-selection scale/rotation writes.
    @Override public void applyResize(com.director.client.gizmo.Axis axis, double delta) {}
    @Override public void applyResize(com.director.client.gizmo.Axis axis, boolean max, double delta) {}
    @Override public void applyResize(Vec3d delta) {}
    @Override public void applyResizeFromCorner(Vec3d delta, net.minecraft.util.math.Vec3i sign) {}
    @Override public void applyRotation(double angle, com.director.client.gizmo.Axis axis) {}

    @Override
    public void applyMove(Vec3d delta) {
        if (!accepted || !controller.isAnchorDragCurrent() || delta == null || delta.lengthSquared() < 1.0e-12) {
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
        if (!controller.setAnchorPosition(index, next)) return;

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
