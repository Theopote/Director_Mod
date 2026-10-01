package com.director.ui.actor.lookat;

import com.director.core.DirectorServices;
import com.director.actor.visualizer.ActorLookAtVisualizer;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.timeline.model.entity.EntityLookAtClip;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MarkerDragController v1.0
 *
 * <p>LOOK_AT 模式下：
 * <ul>
 *   <li>检测鼠标是否悬停在 Marker 上</li>
 *   <li>Preview a detached target during dragging and commit once on release.</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>Marker 不是 Entity，使用屏幕空间拾取判定</li>
 *   <li>拖拽逻辑独立于 LookAt 创建逻辑</li>
 *   <li>沿用 ActorGizmoController 的 drag start/update/end 模式</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class MarkerDragController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkerDragController.class);
    private static final MarkerDragController INSTANCE = new MarkerDragController();

    public static MarkerDragController getInstance() {
        return INSTANCE;
    }

    /** 是否正在拖拽 */
    private boolean dragging = false;

    /** 当前编辑的 LookAtClip */
    private EntityLookAtClip editingClip = null;
    private ActorLookAtEditSession session;

    /** 上次鼠标左键状态（用于 edge trigger） */
    private boolean lastLeftMouseDown = false;

    private MarkerDragController() {
    }

    // =========================================================
    // Update Loop（每帧调用）
    // =========================================================

    /**
     * 更新（每帧调用）
     */
    public void update() {
        // 1️⃣ 检查是否在 LOOK_AT 模式
        GizmoManager gm = GizmoManager.get();
        if (gm == null || !gm.isActive() || gm.getContext() == null) {
            if (dragging) {
                cancelDrag();
            }
            lastLeftMouseDown = false;
            return;
        }

        GizmoMode mode = gm.getContext().getMode();
        if (mode != GizmoMode.LOOK_AT) {
            if (dragging) {
                cancelDrag();
            }
            lastLeftMouseDown = false;
            return;
        }

        var client = MinecraftClient.getInstance();
        if (dragging && (session == null || !isSessionCurrent()
                || (client != null && GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS))) {
            cancelDrag();
        }
        boolean captured = ImGui.getIO().getWantCaptureMouse();
        if (captured && !dragging) {
            lastLeftMouseDown = isLeftMouseDown();
            return;
        }

        // 3️⃣ 检查鼠标左键状态（edge trigger）
        boolean leftDown = isLeftMouseDown();
        boolean isNewPress = leftDown && !lastLeftMouseDown;
        boolean isNewRelease = !leftDown && lastLeftMouseDown;
        lastLeftMouseDown = leftDown;

        // 4️⃣ 处理拖拽生命周期
        if (!dragging) {
            // 未拖拽状态：检查是否开始拖拽
            if (isNewPress && isMouseOverMarker()) {
                beginDrag();
            }
        } else {
            // 拖拽状态：更新或结束
            if (leftDown) {
                if (!captured) updateDrag();
            } else if (isNewRelease) {
                endDrag();
            }
        }
    }

    // =========================================================
    // Drag Lifecycle
    // =========================================================

    /**
     * 开始拖拽
     */
    private void beginDrag() {
        session = ActorLookAtGizmoController.getInstance().beginEdit();
        if (session == null || session.source() == null
                || session.source().getMode() != EntityLookAtClip.TargetMode.WORLD_POINT) {
            cancelDrag();
            return;
        }
        editingClip = session.draft();

        dragging = true;
        LOGGER.debug("[MarkerDragController] 开始拖拽 Marker: clip={}", editingClip.getId());
    }

    /**
     * 更新拖拽
     */
    private void updateDrag() {
        if (!dragging || editingClip == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        Vec3d newPos = bhr.getPos();

        // 更新 LookAtClip 的世界坐标
        editingClip.setWorldPoint(
                (float) newPos.x,
                (float) newPos.y,
                (float) newPos.z
        );

        LOGGER.trace("[MarkerDragController] 更新 Marker 位置: ({}, {}, {})",
                newPos.x, newPos.y, newPos.z);
    }

    /**
     * 结束拖拽
     */
    private void endDrag() {
        if (session != null) {
            var clip = session.commit();
            if (clip != null) ActorLookAtSelectionContext.setActiveLookAtClip(clip);
        }
        session = null; dragging = false; editingClip = null;
    }

    public void cancelDrag() {
        if (session != null) session.cancel();
        session = null; dragging = false; editingClip = null;
    }

    private boolean isSessionCurrent() {
        if (session == null || !session.isCurrent()) return false;
        try {
            return session.model() == DirectorServices.get().containerTimeline().getTimelineModel()
                    && session.actorId().equals(com.director.ui.actor.selection.ActorSelectionHub.getSelectedActorId())
                    && session.owner() == com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(session.model(), session.actorId());
        } catch (IllegalArgumentException unavailable) { return false; }
    }

    public Vec3d previewPoint(java.util.UUID actorId, EntityLookAtClip source) {
        if (!dragging || session == null || !isSessionCurrent()
                || !session.actorId().equals(actorId) || (source != null && session.source() != source)) return null;
        var point = editingClip.getWorldPoint();
        return new Vec3d(point.x, point.y, point.z);
    }

    // =========================================================
    // Hit Test（屏幕空间拾取判定）
    // =========================================================

    /**
     * 检查鼠标是否悬停在 Marker 上
     * <p>使用射线检测：如果鼠标射线与 Marker 位置的距离小于阈值，则认为悬停
     *
     * @return 如果鼠标在 Marker 上则返回 true
     */
    public boolean isMouseOverMarker() {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return false;
        try {
            var owner = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model,
                    com.director.ui.actor.selection.ActorSelectionHub.getSelectedActorId());
            var clip = ActorLookAtEditSession.active(owner.getActorTrackPayload(), model.getCurrentTime());
            if (clip == null || clip.getMode() != EntityLookAtClip.TargetMode.WORLD_POINT) return false;
        } catch (IllegalArgumentException unavailable) { return false; }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return false;
        }

        // 获取 Marker 世界坐标
        Vec3d markerWorld = ActorLookAtVisualizer.getInstance().getLastMarkerWorldPos();
        if (markerWorld == null) {
            return false;
        }

        // 获取鼠标射线（使用 crosshairTarget）
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        Vec3d hitPos = bhr.getPos();

        // 计算 Marker 与射线命中点的距离
        double distance = markerWorld.distanceTo(hitPos);

        // 如果距离小于阈值（0.5 方块），则认为鼠标在 Marker 上
        double threshold = 0.5;
        return distance < threshold;
    }

    /**
     * 检查鼠标左键是否按下
     *
     * @return 如果按下则返回 true
     */
    private boolean isLeftMouseDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        // GLFW_MOUSE_BUTTON_LEFT == 0
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;
    }
}

