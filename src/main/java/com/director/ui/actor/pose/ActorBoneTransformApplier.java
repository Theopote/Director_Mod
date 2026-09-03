package com.director.ui.actor.pose;

import com.director.client.gizmo.Axis;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.selection.SelectionManager;

/** Forwards rotation-ring gestures into an Actor pose editing transaction. */
final class ActorBoneTransformApplier extends TransformApplier {
    private final ActorBoneGizmoController controller;
    private net.minecraft.util.math.Vec3d accumulatedMove = net.minecraft.util.math.Vec3d.ZERO;

    ActorBoneTransformApplier(SelectionManager selectionManager, ActorBoneGizmoController controller) {
        super(selectionManager);
        this.controller = controller;
    }

    @Override public void beginDragSession() { accumulatedMove = net.minecraft.util.math.Vec3d.ZERO; controller.beginTransformGesture(); }
    @Override public void applyRotation(double angle, Axis axis) { controller.updateRotationGesture(axis, (float) angle); }
    @Override public void endDragSession() { controller.endRotationGesture(); }
    @Override public void applyMove(net.minecraft.util.math.Vec3d delta) {
        accumulatedMove = accumulatedMove.add(delta);
        controller.updateTranslationGesture(accumulatedMove);
    }
    @Override public void applyResize(Axis axis, boolean isMaxFace, double delta) { }
}
