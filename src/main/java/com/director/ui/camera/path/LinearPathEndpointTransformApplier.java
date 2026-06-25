package com.director.ui.camera.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.DirectorServices;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.camera.path.CranePathClip;
import com.director.timeline.model.camera.path.DollyPathClip;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 将 Gizmo 平移应用到推拉/升降路径的端点，从而调整直线路径走向。
 */
final class LinearPathEndpointTransformApplier extends TransformApplier {

    private final CameraPathEditController controller;

    LinearPathEndpointTransformApplier(CameraPathEditController controller) {
        super(DirectorServices.get().selection());
        this.controller = controller;
    }

    @Override
    public void applyMove(Vec3d delta) {
        CameraPathClip clip = controller.getSelectedLinearClip();
        CameraPathEditController.LinearEndpoint endpoint = controller.getSelectedLinearEndpoint();
        if (clip == null || endpoint == null || delta == null || delta.lengthSquared() < 1e-12) {
            return;
        }

        switch (clip) {
            case DollyPathClip dolly -> {
                if (endpoint == CameraPathEditController.LinearEndpoint.START) {
                    dolly.applyStartEndpointDelta(delta);
                } else {
                    dolly.applyEndEndpointDelta(delta);
                }
            }
            case CranePathClip crane -> {
                if (endpoint == CameraPathEditController.LinearEndpoint.START) {
                    crane.applyStartEndpointDelta(delta);
                } else {
                    crane.applyEndEndpointDelta(delta);
                }
            }
            default -> {
                return;
            }
        }

        Vec3d pivot = controller.getLinearEndpointPosition(clip, endpoint);
        if (pivot == null) {
            return;
        }

        Box currentBox = GizmoManager.get().getTargetBox();
        Vec3d currentPivot = GizmoManager.get().getPivot();
        if (currentBox != null) {
            GizmoManager.get().setTargetBox(currentBox.offset(delta));
        }
        if (currentPivot != null) {
            GizmoManager.get().setPivot(currentPivot.add(delta));
        } else {
            GizmoManager.get().setPivot(pivot);
        }
    }
}
