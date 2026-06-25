package com.director.ui.camera.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.DirectorServices;
import com.director.timeline.model.camera.path.OrbitPathClip;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 将 Gizmo 平移应用到围绕路径的 Center（LookAt 与 Center 绑定时一并移动）。
 */
final class OrbitCenterTransformApplier extends TransformApplier {

    private final CameraPathEditController controller;

    OrbitCenterTransformApplier(CameraPathEditController controller) {
        super(DirectorServices.get().selection());
        this.controller = controller;
    }

    @Override
    public void applyMove(Vec3d delta) {
        OrbitPathClip clip = controller.getSelectedOrbitClip();
        if (clip == null || delta == null || delta.lengthSquared() < 1.0e-12) {
            return;
        }

        Vec3d oldCenter = clip.getCenter();
        Vec3d newCenter = oldCenter.add(delta);
        clip.setCenter(newCenter);

        Vec3d lookAt = clip.getLookAt();
        if (CameraPathEditController.isLookAtSameAsCenter(oldCenter, lookAt)) {
            clip.setLookAt(newCenter);
        }

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
