package com.director.ui.actor.gizmo;

import com.director.client.gizmo.Axis;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.selection.SelectionManager;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;

/**
 * ActorTransformApplier v1.0
 *
 * <p>自定义 TransformApplier，用于将 Gizmo 拖动事件转发到 ActorGizmoController
 * <p>这样 ActorGizmoController 可以实时更新 EntityTransformClip
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorTransformApplier extends TransformApplier {

    private final ActorGizmoController actorGizmoController;
    private Vec3d accumulatedMoveDelta = Vec3d.ZERO;
    private Vec3d accumulatedScaleDelta = Vec3d.ZERO;
    private Vec3d accumulatedRotateDelta = Vec3d.ZERO;
    private boolean isDragging = false;
    private boolean gestureAttempted;

    public ActorTransformApplier(SelectionManager selectionManager, ActorGizmoController actorGizmoController) {
        super(selectionManager);
        this.actorGizmoController = actorGizmoController;
    }

    public void prepareDragSession() {
        if (actorGizmoController == null || gestureAttempted) {
            return;
        }
        gestureAttempted = true;
        actorGizmoController.handleGizmoDragStart();
        isDragging = actorGizmoController.isDragging();
        accumulatedMoveDelta = Vec3d.ZERO;
        accumulatedScaleDelta = Vec3d.ZERO;
        accumulatedRotateDelta = Vec3d.ZERO;
    }

    @Override
    public void applyMove(Vec3d delta) {
        if (delta.lengthSquared() == 0.0) return;
        // ⭐ 检查是否有目标 Actor（而不是检查 isEditing，因为 editingClip 是在拖动开始时才设置的）
        if (actorGizmoController != null && actorGizmoController.getTargetActorId() != null) {
            beginActorDragIfNeeded();
            if (!isDragging) {
                return;
            }
            accumulatedMoveDelta = accumulatedMoveDelta.add(delta);
            actorGizmoController.handleGizmoDragUpdate(accumulatedMoveDelta);
        } else {
            // An actor applier never writes block selections after its target is cleared.
            return;
        }
    }

    @Override
    public void applyResize(Axis axis, double delta) {
        applyResize(axis, true, delta);
    }

    @Override
    public void applyResize(Axis axis, boolean isMaxFace, double delta) {
        if (actorGizmoController != null && actorGizmoController.getTargetActorId() != null) {
            beginActorDragIfNeeded();
            if (!isDragging) {
                return;
            }
            accumulatedScaleDelta = accumulatedScaleDelta.add(axisDelta(axis, delta));
            actorGizmoController.handleGizmoDragUpdate(accumulatedScaleDelta);
        } else {
            return;
        }
    }

    @Override
    public void applyResize(Vec3d axisDelta) {
        if (actorGizmoController != null && actorGizmoController.getTargetActorId() != null) {
            beginActorDragIfNeeded();
            if (!isDragging) {
                return;
            }
            accumulatedScaleDelta = accumulatedScaleDelta.add(axisDelta);
            actorGizmoController.handleGizmoDragUpdate(accumulatedScaleDelta);
        } else {
            return;
        }
    }

    @Override
    public void applyResizeFromCorner(Vec3d delta, Vec3i cornerSign) {
        if (actorGizmoController != null && actorGizmoController.getTargetActorId() != null) {
            beginActorDragIfNeeded();
            if (!isDragging) {
                return;
            }
            accumulatedScaleDelta = accumulatedScaleDelta.add(delta);
            actorGizmoController.handleGizmoDragUpdate(accumulatedScaleDelta);
        } else {
            return;
        }
    }

    @Override
    public void applyRotation(double angle, Axis axis) {
        if (actorGizmoController != null && actorGizmoController.getTargetActorId() != null) {
            beginActorDragIfNeeded();
            if (!isDragging) {
                return;
            }
            Vec3d rotation = rotationDelta(axis, Math.toDegrees(angle));
            if (rotation.equals(accumulatedRotateDelta)) return;
            accumulatedRotateDelta = rotation;
            actorGizmoController.handleGizmoDragUpdate(accumulatedRotateDelta);
        } else {
            return;
        }
    }

    @Override
    public void resetAccumulators() {
        super.resetAccumulators();
        accumulatedMoveDelta = Vec3d.ZERO;
        accumulatedScaleDelta = Vec3d.ZERO;
        accumulatedRotateDelta = Vec3d.ZERO;
        if (isDragging) {
            // 结束拖动
            if (actorGizmoController != null) {
                actorGizmoController.handleGizmoDragEnd();
            }
            isDragging = false;
        }
        gestureAttempted = false;
    }

    private void beginActorDragIfNeeded() {
        if (!gestureAttempted) prepareDragSession();
    }

    private static Vec3d axisDelta(Axis axis, double delta) {
        return switch (axis) {
            case X -> new Vec3d(delta, 0.0, 0.0);
            case Y -> new Vec3d(0.0, delta, 0.0);
            case Z -> new Vec3d(0.0, 0.0, delta);
        };
    }

    private static Vec3d rotationDelta(Axis axis, double degrees) {
        return switch (axis) {
            case X -> new Vec3d(0.0, degrees, 0.0);
            case Y -> new Vec3d(degrees, 0.0, 0.0);
            case Z -> new Vec3d(0.0, 0.0, degrees);
        };
    }
}

