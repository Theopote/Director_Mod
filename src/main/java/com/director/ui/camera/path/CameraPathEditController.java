package com.director.ui.camera.path;

import com.director.client.gizmo.GizmoInteractionController;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoType;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.CameraPathDebugRenderer;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import com.director.ui.input.InputFocusManager;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CameraPathEditController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPathEditController.class);
    private static final CameraPathEditController INSTANCE = new CameraPathEditController();

    private static final double ANCHOR_PICK_BASE_RADIUS = 0.35;
    private static final double ANCHOR_PICK_DISTANCE_SCALE = 0.006;
    private static final double ANCHOR_BOX_HALF_SIZE = 0.12;

    private boolean active = false;
    private boolean lastLeftMouseDown = false;
    private boolean previousPathRendererEnabled = false;
    private CameraKeyframe selectedKeyframe = null;

    private CameraPathEditController() {
    }

    public static CameraPathEditController getInstance() {
        return INSTANCE;
    }

    public void activate() {
        active = true;
        lastLeftMouseDown = false;
        previousPathRendererEnabled = CameraPathDebugRenderer.getInstance().isEnabled();
        CameraPathDebugRenderer.getInstance().setEnabled(true);
        selectedKeyframe = findSelectedCameraKeyframe();
        if (selectedKeyframe != null) {
            showGizmoFor(selectedKeyframe);
        }
        LOGGER.info("[CameraPathEdit] Activated");
    }

    public void deactivate() {
        active = false;
        lastLeftMouseDown = false;
        selectedKeyframe = null;
        TimelineCameraController.getInstance().stopKeyframePreview();
        CameraPathDebugRenderer.getInstance().setEnabled(previousPathRendererEnabled);
        if (GizmoManager.get().isActive()) {
            GizmoManager.get().deactivate();
        }
        LOGGER.info("[CameraPathEdit] Deactivated");
    }

    public boolean isActive() {
        return active;
    }

    CameraKeyframe getSelectedKeyframe() {
        return selectedKeyframe;
    }

    TimelineModel getTimelineModel() {
        return ContainerTimelineService.getInstance().getTimelineModel();
    }

    public void update() {
        if (!active) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.getWindow() == null) {
            return;
        }

        if (selectedKeyframe != null && !containsActiveTrackKeyframe(selectedKeyframe)) {
            clearSelectionAndGizmo();
        }

        if (selectedKeyframe != null && !GizmoManager.get().isDraggingHandle()) {
            syncGizmoToSelectedKeyframe();
        }

        if (isMouseOverDirectorUi()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            return;
        }

        boolean leftDown = isLeftMouseDown(client);
        if (leftDown && !lastLeftMouseDown && !GizmoManager.get().isHot()) {
            handleAnchorPick();
        }
        lastLeftMouseDown = leftDown;
    }

    private boolean isMouseOverDirectorUi() {
        return InputFocusManager.getInstance().isPanelHovered()
                || ImGui.isAnyItemHovered()
                || ImGui.isAnyItemActive();
    }

    private boolean isLeftMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void handleAnchorPick() {
        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
        if (ray == null) {
            return;
        }

        CameraKeyframe picked = pickNearestKeyframe(ray.origin(), ray.direction());
        if (picked == null) {
            clearSelectionAndGizmo();
            return;
        }

        selectedKeyframe = picked;
        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.selectItem(picked, false);
        }
        showGizmoFor(picked);
        TimelineCameraController.getInstance().previewKeyframeDirect(picked);
    }

    private CameraKeyframe pickNearestKeyframe(Vec3d rayOrigin, Vec3d rayDir) {
        List<CameraKeyframe> keyframes = getActiveCameraKeyframes();
        if (keyframes.isEmpty()) {
            return null;
        }

        CameraKeyframe best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (CameraKeyframe keyframe : keyframes) {
            Vec3d point = toVec(keyframe.getPosition());
            double t = point.subtract(rayOrigin).dotProduct(rayDir);
            if (t < 0) {
                continue;
            }
            Vec3d closest = rayOrigin.add(rayDir.multiply(t));
            double distance = closest.distanceTo(point);
            double threshold = ANCHOR_PICK_BASE_RADIUS + Math.min(0.75, rayOrigin.distanceTo(point) * ANCHOR_PICK_DISTANCE_SCALE);
            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance;
                best = keyframe;
            }
        }
        return best;
    }

    private void showGizmoFor(CameraKeyframe keyframe) {
        Vec3d pos = toVec(keyframe.getPosition());
        Box box = anchorBox(pos);
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pos);
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null) {
            controller.setTransformApplier(new CameraPathKeyframeTransformApplier(this));
        }
    }

    private void syncGizmoToSelectedKeyframe() {
        if (!GizmoManager.get().isActive()) {
            showGizmoFor(selectedKeyframe);
            return;
        }
        Vec3d pos = toVec(selectedKeyframe.getPosition());
        GizmoManager.get().setTargetBox(anchorBox(pos));
        GizmoManager.get().setPivot(pos);
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null && !(controller.getTransformApplier() instanceof CameraPathKeyframeTransformApplier)) {
            controller.setTransformApplier(new CameraPathKeyframeTransformApplier(this));
        }
    }

    private void clearSelectionAndGizmo() {
        selectedKeyframe = null;
        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.clearSelection();
        }
        TimelineCameraController.getInstance().stopKeyframePreview();
        if (GizmoManager.get().isActive()) {
            GizmoManager.get().deactivate();
        }
    }

    private CameraKeyframe findSelectedCameraKeyframe() {
        TimelineModel model = getTimelineModel();
        if (model == null) {
            return null;
        }
        for (Object item : model.getSelectedItems()) {
            if (item instanceof CameraKeyframe keyframe && containsActiveTrackKeyframe(keyframe)) {
                return keyframe;
            }
        }
        return null;
    }

    private boolean containsActiveTrackKeyframe(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return false;
        }
        TimelineTrack track = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return false;
        }
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable != null && keyframe.getId().equals(selectable.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<CameraKeyframe> getActiveCameraKeyframes() {
        TimelineTrack track = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return List.of();
        }
        List<CameraKeyframe> out = new ArrayList<>();
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof CameraKeyframe keyframe) {
                out.add(keyframe);
            }
        }
        out.sort(Comparator.comparing(CameraKeyframe::getTime));
        return out;
    }

    private Vec3d toVec(Vector3f v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    private Box anchorBox(Vec3d pos) {
        return new Box(
                pos.x - ANCHOR_BOX_HALF_SIZE,
                pos.y - ANCHOR_BOX_HALF_SIZE,
                pos.z - ANCHOR_BOX_HALF_SIZE,
                pos.x + ANCHOR_BOX_HALF_SIZE,
                pos.y + ANCHOR_BOX_HALF_SIZE,
                pos.z + ANCHOR_BOX_HALF_SIZE
        );
    }
}
