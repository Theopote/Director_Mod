package com.director.ui.actor.lookat;

import com.director.actor.visualizer.ActorLookAtVisualizer;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.entity.EntityLookAtClip;
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
 *   <li>拖拽 Marker 时实时修改 EntityLookAtClip.worldPoint</li>
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
                endDrag();
            }
            lastLeftMouseDown = false;
            return;
        }

        GizmoMode mode = gm.getContext().getMode();
        if (mode != GizmoMode.LOOK_AT) {
            if (dragging) {
                endDrag();
            }
            lastLeftMouseDown = false;
            return;
        }

        // 2️⃣ 检查 ImGui 是否占用鼠标
        if (ImGui.getIO().getWantCaptureMouse()) {
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
                updateDrag();
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
        editingClip = ActorLookAtSelectionContext.getActiveLookAtClip();
        if (editingClip == null) {
            return;
        }

        // 只允许拖拽 WORLD_POINT 模式的 Clip
        if (editingClip.getMode() != EntityLookAtClip.TargetMode.WORLD_POINT) {
            return;
        }

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

        // 标记时间线为 dirty
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }

        LOGGER.trace("[MarkerDragController] 更新 Marker 位置: ({}, {}, {})",
                newPos.x, newPos.y, newPos.z);
    }

    /**
     * 结束拖拽
     */
    private void endDrag() {
        if (editingClip != null) {
            LOGGER.debug("[MarkerDragController] 结束拖拽 Marker: clip={}, worldPoint=({}, {}, {})",
                    editingClip.getId(),
                    editingClip.getWorldPoint().x,
                    editingClip.getWorldPoint().y,
                    editingClip.getWorldPoint().z);
        }

        dragging = false;
        editingClip = null;
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
    private boolean isMouseOverMarker() {
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

