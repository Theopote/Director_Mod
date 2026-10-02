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
import com.director.timeline.model.entity.EntityTimelineClip;
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
    private static final double ANCHOR_PICK_BASE_RADIUS = 0.35;
    private static final double ANCHOR_PICK_DISTANCE_SCALE = 0.006;
    private static final double ANCHOR_BOX_HALF_SIZE = 0.12;
    private static final double GIZMO_SYNC_EPSILON_SQ = 1.0e-8;

    private final List<Vec3d> pathPoints = new ArrayList<>();

    private boolean active = false;
    private boolean drawing = false;
    private boolean lastLeftMouseDown = false;
    private boolean lastRightMouseDown = false;
    private boolean lastDeleteDown = false;

    private int selectedAnchorIndex = -1;
    private Vec3d lastGizmoPosition = null;
    private ActorRouteClip.InterpolationMode interpolationMode = ActorRouteClip.InterpolationMode.CATMULL_ROM;

    /** 正在编辑的库路径；null 表示正在绘制新路径 */
    private UUID editingLibraryPathId = null;
    private ActorRouteClip editingClip = null;
    private boolean anchorDragDirty;
    private boolean anchorDragActive;
    private List<Vec3d> dragBeforePoints;
    private UUID dragActorId;
    private TimelineModel dragModel;
    private com.director.ui.actor.edit.ActorGestureContext dragContext;
    private float dragTime;
    private int dragAnchorIndex;
    private ActorPathEditing.Target editingTarget;
    private com.director.actor.path.ActorPathLibrary editingLibrary;
    private boolean lastEscapeDown;
    private String lastError = "";
    public String getLastError() { return lastError; }
    public boolean isLibraryEditing() { return editingLibraryPathId != null; }
    public boolean isRouteEditing() { return editingClip != null; }
    public boolean isReadOnly() { return isEditingLocked(); }
    public boolean canRemoveSelectedAnchor() {
        return selectedAnchorIndex >= 0 && selectedAnchorIndex < pathPoints.size()
                && (editingTarget == null || pathPoints.size() > 2) && !isEditingLocked();
    }

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
        cancelAnchorDrag();
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

    public boolean setAnchorPosition(int index, Vec3d position) {
        if (index < 0 || index >= pathPoints.size() || position == null
                || !Float.isFinite((float) position.x) || !Float.isFinite((float) position.y)
                || !Float.isFinite((float) position.z) || isEditingLocked()) return false;
        if (GizmoManager.get().isDraggingHandle() && !isAnchorDragCurrent()) return false;
        if (position.equals(pathPoints.get(index))) return true;
        pathPoints.set(index, position);
        drawing = !pathPoints.isEmpty();
        if (anchorDragActive) { anchorDragDirty = true; return true; }
        return syncDraftToStorage();
    }

    boolean beginAnchorDrag() {
        if (!active || isEditingLocked() || selectedAnchorIndex < 0 || selectedAnchorIndex >= pathPoints.size()) return false;
        dragModel = DirectorServices.get().containerTimeline().getTimelineModel();
        if (dragModel == null) return false;
        dragBeforePoints = List.copyOf(pathPoints);
        dragActorId = ActorSelectionHub.getSelectedActorId();
        dragContext=new com.director.ui.actor.edit.ActorGestureContext(dragModel);
        dragTime = dragModel.getCurrentTime(); dragAnchorIndex = selectedAnchorIndex;
        editingLibrary = ActorPathLibraryManager.getInstance();
        anchorDragActive = true; anchorDragDirty = false;
        return true;
    }

    boolean isAnchorDragCurrent() {
        var gm = GizmoManager.get();
        return anchorDragActive && dragContext!=null && dragContext.isCurrent(DirectorServices.get().containerTimeline().getTimelineModel()) && active && !isEditingLocked() && dragModel != null
                && dragModel == DirectorServices.get().containerTimeline().getTimelineModel()
                && dragTime == dragModel.getCurrentTime()
                && java.util.Objects.equals(dragActorId, ActorSelectionHub.getSelectedActorId())
                && dragAnchorIndex == selectedAnchorIndex && editingLibrary == ActorPathLibraryManager.getInstance()
                && gm.isActive() && gm.getController() != null
                && gm.getContext() != null && gm.getContext().getMode() == com.director.client.gizmo.GizmoMode.TRANSLATE
                && gm.getController().getTransformApplier() instanceof ActorPathWaypointTransformApplier;
    }

    void finishAnchorDrag() {
        if (!anchorDragActive) return;
        if (!isAnchorDragCurrent()) { cancelAnchorDrag(); return; }
        boolean changed = anchorDragDirty && !dragBeforePoints.equals(pathPoints);
        anchorDragActive = false; anchorDragDirty = false;
        if (changed) syncDraftToStorage();
        dragBeforePoints = null;
    }

    void cancelAnchorDrag() {
        if (anchorDragActive && dragBeforePoints != null) {
            pathPoints.clear(); pathPoints.addAll(dragBeforePoints);
        }
        anchorDragActive = false; anchorDragDirty = false; dragBeforePoints = null;
        lastGizmoPosition = null;
    }

    public ActorRouteClip.InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public void setInterpolationMode(ActorRouteClip.InterpolationMode mode) {
        if (mode == interpolationMode) return;
        if (mode == null || isEditingLocked()) {
            return;
        }
        cancelAnchorDrag();
        interpolationMode = mode;
        syncDraftToStorage();
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
        cancelAnchorDrag();
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return;
        try { editingTarget = ActorPathEditing.library(model, ActorPathLibraryManager.getInstance(), path, librarySaver()); }
        catch (IllegalArgumentException unavailable) { LOGGER.warn("[ActorPath] {}", unavailable.getMessage()); return; }
        editingLibrary = ActorPathLibraryManager.getInstance();
        editingLibraryPathId = pathId;
        editingClip = null;
        lastError = "";
        editingTarget = null; editingLibrary = null;
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
            cancelAnchorDrag();
            return;
        }
        boolean escapeDown = isKeyPressed(client, GLFW.GLFW_KEY_ESCAPE) && !ImGui.getIO().getWantTextInput();
        boolean escapePressed = escapeDown && !lastEscapeDown; lastEscapeDown = escapeDown;
        if (escapePressed) {
            if (anchorDragActive) cancelAnchorDrag(); else beginNewDraft();
            return;
        }
        boolean draggingNow = GizmoManager.get().isDraggingHandle();
        if (anchorDragActive && (!draggingNow || !isAnchorDragCurrent())) cancelAnchorDrag();
        if (editingTarget != null && !isTargetCurrent()) reloadEditingSource();

        processDeleteShortcut();

        if (ImGui.getIO().getWantCaptureMouse() && !draggingNow) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        if (selectedAnchorIndex >= 0 && !draggingNow) {
            syncGizmoToSelectedAnchor();
        }


        if (draggingNow || GizmoManager.get().isHot()) {
            boolean leftDown = isLeftMouseDown(client);
            boolean rightDown = isRightMouseDown(client);
            lastLeftMouseDown = leftDown;
            lastRightMouseDown = rightDown;
            return;
        }

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
        cancelAnchorDrag();
        if (isEditingLocked() || pathPoints.size() < 2) { lastError = "请先准备至少两个锚点，并选择可编辑的目标"; return; }
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return;
        try {
            var track = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, ActorSelectionHub.getSelectedActorId());
            String name = editingLibraryPathId != null ? ActorPathLibraryManager.getPath(editingLibraryPathId).getName()
                    : editingClip != null ? editingClip.getName() : "Actor route";
            var wrapper = ActorPathEditing.attach(model, track, getWaypointVectors(), interpolationMode, name);
            lastError = "";
            model.selectItem(wrapper, false);
            beginNewDraft();
            tryLoadSelectedRouteClip();
        } catch (IllegalArgumentException | IllegalStateException failure) {
            lastError = failure.getMessage();
            LOGGER.warn("[ActorPath] {}", failure.getMessage());
        }
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
        if (isEditingLocked()) return;
        cancelAnchorDrag();
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
        cancelAnchorDrag();
        if (isEditingLocked() || pathPoints.size() < 2) return null;
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return null;
        try {
            UUID id;
            if (editingLibraryPathId != null) {
                if (!syncDraftToStorage()) return null;
                id = editingLibraryPathId;
            } else {
                var created = ActorPathEditing.saveNew(model, ActorPathLibraryManager.getInstance(),
                        getWaypointVectors(), interpolationMode, librarySaver());
                id = created.getId();
            }
            beginNewDraft();
            return id;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            lastError = failure.getMessage();
            LOGGER.warn("[ActorPath] {}", failure.getMessage()); return null;
        }
    }

    private void beginNewDraft() {
        cancelAnchorDrag();
        pathPoints.clear(); drawing = false; selectedAnchorIndex = -1;
        editingLibraryPathId = null; editingClip = null; editingTarget = null; editingLibrary = null;
        clearGizmo();
    }

    private void processDeleteShortcut() {
        if (selectedAnchorIndex < 0) {
            lastDeleteDown = false;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        boolean deleteDown = isKeyPressed(client, GLFW.GLFW_KEY_DELETE)
                || isKeyPressed(client, GLFW.GLFW_KEY_BACKSPACE);
        if (deleteDown && !lastDeleteDown) {
            if (!(ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive())) {
                handleDeleteSelectedAnchor();
            }
        }
        lastDeleteDown = deleteDown;
    }

    private void handleDeleteSelectedAnchor() {
        cancelAnchorDrag();
        if (editingTarget != null && pathPoints.size() <= 2) return;
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

    private boolean syncDraftToStorage() {
        if (isEditingLocked()) { reloadEditingSource(); return false; }
        if (editingTarget == null) return true; // Unsaved paths remain local drafts.
        try {
            if (!isTargetCurrent() || !editingTarget.commit(getWaypointVectors(), interpolationMode)) {
                reloadEditingSource(); return false;
            }
            captureEditingTarget();
            lastError = "";
            return true;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            lastError = failure.getMessage();
            LOGGER.warn("[ActorPath] {}", failure.getMessage());
            reloadEditingSource(); return false;
        }
    }

    private boolean isTargetCurrent() {
        if (editingTarget == null) return true;
        if (editingTarget.model() != DirectorServices.get().containerTimeline().getTimelineModel() || !editingTarget.isCurrent()) return false;
        if (editingLibraryPathId != null) return editingTarget.library() == ActorPathLibraryManager.getInstance();
        try {
            return editingTarget.owner() == com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(
                    editingTarget.model(), ActorSelectionHub.getSelectedActorId());
        } catch (IllegalArgumentException unavailable) { return false; }
    }

    private void captureEditingTarget() {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (editingLibraryPathId != null) {
            editingTarget = ActorPathEditing.library(model, ActorPathLibraryManager.getInstance(),
                    ActorPathLibraryManager.getPath(editingLibraryPathId), librarySaver());
        } else if (editingClip != null) {
            editingTarget = ActorPathEditing.route(model, editingClip.getTimelineClip().getParentTrack(), editingClip);
        }
    }

    private void reloadEditingSource() {
        cancelAnchorDrag();
        var previous = editingTarget;
        if (previous == null) return;
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != previous.model() || editingLibraryPathId != null && previous.library() != ActorPathLibraryManager.getInstance()) {
            beginNewDraft(); return;
        }
        try {
            if (editingClip != null && previous.owner() != com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, ActorSelectionHub.getSelectedActorId())) {
                beginNewDraft(); return;
            }
            captureEditingTarget();
            var points = editingClip != null ? editingClip.getWaypoints() : ActorPathLibraryManager.getPath(editingLibraryPathId).getWaypoints();
            interpolationMode = editingClip != null ? editingClip.getInterpolationMode() : ActorPathLibraryManager.getPath(editingLibraryPathId).getInterpolationMode();
            pathPoints.clear();
            for (var point : points) pathPoints.add(new Vec3d(point.x, point.y, point.z));
            selectedAnchorIndex = Math.min(selectedAnchorIndex, pathPoints.size() - 1);
            drawing = pathPoints.size() >= 2; lastGizmoPosition = null;
        } catch (IllegalArgumentException | NullPointerException unavailable) { beginNewDraft(); }
    }

    static Runnable librarySaver() {
        var library = ActorPathLibraryManager.getInstance();
        return () -> {
            if (library != ActorPathLibraryManager.getInstance()) throw new IllegalArgumentException("路径库已切换，请重新选择");
            if (!ActorPathLibraryManager.save()) throw new IllegalStateException("路径库保存失败");
        };
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
                TimelineTrack track = timelineClip.getParentTrack();
                if (track == null || track.getActorTrackPayload() == null || track.getActorTrackPayload().getActorRef() == null
                        || !actorId.equals(track.getActorTrackPayload().getActorRef().getActorId()) || !track.getClips().contains(timelineClip)) {
                    continue;
                }
            }

            try { editingTarget = ActorPathEditing.route(model, timelineClip.getParentTrack(), routeClip); }
            catch (IllegalArgumentException unavailable) { return; }
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
        if (GizmoManager.get().getContext() != null)
            GizmoManager.get().getContext().setMode(com.director.client.gizmo.GizmoMode.TRANSLATE);
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
        if (GizmoManager.get().getContext() != null)
            GizmoManager.get().getContext().setMode(com.director.client.gizmo.GizmoMode.TRANSLATE);
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
        if (GizmoManager.get().isActive() && GizmoManager.get().getController() != null
                && GizmoManager.get().getController().getTransformApplier() instanceof ActorPathWaypointTransformApplier) {
            GizmoManager.get().deactivate();
        }
    }

    private void resetState() {
        pathPoints.clear();
        drawing = false;
        selectedAnchorIndex = -1;
        editingLibraryPathId = null;
        editingClip = null;
        editingTarget = null; editingLibrary = null;
        interpolationMode = ActorRouteClip.InterpolationMode.CATMULL_ROM;
        resetTransientInputState();
        clearGizmo();
    }

    private void resetTransientInputState() {
        lastLeftMouseDown = false;
        lastRightMouseDown = false;
        lastDeleteDown = false;
        lastEscapeDown = false;
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
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        return model == null || model.isPlaying() || editingTarget != null && !isTargetCurrent();
    }
}
