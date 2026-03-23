package com.director.ui.actor.path;

import com.director.client.gizmo.GizmoManager;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.ToolPreviewManager;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.EntityTrack;
import com.director.ui.timeline.model.entity.EntityTransformClip;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

    public static ActorPathController getInstance() {
        return INSTANCE;
    }

    private static final int MAX_PATH_POINTS = 2048;
    private static final double MIN_SAMPLE_DISTANCE_SQ = 0.25;
    private static final double MIN_SEGMENT_DISTANCE_SQ = 0.01;
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

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorPath] TimelineModel unavailable");
            resetState();
            return;
        }

        TimelineTrack timelineTrack = model.findEntityTrackByActorId(actorId);
        if (timelineTrack == null) {
            LOGGER.warn("[ActorPath] No entity track for actor {}", actorId);
            resetState();
            return;
        }

        EntityTrack entityTrack = timelineTrack.getEntityTrackPayload();
        if (entityTrack == null) {
            LOGGER.warn("[ActorPath] EntityTrack payload missing for {}", timelineTrack.getName());
            resetState();
            return;
        }

        float timeCursor = model.getCurrentTime();
        int created = 0;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec3d from = pathPoints.get(i);
            Vec3d to = pathPoints.get(i + 1);
            double distanceSq = from.squaredDistanceTo(to);
            if (distanceSq < MIN_SEGMENT_DISTANCE_SQ) {
                continue;
            }

            float duration = Math.max(MIN_SEGMENT_DURATION, (float) (Math.sqrt(distanceSq) / DEFAULT_SPEED));
            EntityTransformClip clip = new EntityTransformClip("Path", timeCursor, timeCursor + duration);
            clip.setInterpolation(TimelineInterpolation.LINEAR);
            clip.setFromPos((float) from.x, (float) from.y, (float) from.z);
            clip.setToPos((float) to.x, (float) to.y, (float) to.z);

            float[] yawPitch = computeYawPitch(from, to);
            clip.setFromRot(yawPitch[0], yawPitch[1], 0f);
            clip.setToRot(yawPitch[0], yawPitch[1], 0f);
            clip.setFromScale(1f, 1f, 1f);
            clip.setToScale(1f, 1f, 1f);

            entityTrack.addClip(clip);
            model.addClip(timelineTrack, new EntityTimelineClip(clip, entityTrack.getColorRgba()));

            timeCursor += duration;
            created++;
        }

        if (created > 0) {
            model.markDirty();
            LOGGER.info("[ActorPath] Created {} path segments for actor {}", created, actorId);
        }

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

    private float[] computeYawPitch(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-6 && Math.abs(dy) < 1.0e-6) {
            return new float[]{0f, 0f};
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        return new float[]{yaw, pitch};
    }
}
