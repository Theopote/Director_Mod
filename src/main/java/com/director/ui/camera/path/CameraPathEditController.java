package com.director.ui.camera.path;

import com.director.client.gizmo.GizmoInteractionController;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoType;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.CameraPathDebugRenderer;
import com.director.mixin.accessor.GameRendererAccessor;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.model.camera.path.CustomPathClip;
import com.director.ui.timeline.playback.camera.CameraSample;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import com.director.ui.timeline.playback.camera.TimelineCameraPathManager;
import com.director.ui.input.InputFocusManager;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CameraPathEditController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPathEditController.class);
    private static final CameraPathEditController INSTANCE = new CameraPathEditController();

    private static final double ANCHOR_PICK_BASE_RADIUS = 0.35;
    private static final double ANCHOR_PICK_DISTANCE_SCALE = 0.006;
    private static final double CURVE_PICK_BASE_RADIUS = 0.28;
    private static final double CURVE_PICK_DISTANCE_SCALE = 0.004;
    private static final double ANCHOR_BOX_HALF_SIZE = 0.12;
    private static final double BOX_DRAG_THRESHOLD_PX = 5.0;
    private static final double DOUBLE_CLICK_MAX_INTERVAL_MS = 350.0;
    private static final double DOUBLE_CLICK_MAX_DISTANCE_PX = 16.0;
    private static final double GIZMO_SYNC_EPSILON_SQ = 1.0e-8;
    private static final int BOX_FILL_COLOR = 0x3345A9FF;
    private static final int BOX_BORDER_COLOR = 0x6645A9FF;

    private boolean active = false;
    private boolean lastLeftMouseDown = false;
    private boolean previousPathRendererEnabled = false;
    private CameraKeyframe selectedKeyframe = null;
    private Vec3d lastGizmoPosition = null;
    private boolean pendingWorldMouseDown = false;
    private boolean boxDragging = false;
    private boolean gizmoMouseCaptured = false;
    private boolean wasGizmoDragging = false;
    private boolean lastDeleteDown = false;
    private double dragStartX = 0.0;
    private double dragStartY = 0.0;
    private double dragCurrentX = 0.0;
    private double dragCurrentY = 0.0;
    private long lastClickTimeMs = 0L;
    private double lastClickX = 0.0;
    private double lastClickY = 0.0;

    private CameraPathEditController() {
    }

    public static CameraPathEditController getInstance() {
        return INSTANCE;
    }

    public void activate() {
        active = true;
        lastLeftMouseDown = false;
        pendingWorldMouseDown = false;
        boxDragging = false;
        gizmoMouseCaptured = false;
        wasGizmoDragging = false;
        lastDeleteDown = false;
        lastGizmoPosition = null;
        lastClickTimeMs = 0L;
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
        pendingWorldMouseDown = false;
        boxDragging = false;
        gizmoMouseCaptured = false;
        wasGizmoDragging = false;
        lastDeleteDown = false;
        lastGizmoPosition = null;
        lastClickTimeMs = 0L;
        selectedKeyframe = null;
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

        boolean leftDown = isLeftMouseDown(client);

        boolean draggingNow = GizmoManager.get().isDraggingHandle();

        if (selectedKeyframe != null && !draggingNow) {
            syncGizmoToSelectedKeyframe();
        }

        if (wasGizmoDragging && !draggingNow && selectedKeyframe != null) {
            TimelineCameraController.getInstance().onKeyframeChanged();
            TimelineModel model = getTimelineModel();
            if (model != null) {
                model.markDirty();
            }
        }
        wasGizmoDragging = draggingNow;

        processDeleteShortcut(client);

        if (leftDown && !lastLeftMouseDown && GizmoManager.get().isHot()) {
            gizmoMouseCaptured = true;
        }
        if (!leftDown) {
            gizmoMouseCaptured = false;
        }
        if (draggingNow || (leftDown && gizmoMouseCaptured)) {
            pendingWorldMouseDown = false;
            boxDragging = false;
            lastLeftMouseDown = true;
            return;
        }

        if (!pendingWorldMouseDown && isMouseOverDirectorUi()) {
            lastLeftMouseDown = leftDown;
            return;
        }

        if (leftDown && !lastLeftMouseDown) {
            pendingWorldMouseDown = true;
            boxDragging = false;
            dragStartX = client.mouse.getX();
            dragStartY = client.mouse.getY();
            dragCurrentX = dragStartX;
            dragCurrentY = dragStartY;
        } else if (leftDown && pendingWorldMouseDown) {
            dragCurrentX = client.mouse.getX();
            dragCurrentY = client.mouse.getY();
            double dx = client.mouse.getX() - dragStartX;
            double dy = client.mouse.getY() - dragStartY;
            if ((dx * dx + dy * dy) >= BOX_DRAG_THRESHOLD_PX * BOX_DRAG_THRESHOLD_PX) {
                boxDragging = true;
            }
        } else if (!leftDown && lastLeftMouseDown && pendingWorldMouseDown) {
            if (boxDragging) {
                handleBoxSelect(client, dragStartX, dragStartY, client.mouse.getX(), client.mouse.getY());
            } else {
                boolean doubleClick = isDoubleClick(client);
                if (doubleClick && handleDoubleClickInsert()) {
                    lastClickTimeMs = 0L;
                } else {
                    handleAnchorPick();
                    rememberClick(client);
                }
            }
            pendingWorldMouseDown = false;
            boxDragging = false;
        }
        lastLeftMouseDown = leftDown;
    }

    public void renderOverlay(float viewportX, float viewportY, float viewportW, float viewportH) {
        // Deprecated: screen-space overlays are now rendered in Minecraft HUD pipeline.
    }

    public void renderHudOverlay(DrawContext context) {
        if (!active || !pendingWorldMouseDown || !boxDragging) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int windowW = Math.max(1, client.getWindow().getWidth());
        int windowH = Math.max(1, client.getWindow().getHeight());
        int guiW = Math.max(1, client.getWindow().getScaledWidth());
        int guiH = Math.max(1, client.getWindow().getScaledHeight());

        double scaleX = guiW / (double) windowW;
        double scaleY = guiH / (double) windowH;

        int minX = (int) Math.floor(Math.min(dragStartX, dragCurrentX) * scaleX);
        int maxX = (int) Math.ceil(Math.max(dragStartX, dragCurrentX) * scaleX);
        int minY = (int) Math.floor(Math.min(dragStartY, dragCurrentY) * scaleY);
        int maxY = (int) Math.ceil(Math.max(dragStartY, dragCurrentY) * scaleY);

        minX = Math.max(0, Math.min(guiW, minX));
        maxX = Math.max(0, Math.min(guiW, maxX));
        minY = Math.max(0, Math.min(guiH, minY));
        maxY = Math.max(0, Math.min(guiH, maxY));

        if (maxX <= minX || maxY <= minY) {
            return;
        }

        context.fill(minX, minY, maxX, maxY, BOX_FILL_COLOR);
        drawRectOutline(context, minX, minY, maxX, maxY, BOX_BORDER_COLOR, 1);
    }

    private static void drawRectOutline(DrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        int t = Math.max(1, thickness);
        context.fill(x1, y1, x2, y1 + t, color);
        context.fill(x1, y2 - t, x2, y2, color);
        context.fill(x1, y1 + t, x1 + t, y2 - t, color);
        context.fill(x2 - t, y1 + t, x2, y2 - t, color);
    }

    public boolean isKeyframeSelected(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return false;
        }
        TimelineModel model = getTimelineModel();
        if (model == null) {
            return selectedKeyframe != null && keyframe.getId().equals(selectedKeyframe.getId());
        }
        for (Object item : model.getSelectedItems()) {
            if (item instanceof CameraKeyframe cameraKeyframe
                    && cameraKeyframe.getId().equals(keyframe.getId())
                    && containsActiveTrackKeyframe(cameraKeyframe)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverDirectorUi() {
        return InputFocusManager.getInstance().isPanelHovered()
                || ImGui.isAnyItemHovered()
                || ImGui.isAnyItemActive();
    }

    private boolean isLeftMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void processDeleteShortcut(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }

        boolean deleteDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
        if (deleteDown && !lastDeleteDown) {
            if (!(ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive())) {
                deleteSelectedKeyframes();
            }
        }
        lastDeleteDown = deleteDown;
    }

    private void deleteSelectedKeyframes() {
        TimelineTrack track = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (track == null || track.getType() != TrackType.CAMERA || track.isLocked()) {
            return;
        }

        Set<UUID> selectedIds = getSelectedCameraKeyframes().stream()
                .map(CameraKeyframe::getId)
                .collect(Collectors.toSet());
        if (selectedIds.isEmpty()) {
            return;
        }

        int removed = 0;
        for (TimelineSelectable selectable : new ArrayList<>(track.getAutomationKeyframes())) {
            if (selectable == null || selectable.getId() == null || !selectedIds.contains(selectable.getId())) {
                continue;
            }
            track.removeAutomationKeyframe(selectable.getId());
            removed++;
        }
        for (com.director.ui.timeline.model.camera.path.CameraPathClip pathClip : track.getCameraPathClips()) {
            if (!(pathClip instanceof CustomPathClip customPathClip)) {
                continue;
            }
            for (CameraKeyframe keyframe : new ArrayList<>(customPathClip.getKeyframes())) {
                if (keyframe != null && selectedIds.contains(keyframe.getId())
                        && customPathClip.removeKeyframe(keyframe.getId())) {
                    removed++;
                }
            }
        }

        if (removed <= 0) {
            return;
        }

        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.clearSelection();
            model.markDirty();
        }
        selectedKeyframe = null;
        lastGizmoPosition = null;
        if (GizmoManager.get().isActive()) {
            GizmoManager.get().deactivate();
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.info("[CameraPathEdit] Deleted {} selected camera keyframe(s)", removed);
    }

    private List<CameraKeyframe> getSelectedCameraKeyframes() {
        TimelineModel model = getTimelineModel();
        if (model == null) {
            return List.of();
        }
        List<CameraKeyframe> selected = new ArrayList<>();
        for (Object item : model.getSelectedItems()) {
            if (item instanceof CameraKeyframe keyframe && containsActiveTrackKeyframe(keyframe)) {
                selected.add(keyframe);
            }
        }
        return selected;
    }

    private void handleAnchorPick() {
        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
        if (ray == null) {
            return;
        }

        CameraKeyframe picked = pickNearestKeyframe(ray.origin(), ray.direction());
        if (picked == null) {
            picked = pickNearestKeyframeFromCurve(ray.origin(), ray.direction());
        }
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
    }

    private boolean isDoubleClick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        double dx = client.mouse.getX() - lastClickX;
        double dy = client.mouse.getY() - lastClickY;
        return lastClickTimeMs > 0L
                && now - lastClickTimeMs <= DOUBLE_CLICK_MAX_INTERVAL_MS
                && (dx * dx + dy * dy) <= DOUBLE_CLICK_MAX_DISTANCE_PX * DOUBLE_CLICK_MAX_DISTANCE_PX;
    }

    private void rememberClick(MinecraftClient client) {
        lastClickTimeMs = System.currentTimeMillis();
        lastClickX = client.mouse.getX();
        lastClickY = client.mouse.getY();
    }

    private boolean handleDoubleClickInsert() {
        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
        if (ray == null) {
            return false;
        }

        CurveHit hit = pickCurveHit(ray.origin(), ray.direction());
        if (hit == null) {
            return false;
        }

        TimelineTrack track = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return false;
        }

        CustomPathClip customPathClip = hit.pathClip;
        if (customPathClip == null) {
            return false;
        }

        TimelineCameraPathManager manager = new TimelineCameraPathManager();
        manager.setKeyframes(customPathClip.getKeyframes());
        CameraSample sample = manager.sample(hit.time).orElse(null);
        if (sample == null) {
            return false;
        }

        CameraKeyframe keyframe = getCameraKeyframe(hit, sample);

        customPathClip.addKeyframe(keyframe);
        TimelineCameraController.getInstance().onKeyframeChanged();

        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.selectItem(keyframe, false);
            model.markDirty();
        }

        selectedKeyframe = keyframe;
        showGizmoFor(keyframe);
        return true;
    }

    private static @NotNull CameraKeyframe getCameraKeyframe(CurveHit hit, CameraSample sample) {
        CameraKeyframe keyframe = new CameraKeyframe(String.format("Camera @ %.2fs", hit.time), hit.time);
        keyframe.setPosition(new Vector3f((float) sample.position().x, (float) sample.position().y, (float) sample.position().z));
        keyframe.setRotation(new Vector3f(sample.pitch(), sample.yaw(), sample.roll()));
        keyframe.setFov(sample.fov());
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());
        if (hit.previousKeyframe != null) {
            keyframe.setKeyType(hit.previousKeyframe.getKeyType());
            keyframe.setSegmentPathType(hit.previousKeyframe.getSegmentPathType());
            keyframe.setRotationInterpolation(hit.previousKeyframe.getRotationInterpolation());
            keyframe.setHandleMode(hit.previousKeyframe.getHandleMode());
        }
        return keyframe;
    }

    private void handleBoxSelect(MinecraftClient client, double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0, x1);
        double maxX = Math.max(x0, x1);
        double minY = Math.min(y0, y1);
        double maxY = Math.max(y0, y1);

        List<CameraKeyframe> selected = new ArrayList<>();
        for (CameraKeyframe keyframe : getActiveCameraKeyframes()) {
            ScreenPoint point = worldToScreen(client, toVec(keyframe.getPosition()));
            if (point != null && point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY) {
                selected.add(keyframe);
            }
        }

        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.clearSelection();
            for (int i = 0; i < selected.size(); i++) {
                model.selectItem(selected.get(i), i > 0);
            }
        }

        if (selected.size() == 1) {
            selectedKeyframe = selected.getFirst();
            showGizmoFor(selectedKeyframe);
        } else {
            selectedKeyframe = null;
            if (GizmoManager.get().isActive()) {
                GizmoManager.get().deactivate();
            }
        }
        dragCurrentX = x1;
        dragCurrentY = y1;
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

    private CameraKeyframe pickNearestKeyframeFromCurve(Vec3d rayOrigin, Vec3d rayDir) {
        CurveHit hit = pickCurveHit(rayOrigin, rayDir);
        return hit != null ? hit.nearestKeyframe : null;
    }

    private CurveHit pickCurveHit(Vec3d rayOrigin, Vec3d rayDir) {
        CurveHit bestHit = null;
        for (KeyframeGroup group : getActiveCameraKeyframeGroups()) {
            CurveHit hit = pickCurveHitInGroup(rayOrigin, rayDir, group);
            if (hit == null) {
                continue;
            }
            if (bestHit == null || hit.distance < bestHit.distance) {
                bestHit = hit;
            }
        }
        return bestHit;
    }

    private CurveHit pickCurveHitInGroup(Vec3d rayOrigin, Vec3d rayDir, KeyframeGroup group) {
        List<CameraKeyframe> keyframes = group.keyframes;
        if (keyframes.size() < 2) {
            return null;
        }
        TimelineCameraPathManager manager = new TimelineCameraPathManager();
        manager.setKeyframes(keyframes);
        float start = keyframes.getFirst().getTime();
        float end = keyframes.getLast().getTime();
        float duration = Math.max(0.0001f, end - start);
        int segments = Math.max(8, (int) (duration * 16));
        segments = Math.min(segments, 512);

        Vec3d prevPoint = null;
        float prevTime = start;
        Vec3d bestPoint = null;
        float bestTime = start;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= segments; i++) {
            float time = start + duration * (i / (float) segments);
            CameraSample sample = manager.sample(time).orElse(null);
            if (sample == null) {
                continue;
            }
            Vec3d point = sample.position();
            if (prevPoint != null) {
                SegmentHit hit = distanceRayToSegment(rayOrigin, rayDir, prevPoint, point);
                double threshold = CURVE_PICK_BASE_RADIUS + Math.min(0.75, rayOrigin.distanceTo(hit.point) * CURVE_PICK_DISTANCE_SCALE);
                if (hit.distance <= threshold && hit.distance < bestDistance) {
                    bestDistance = hit.distance;
                    bestPoint = hit.point;
                    bestTime = prevTime + (time - prevTime) * (float) hit.segmentT;
                }
            }
            prevPoint = point;
            prevTime = time;
        }

        if (bestPoint == null) {
            return null;
        }

        CameraKeyframe nearest = null;
        CameraKeyframe previous = null;
        CameraKeyframe next = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (CameraKeyframe keyframe : keyframes) {
            double distance = toVec(keyframe.getPosition()).squaredDistanceTo(bestPoint);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = keyframe;
            }
            if (keyframe.getTime() <= bestTime && (previous == null || keyframe.getTime() > previous.getTime())) {
                previous = keyframe;
            }
            if (keyframe.getTime() >= bestTime && (next == null || keyframe.getTime() < next.getTime())) {
                next = keyframe;
            }
        }
        return new CurveHit(bestPoint, bestTime, nearest, previous, next, group.pathClip, bestDistance);
    }

    private SegmentHit distanceRayToSegment(Vec3d rayOrigin, Vec3d rayDir, Vec3d segA, Vec3d segB) {
        Vec3d u = rayDir;
        Vec3d v = segB.subtract(segA);
        Vec3d w0 = rayOrigin.subtract(segA);

        double a = u.dotProduct(u);
        double b = u.dotProduct(v);
        double c = v.dotProduct(v);
        double d = u.dotProduct(w0);
        double e = v.dotProduct(w0);
        double denom = a * c - b * b;

        double s = denom > 1e-8 ? (b * e - c * d) / denom : 0.0;
        double t = denom > 1e-8 ? (a * e - b * d) / denom : 0.0;
        s = Math.max(0.0, s);
        t = Math.max(0.0, Math.min(1.0, t));

        Vec3d rayPoint = rayOrigin.add(u.multiply(s));
        Vec3d segmentPoint = segA.add(v.multiply(t));
        return new SegmentHit(rayPoint.distanceTo(segmentPoint), segmentPoint, t);
    }

    private void showGizmoFor(CameraKeyframe keyframe) {
        Vec3d pos = toVec(keyframe.getPosition());
        Box box = anchorBox(pos);
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pos);
        lastGizmoPosition = pos;
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null) {
            controller.setRotationHandles(Collections.emptyList());
            controller.setTransformApplier(new CameraPathKeyframeTransformApplier(this));
        }
    }

    private void syncGizmoToSelectedKeyframe() {
        if (!GizmoManager.get().isActive()) {
            showGizmoFor(selectedKeyframe);
            return;
        }
        Vec3d pos = toVec(selectedKeyframe.getPosition());
        if (lastGizmoPosition == null || lastGizmoPosition.squaredDistanceTo(pos) > GIZMO_SYNC_EPSILON_SQ) {
            GizmoManager.get().setTargetBox(anchorBox(pos));
            GizmoManager.get().setPivot(pos);
            lastGizmoPosition = pos;
        }
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null && !(controller.getTransformApplier() instanceof CameraPathKeyframeTransformApplier)) {
            controller.setRotationHandles(Collections.emptyList());
            controller.setTransformApplier(new CameraPathKeyframeTransformApplier(this));
        }
    }

    private void clearSelectionAndGizmo() {
        selectedKeyframe = null;
        lastGizmoPosition = null;
        TimelineModel model = getTimelineModel();
        if (model != null) {
            model.clearSelection();
        }
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
        return track.findCustomPathClipForKeyframe(keyframe) != null;
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
        for (com.director.ui.timeline.model.camera.path.CameraPathClip pathClip : track.getCameraPathClips()) {
            if (pathClip instanceof CustomPathClip customPathClip) {
                out.addAll(customPathClip.getKeyframes());
            }
        }
        out.sort(Comparator.comparing(CameraKeyframe::getTime));
        return out;
    }

    private List<KeyframeGroup> getActiveCameraKeyframeGroups() {
        TimelineTrack track = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (track == null || track.getType() != TrackType.CAMERA) {
            return List.of();
        }

        List<KeyframeGroup> groups = new ArrayList<>();
        List<CameraKeyframe> legacyKeyframes = new ArrayList<>();
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof CameraKeyframe keyframe) {
                legacyKeyframes.add(keyframe);
            }
        }
        legacyKeyframes.sort(Comparator.comparing(CameraKeyframe::getTime));
        if (legacyKeyframes.size() >= 2) {
            groups.add(new KeyframeGroup(null, legacyKeyframes));
        }

        for (com.director.ui.timeline.model.camera.path.CameraPathClip pathClip : track.getCameraPathClips()) {
            if (!(pathClip instanceof CustomPathClip customPathClip) || customPathClip.getKeyframes().size() < 2) {
                continue;
            }
            List<CameraKeyframe> keyframes = new ArrayList<>(customPathClip.getKeyframes());
            keyframes.sort(Comparator.comparing(CameraKeyframe::getTime));
            groups.add(new KeyframeGroup(customPathClip, keyframes));
        }
        return groups;
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

    private ScreenPoint worldToScreen(MinecraftClient client, Vec3d worldPos) {
        if (client == null || client.gameRenderer == null || client.getWindow() == null) {
            return null;
        }

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        Vec3d rel = worldPos.subtract(camera.getPos());
        org.joml.Vector3f view = new org.joml.Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        new org.joml.Quaternionf(camera.getRotation()).conjugate().transform(view);

        GameRenderer gr = client.gameRenderer;
        float fov;
        if (gr instanceof GameRendererAccessor accessor) {
            fov = accessor.director$invokeGetFov(camera, 0.0f, true);
        } else {
            fov = (float) client.options.getFov().getValue();
        }

        Matrix4f projection = new Matrix4f(gr.getBasicProjectionMatrix(fov));
        Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0f).mul(projection);
        if (Math.abs(clip.w) < 1e-6f) {
            return null;
        }

        clip.div(clip.w);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.z)) {
            return null;
        }

        double x = (clip.x + 1.0) * 0.5 * client.getWindow().getWidth();
        double y = (1.0 - clip.y) * 0.5 * client.getWindow().getHeight();
        return new ScreenPoint(x, y);
    }

    private record SegmentHit(double distance, Vec3d point, double segmentT) {}

    private record CurveHit(
            Vec3d point,
            float time,
            CameraKeyframe nearestKeyframe,
            CameraKeyframe previousKeyframe,
            CameraKeyframe nextKeyframe,
            CustomPathClip pathClip,
            double distance
    ) {}

    private record KeyframeGroup(CustomPathClip pathClip, List<CameraKeyframe> keyframes) {}

    private record ScreenPoint(double x, double y) {}
}
