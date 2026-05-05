package com.director.ui.camera.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.selection.SelectionManager;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

final class CameraPathKeyframeTransformApplier extends TransformApplier {
    private final CameraPathEditController controller;

    CameraPathKeyframeTransformApplier(CameraPathEditController controller) {
        super(SelectionManager.get());
        this.controller = controller;
    }

    @Override
    public void applyMove(Vec3d delta) {
        CameraKeyframe keyframe = controller.getSelectedKeyframe();
        if (keyframe == null || delta == null || delta.lengthSquared() < 1.0e-12) {
            return;
        }

        Vector3f pos = keyframe.getPosition();
        keyframe.setPosition(new Vector3f(
                (float) (pos.x + delta.x),
                (float) (pos.y + delta.y),
                (float) (pos.z + delta.z)
        ));

        Box currentBox = GizmoManager.get().getTargetBox();
        Vec3d currentPivot = GizmoManager.get().getPivot();
        if (currentBox != null) {
            GizmoManager.get().setTargetBox(currentBox.offset(delta));
        }
        if (currentPivot != null) {
            GizmoManager.get().setPivot(currentPivot.add(delta));
        }

        // 拖拽中只做轻量路径刷新，避免每帧全量重建导致卡顿。
        TimelineCameraController.getInstance().onKeyframePreviewMoved();
    }
}
