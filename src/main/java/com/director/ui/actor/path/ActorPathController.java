package com.director.ui.actor.path;

import com.director.core.DirectorServices;
import com.director.client.gizmo.GizmoManager;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.ToolPreviewManager;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.entity.ActorRouteClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.ActorTrack;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ActorPathController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathController.class);
    private static final ActorPathController INSTANCE = new ActorPathController();

    public static ActorPathController getInstance() {
        return INSTANCE;
    }

    private static final int MAX_PATH_POINTS = 2048;
    private static final double MIN_SAMPLE_DISTANCE_SQ = 0.25;
    private static final float DEFAULT_SPEED = 2.0f;
    private static final float MIN_SEGMENT_DURATION = 0.1f;

    private final ToolPreviewManager previewManager = ToolPreviewManager.getInstance();
    private final List<Vec3d> pathPoints = new ArrayList<>();

    private boolean active = false;
    private boolean drawing = false;
    private boolean lastLeftMouseDown = false;
    private boolean lastRightMouseDown = false;

    private ActorPathController() {
    }

    public void activate() {
        active = true;
        resetState();
        LOGGER.info("[ActorPath] Activated");
    }

    public void deactivate() {
        active = false;
        resetState();
        LOGGER.info("[ActorPath] Deactivated");
    }

    public boolean isActive() {
        return active;
    }

    public void update() {
        if (!active) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.getWindow() == null) {
            return;
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        if (GizmoManager.get().isHot()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            resetState();
            return;
        }

        boolean leftDown = isLeftMouseDown(client);
        boolean rightDown = isRightMouseDown(client);

        if (leftDown && !lastLeftMouseDown) {
            handleAddPoint(client);
        }
        if (rightDown && !lastRightMouseDown) {
            handleFinalizePath();
        }

        lastLeftMouseDown = leftDown;
        lastRightMouseDown = rightDown;
    }

    private boolean isLeftMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;
    }

    private boolean isRightMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
        ) == GLFW.GLFW_PRESS;
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
        refreshPreview();
    }

    private void handleFinalizePath() {
        if (!drawing) {
            return;
        }
        drawing = false;

        if (pathPoints.size() < 2) {
            resetState();
            return;
        }

        UUID actorId = ActorSelectionHub.getSelectedActorId();
        if (actorId == null) {
            LOGGER.warn("[ActorPath] No actor selected, cannot build path clips");
            resetState();
            return;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorPath] TimelineModel unavailable");
            resetState();
            return;
        }

        TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
        if (timelineTrack == null) {
            LOGGER.warn("[ActorPath] No entity track for actor {}", actorId);
            resetState();
            return;
        }

        ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
        if (actorTrack == null) {
            LOGGER.warn("[ActorPath] ActorTrack payload missing for {}", timelineTrack.getName());
            resetState();
            return;
        }

        float start = model.getCurrentTime();
        float duration = Math.max(MIN_SEGMENT_DURATION, computeRouteLength(pathPoints) / DEFAULT_SPEED);
        ActorRouteClip clip = new ActorRouteClip("Route", start, start + duration);
        clip.setSpeed(DEFAULT_SPEED);
        clip.setInterpolationMode(ActorRouteClip.InterpolationMode.LINEAR);
        clip.setLoopMode(ActorRouteClip.LoopMode.ONCE);
        clip.setAlignRotationToRoute(true);

        List<Vector3f> routePoints = new ArrayList<>(pathPoints.size());
        for (Vec3d point : pathPoints) {
            routePoints.add(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        }
        clip.setWaypoints(routePoints);

        actorTrack.addClip(clip);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, actorTrack.getColorRgba());
        model.addClip(timelineTrack, timelineClip);
        model.selectItem(timelineClip, false);
        model.markDirty();
        LOGGER.info("[ActorPath] Created route clip with {} waypoints for actor {}", routePoints.size(), actorId);

        resetState();
    }

    private void resetState() {
        pathPoints.clear();
        drawing = false;
        lastLeftMouseDown = false;
        lastRightMouseDown = false;
        previewManager.updateLassoPath(Collections.emptyList(), false);
        previewManager.updateLassoFill(Collections.emptyList());
    }

    private void refreshPreview() {
        if (pathPoints.isEmpty()) {
            previewManager.updateLassoPath(Collections.emptyList(), false);
            previewManager.updateLassoFill(Collections.emptyList());
            return;
        }

        List<BlockPos> pathBlocks = new ArrayList<>(pathPoints.size());
        for (Vec3d pos : pathPoints) {
            pathBlocks.add(BlockPos.ofFloored(pos));
        }
        previewManager.updateLassoPath(pathBlocks, false);
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

    private float computeRouteLength(List<Vec3d> points) {
        float total = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            total += (float) points.get(i).distanceTo(points.get(i + 1));
        }
        return total;
    }
}
