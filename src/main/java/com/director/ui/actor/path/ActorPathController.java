package com.director.ui.actor.path;

import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibraryManager;
import com.director.client.gizmo.GizmoInteractionController;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoType;
import com.director.client.input.DirectorInputSystem;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.playback.entity.ActorRouteSampler;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ActorPathController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathController.class);
    private static final ActorPathController INSTANCE = new ActorPathController();

    private static final int MAX_PATH_POINTS = 2048;
    private static final double MIN_SAMPLE_DISTANCE_SQ = 0.25;
    private static final float DEFAULT_SPEED = 2.0f;
    private static final float MIN_SEGMENT_DURATION = 0.1f;
    private static final double ANCHOR_PICK_BASE_RADIUS = 0.35;
    private static final double ANCHOR_PICK_DISTANCE_SCALE = 0.006;
    private static final double ANCHOR_BOX_HALF_SIZE = 0.12;
    private static final double GIZMO_SYNC_EPSILON_SQ = 1.0e-8;

    private final List<Vec3d> pathPoints = new ArrayList<>();

    private boolean active = false;
    private boolean drawing = false;
    private boolean lastLeftMouseDown = false;
    private boolean lastRightMouseDown = false;
    private boolean wasGizmoDragging = false;
    private boolean lastDeleteDown = false;

    private int selectedAnchorIndex = -1;
    private Vec3d lastGizmoPosition = null;
    private ActorRouteClip.InterpolationMode interpolationMode = ActorRouteClip.InterpolationMode.CATMULL_ROM;

    /** 正在编辑的库路径；null 表示正在绘制新路径 */
    private UUID editingLibraryPathId = null;
    private ActorRouteClip editingClip = null;

    private ActorPathController() {
    }

    public static ActorPathController getInstance() {
        return INSTANCE;
    }

    public void activate() {
        active = true;
        resetTransientInputState();
        beginNewDraft();
        tryLoadSelectedRouteClip();
        LOGGER.info("[ActorPath] Activated");
    }

    public void deactivate() {
        active = false;
        clearGizmo();
        resetState();
        LOGGER.info("[ActorPath] Deactivated");
    }

    public boolean isActive() {
        return active;
    }

    public List<Vec3d> getAnchorPositions() {
        return Collections.unmodifiableList(pathPoints);
    }

    public List<Vector3f> getWaypointVectors() {
        List<Vector3f> vectors = new ArrayList<>(pathPoints.size());
        for (Vec3d point : pathPoints) {
            vectors.add(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        }
        return vectors;
    }

    public int getSelectedAnchorIndex() {
        return selectedAnchorIndex;
    }

    public UUID getEditingLibraryPathId() {
        return editingLibraryPathId;
    }

    public Vec3d getAnchorPosition(int index) {
        if (index < 0 || index >= pathPoints.size()) {
            return null;
        }
        return pathPoints.get(index);
    }

    public void setAnchorPosition(int index, Vec3d position) {
        if (index < 0 || index >= pathPoints.size() || position == null) {
            return;
        }
        if (isEditingLocked()) {
            return;
        }
        pathPoints.set(index, position);
        drawing = !pathPoints.isEmpty();
        syncDraftToStorage();
    }

    public ActorRouteClip.InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public void setInterpolationMode(ActorRouteClip.InterpolationMode mode) {
        if (mode == null) {
            return;
        }
        interpolationMode = mode;
        syncDraftToStorage();
        if (editingClip != null) {
            editingClip.setInterpolationMode(mode);
            markTimelineDirty();
        }
    }

    public int getAnchorCount() {
        return pathPoints.size();
    }

    public boolean isDrawing() {
        return drawing;
    }

    public void loadPathForEditing(UUID pathId) {
        if (!active || pathId == null) {
            return;
        }
        ActorPathDefinition path = ActorPathLibraryManager.getPath(pathId);
        if (path == null) {
            return;
        }
        if (path.isLocked()) {
            LOGGER.warn("[ActorPath] Path '{}' is locked, cannot edit", path.getName());
            return;
        }
        editingLibraryPathId = pathId;
        editingClip = null;
        interpolationMode = path.getInterpolationMode();
        pathPoints.clear();
        for (Vector3f point : path.getWaypoints()) {
            pathPoints.add(new Vec3d(point.x, point.y, point.z));
        }
        drawing = pathPoints.size() >= 2;
        selectedAnchorIndex = pathPoints.isEmpty() ? -1 : 0;
        if (selectedAnchorIndex >= 0) {
            showGizmoForAnchor(selectedAnchorIndex);
        } else {
            clearGizmo();
        }
        LOGGER.info("[ActorPath] Loaded library path '{}' for editing", path.getName());
    }

    public void onPathRemovedFromLibrary(UUID pathId) {
        if (pathId != null && pathId.equals(editingLibraryPathId)) {
            beginNewDraft();
        }
    }

    public void update() {
        if (!active) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.getWindow() == null) {
            return;
        }

        boolean draggingNow = GizmoManager.get().isDraggingHandle();

        if (ImGui.getIO().getWantCaptureMouse() && !draggingNow) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        if (selectedAnchorIndex >= 0 && !draggingNow) {
            syncGizmoToSelectedAnchor();
        }

        if (wasGizmoDragging && !draggingNow) {
            syncDraftToStorage();
            markTimelineDirty();
        }
        wasGizmoDragging = draggingNow;

        if (draggingNow || GizmoManager.get().isHot()) {
            boolean leftDown = isLeftMouseDown(client);
            boolean rightDown = isRightMouseDown(client);
            lastLeftMouseDown = leftDown;
            lastRightMouseDown = rightDown;
            return;
        }

        if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            beginNewDraft();
            return;
        }

        boolean deleteDown = isKeyPressed(client, GLFW.GLFW_KEY_DELETE) || isKeyPressed(client, GLFW.GLFW_KEY_BACKSPACE);
        if (deleteDown && !lastDeleteDown) {
            handleDeleteSelectedAnchor();
        }
        lastDeleteDown = deleteDown;

        boolean leftDown = isLeftMouseDown(client);
        boolean rightDown = isRightMouseDown(client);

        if (rightDown && !lastRightMouseDown) {
            handleRightClick(client);
        }
        if (leftDown && !lastLeftMouseDown) {
            handleLeftClick();
        }

        lastLeftMouseDown = leftDown;
        lastRightMouseDown = rightDown;
    }

    public void finalizePath() {
        completeCurrentPath();
    }

    public void removeSelectedAnchor() {
        handleDeleteSelectedAnchor();
    }

    public void applyPathToSelectedActor() {
        UUID pathId = editingLibraryPathId;
        if (pathPoints.size() >= 2) {
            pathId = completeCurrentPath();
        }
        if (pathId == null) {
            return;
        }
        createRouteClipFromLibraryPath(pathId);
    }

    private void handleRightClick(MinecraftClient client) {
        if (!drawing || pathPoints.isEmpty()) {
            beginNewDraft();
        }
        handleAddPoint(client);
    }

    private void handleLeftClick() {
        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
        if (ray == null) {
            return;
        }

        int picked = pickNearestAnchor(ray.origin(), ray.direction());
        if (picked >= 0) {
            selectedAnchorIndex = picked;
            showGizmoForAnchor(picked);
            return;
        }

        if (drawing && pathPoints.size() >= 2) {
            completeCurrentPath();
            return;
        }

        clearAnchorSelection();
    }

    private void handleAddPoint(MinecraftClient client) {
        BlockHitResult hit = resolvePlacementHit(client);
        if (hit == null) {
            return;
        }

        if (pathPoints.size() >= MAX_PATH_POINTS) {
            LOGGER.warn("[ActorPath] Path point limit reached: {}", MAX_PATH_POINTS);
            return;
        }

        Vec3d pos = hit.getPos();
        if (!pathPoints.isEmpty()) {
            Vec3d last = pathPoints.getLast();
            if (last.squaredDistanceTo(pos) < MIN_SAMPLE_DISTANCE_SQ) {
                return;
            }
        }

        pathPoints.add(pos);
        drawing = true;
        selectedAnchorIndex = pathPoints.size() - 1;
        showGizmoForAnchor(selectedAnchorIndex);
        syncDraftToStorage();
    }

    private UUID completeCurrentPath() {
        if (pathPoints.size() < 2) {
            return editingLibraryPathId;
        }

        List<Vector3f> waypoints = getWaypointVectors();
        UUID savedId;
        if (editingLibraryPathId != null) {
            savedId = editingLibraryPathId;
            ActorPathLibraryManager.updatePath(savedId, waypoints, interpolationMode);
            ActorPathDefinition path = ActorPathLibraryManager.getPath(savedId);
            LOGGER.info("[ActorPath] Updated library path '{}'", path != null ? path.getName() : savedId);
        } else {
            ActorPathDefinition created = ActorPathLibraryManager.createPath(
                    ActorPathLibraryManager.getInstance().generateUniqueName("Path"),
                    waypoints,
                    interpolationMode
            );
            savedId = created.getId();
            LOGGER.info("[ActorPath] Saved new library path '{}'", created.getName());
        }

        beginNewDraft();
        return savedId;
    }

    private void beginNewDraft() {
        pathPoints.clear();
        drawing = false;
        selectedAnchorIndex = -1;
        editingLibraryPathId = null;
        clearGizmo();
    }

    private void handleDeleteSelectedAnchor() {
        if (isEditingLocked()) {
            return;
        }
        if (selectedAnchorIndex < 0 || selectedAnchorIndex >= pathPoints.size()) {
            return;
        }
        pathPoints.remove(selectedAnchorIndex);
        if (pathPoints.isEmpty()) {
            drawing = false;
            clearAnchorSelection();
        } else {
            drawing = true;
            selectedAnchorIndex = Math.min(selectedAnchorIndex, pathPoints.size() - 1);
            showGizmoForAnchor(selectedAnchorIndex);
        }
        syncDraftToStorage();
    }

    private void syncDraftToStorage() {
        if (isEditingLocked()) {
            return;
        }
        if (editingLibraryPathId != null && pathPoints.size() >= 2) {
            ActorPathLibraryManager.updatePath(editingLibraryPathId, getWaypointVectors(), interpolationMode);
        }
        if (editingClip != null) {
            editingClip.setWaypoints(getWaypointVectors());
            editingClip.setInterpolationMode(interpolationMode);
            updateRouteDuration(editingClip);
            markTimelineDirty();
        }
    }

    private void createRouteClipFromLibraryPath(UUID pathId) {
        ActorPathDefinition libraryPath = ActorPathLibraryManager.getPath(pathId);
        if (libraryPath == null || libraryPath.getWaypoints().size() < 2) {
            return;
        }

        UUID actorId = ActorSelectionHub.getSelectedActorId();
        if (actorId == null) {
            LOGGER.warn("[ActorPath] No actor selected, cannot build path clips");
            return;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
        if (timelineTrack == null) {
            LOGGER.warn("[ActorPath] No entity track for actor {}", actorId);
            return;
        }

        ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
        if (actorTrack == null) {
            return;
        }

        List<Vector3f> waypoints = new ArrayList<>(libraryPath.getWaypoints());
        ActorRouteClip.InterpolationMode mode = libraryPath.getInterpolationMode();
        String clipName = libraryPath.getName();

        float start = model.getCurrentTime();
        float duration = Math.max(MIN_SEGMENT_DURATION,
                ActorRouteSampler.computeCurveLength(waypoints, mode) / DEFAULT_SPEED);
        ActorRouteClip clip = new ActorRouteClip(clipName, start, start + duration);
        clip.setSpeed(DEFAULT_SPEED);
        clip.setInterpolationMode(mode);
        clip.setLoopMode(ActorRouteClip.LoopMode.ONCE);
        clip.setAlignRotationToRoute(true);
        clip.setWaypoints(waypoints);

        actorTrack.addClip(clip);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, actorTrack.getColorRgba());
        model.addClip(timelineTrack, timelineClip);
        model.selectItem(timelineClip, false);
        model.markDirty();
        editingClip = clip;
        LOGGER.info("[ActorPath] Created route clip '{}' for actor {}", clipName, actorId);
    }

    private void tryLoadSelectedRouteClip() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        UUID actorId = ActorSelectionHub.getSelectedActorId();
        for (Object item : model.getSelectedItems()) {
            if (!(item instanceof EntityTimelineClip timelineClip)) {
                continue;
            }
            if (!(timelineClip.getEntityClip() instanceof ActorRouteClip routeClip)) {
                continue;
            }
            if (actorId != null) {
                TimelineTrack track = model.findActorTrackByActorId(actorId);
                if (track == null || !track.getClips().contains(timelineClip)) {
                    continue;
                }
            }

            editingClip = routeClip;
            editingLibraryPathId = null;
            interpolationMode = routeClip.getInterpolationMode();
            pathPoints.clear();
            for (Vector3f point : routeClip.getWaypoints()) {
                pathPoints.add(new Vec3d(point.x, point.y, point.z));
            }
            drawing = pathPoints.size() >= 2;
            if (!pathPoints.isEmpty()) {
                selectedAnchorIndex = 0;
                showGizmoForAnchor(0);
            }
            LOGGER.info("[ActorPath] Loaded existing route clip with {} anchors", pathPoints.size());
            return;
        }
    }

    private void updateRouteDuration(ActorRouteClip clip) {
        float distance = ActorRouteSampler.computeCurveLength(clip.getWaypoints(), clip.getInterpolationMode());
        float duration = Math.max(MIN_SEGMENT_DURATION, distance / Math.max(0.05f, clip.getSpeed()));
        clip.setTimeRange(clip.getStartTime(), clip.getStartTime() + duration);
    }

    private int pickNearestAnchor(Vec3d rayOrigin, Vec3d rayDir) {
        if (pathPoints.isEmpty()) {
            return -1;
        }

        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < pathPoints.size(); i++) {
            Vec3d point = pathPoints.get(i);
            double t = point.subtract(rayOrigin).dotProduct(rayDir);
            if (t < 0) {
                continue;
            }
            Vec3d closest = rayOrigin.add(rayDir.multiply(t));
            double distance = closest.distanceTo(point);
            double threshold = ANCHOR_PICK_BASE_RADIUS
                    + Math.min(0.75, rayOrigin.distanceTo(point) * ANCHOR_PICK_DISTANCE_SCALE);
            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void showGizmoForAnchor(int index) {
        Vec3d pos = getAnchorPosition(index);
        if (pos == null) {
            return;
        }
        Box box = anchorBox(pos);
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pos);
        lastGizmoPosition = pos;
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null) {
            controller.setRotationHandles(Collections.emptyList());
            controller.setTransformApplier(new ActorPathWaypointTransformApplier(this));
        }
    }

    private void syncGizmoToSelectedAnchor() {
        if (selectedAnchorIndex < 0) {
            return;
        }
        Vec3d pos = getAnchorPosition(selectedAnchorIndex);
        if (pos == null) {
            return;
        }
        if (!GizmoManager.get().isActive()) {
            showGizmoForAnchor(selectedAnchorIndex);
            return;
        }
        if (lastGizmoPosition == null || lastGizmoPosition.squaredDistanceTo(pos) > GIZMO_SYNC_EPSILON_SQ) {
            GizmoManager.get().setTargetBox(anchorBox(pos));
            GizmoManager.get().setPivot(pos);
            lastGizmoPosition = pos;
        }
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null && !(controller.getTransformApplier() instanceof ActorPathWaypointTransformApplier)) {
            controller.setRotationHandles(Collections.emptyList());
            controller.setTransformApplier(new ActorPathWaypointTransformApplier(this));
        }
    }

    private void clearAnchorSelection() {
        selectedAnchorIndex = -1;
        lastGizmoPosition = null;
        if (GizmoManager.get().isActive()
                && GizmoManager.get().getController() != null
                && GizmoManager.get().getController().getTransformApplier() instanceof ActorPathWaypointTransformApplier) {
            GizmoManager.get().deactivate();
        }
    }

    private void clearGizmo() {
        selectedAnchorIndex = -1;
        lastGizmoPosition = null;
        if (GizmoManager.get().isActive()) {
            GizmoManager.get().deactivate();
        }
    }

    private void resetState() {
        pathPoints.clear();
        drawing = false;
        selectedAnchorIndex = -1;
        editingLibraryPathId = null;
        editingClip = null;
        interpolationMode = ActorRouteClip.InterpolationMode.CATMULL_ROM;
        resetTransientInputState();
        clearGizmo();
    }

    private void resetTransientInputState() {
        lastLeftMouseDown = false;
        lastRightMouseDown = false;
        wasGizmoDragging = false;
        lastDeleteDown = false;
    }

    private void markTimelineDirty() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }
    }

    private static Box anchorBox(Vec3d pos) {
        return new Box(
                pos.x - ANCHOR_BOX_HALF_SIZE, pos.y - ANCHOR_BOX_HALF_SIZE, pos.z - ANCHOR_BOX_HALF_SIZE,
                pos.x + ANCHOR_BOX_HALF_SIZE, pos.y + ANCHOR_BOX_HALF_SIZE, pos.z + ANCHOR_BOX_HALF_SIZE
        );
    }

    private BlockHitResult resolvePlacementHit(MinecraftClient client) {
        BlockHitResult hit = DirectorInputSystem.getCurrentRay();
        if (hit == null) {
            hit = DirectorInputSystem.raycastFromImGui();
        }
        if (hit == null && client.crosshairTarget instanceof BlockHitResult crosshairHit
                && crosshairHit.getType() == HitResult.Type.BLOCK) {
            hit = crosshairHit;
        }
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }
        return null;
    }

    private static boolean isLeftMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static boolean isRightMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private static boolean isKeyPressed(MinecraftClient client, int key) {
        return GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private boolean isEditingLocked() {
        if (editingLibraryPathId == null) {
            return false;
        }
        ActorPathDefinition path = ActorPathLibraryManager.getPath(editingLibraryPathId);
        return path != null && path.isLocked();
    }
}
