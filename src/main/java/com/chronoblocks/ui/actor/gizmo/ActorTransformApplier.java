package com.chronoblocks.ui.actor.gizmo;

import com.chronoblocks.client.gizmo.logic.TransformApplier;
import com.chronoblocks.core.selection.SelectionManager;
import net.minecraft.util.math.Vec3d;

/**
 * ActorTransformApplier v1.0
 *
 * <p>自定义 TransformApplier，用于将 Gizmo 拖动事件转发到 ActorGizmoController
 * <p>这样 ActorGizmoController 可以实时更新 EntityTransformClip
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ActorTransformApplier extends TransformApplier {

    private final ActorGizmoController actorGizmoController;
    private Vec3d lastDragDelta = Vec3d.ZERO;
    private boolean isDragging = false;

    public ActorTransformApplier(SelectionManager selectionManager, ActorGizmoController actorGizmoController) {
        super(selectionManager);
        this.actorGizmoController = actorGizmoController;
    }

    @Override
    public void applyMove(Vec3d delta) {
        // 如果正在编辑 Actor，转发到 ActorGizmoController
        if (actorGizmoController != null && actorGizmoController.isEditing()) {
            // 计算增量（相对于上次的增量）
            Vec3d actualDelta = delta.subtract(lastDragDelta);
            lastDragDelta = delta;

            if (!isDragging) {
                // 第一次调用，开始拖动
                actorGizmoController.handleGizmoDragStart();
                isDragging = true;
            }

            // 更新拖动
            actorGizmoController.handleGizmoDragUpdate(actualDelta);
        } else {
            // 否则使用默认行为（移动选区）
            super.applyMove(delta);
        }
    }

    @Override
    public void resetAccumulators() {
        super.resetAccumulators();
        lastDragDelta = Vec3d.ZERO;
        if (isDragging) {
            // 结束拖动
            if (actorGizmoController != null) {
                actorGizmoController.handleGizmoDragEnd();
            }
            isDragging = false;
        }
    }
}

