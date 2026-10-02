package com.director.ui.actor.lookat;

import com.director.core.DirectorServices;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * ActorLookAtGizmoController v1.0
 *
 * <p>LOOK_AT 模式下：
 * <ul>
 *   <li>点击世界中的点 → 生成 / 修改 LookAtClip（WORLD_POINT）</li>
 *   <li>不干扰 Transform Gizmo</li>
 *   <li>支持两种目标类型：WORLD_POINT 和 CAMERA</li>
 * </ul>
 *
 * <p>导演心智模型：
 * <ul>
 *   <li>"我现在让这个演员，在这一段时间里，看向某个东西"</li>
 *   <li>点击世界位置 → 生成 WORLD_POINT LookAtClip</li>
 *   <li>点击"Look At Camera"按钮 → 生成 CAMERA LookAtClip</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>只在 GizmoMode == LOOK_AT 时工作</li>
 *   <li>独立于 ActorGizmoController（避免上帝类）</li>
 *   <li>完全遵守方案 C：只操作 ActorId、ActorTrack、EntityLookAtClip</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorLookAtGizmoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorLookAtGizmoController.class);
    private static final ActorLookAtGizmoController INSTANCE = new ActorLookAtGizmoController();

    public static ActorLookAtGizmoController getInstance() {
        return INSTANCE;
    }

    /** 当前选中的 Actor */
    private UUID targetActorId;

    /** 上次点击时间（用于防抖） */
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_MS = 200; // 200ms 防抖

    /** 上次鼠标左键状态（用于 edge trigger） */
    private boolean lastLeftMouseDown = false;

    private ActorLookAtGizmoController() {
    }

    // =========================================================
    // Selection API（由 ActorSelectionHub 调用）
    // =========================================================

    /**
     * 设置目标 Actor
     *
     * @param actorId Actor ID，如果为 null 则清除目标
     */
    public void setTargetActor(UUID actorId) {
        if (!Objects.equals(this.targetActorId, actorId)) MarkerDragController.getInstance().cancelDrag();
        this.targetActorId = actorId;
    }

    /**
     * 清除目标
     */
    public void clearTarget() {
        MarkerDragController.getInstance().cancelDrag();
        this.targetActorId = null;
    }

    // =========================================================
    // Update Loop（每帧调用）
    // =========================================================

    /**
     * 更新（每帧调用）
     * <p>只在 LOOK_AT 模式下处理世界点击
     */
    public void update() {
        // 1️⃣ 检查是否在 LOOK_AT 模式
        GizmoManager gm = GizmoManager.get();
        if (gm == null || !gm.isActive() || gm.getContext() == null) {
            lastLeftMouseDown = isLeftMouseDown();
            return;
        }

        GizmoMode mode = gm.getContext().getMode();
        if (mode != GizmoMode.LOOK_AT) {
            lastLeftMouseDown = isLeftMouseDown();
            return;
        }

        // 2️⃣ 检查是否有目标 Actor
        if (targetActorId == null) {
            lastLeftMouseDown = isLeftMouseDown();
            return;
        }

        // 3️⃣ 检查 ImGui 是否占用鼠标
        if (ImGui.getIO().getWantCaptureMouse()) {
            lastLeftMouseDown = isLeftMouseDown();
            return;
        }

        // 4️⃣ 检查鼠标左键点击（edge trigger）
        boolean leftDown = isLeftMouseDown();
        boolean isNewClick = leftDown && !lastLeftMouseDown;
        lastLeftMouseDown = leftDown;

        if (!isNewClick || com.director.ui.actor.pick.ActorPickController.getInstance().consumedActorClick()
                || MarkerDragController.getInstance().isMouseOverMarker()) {
            return;
        }

        // 5️⃣ 防抖
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_MS) {
            return;
        }

        // 6️⃣ 处理点击
        handleClick();
        lastClickTime = currentTime;
    }

    // =========================================================
    // Click Handling
    // =========================================================

    /**
     * 处理点击事件
     */
    private void handleClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        HitResult hit = com.director.ui.actor.pick.ActorViewportRay.blockHit();
        if (hit == null) {
            return;
        }

        // 只处理方块点击（世界点）
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            Vec3d point = bhr.getPos();
            createOrUpdateLookAtClip(point);
            LOGGER.debug("[ActorLookAtGizmoController] 点击世界点创建 LookAtClip: ({}, {}, {})",
                    point.x, point.y, point.z);
        }
    }

    // =========================================================
    // Public API（用于 UI 按钮）
    // =========================================================

    /**
     * 创建 Camera LookAtClip
     * <p>由 UI 按钮调用（例如"Look At Camera"按钮）
     */
    public void createCameraLookAtClip() {
        editTarget(null);
    }

    private void createOrUpdateLookAtClip(Vec3d worldPoint) {
        editTarget(worldPoint);
    }

    ActorLookAtEditSession beginEdit() {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null || targetActorId == null) return null;
        try {
            var owner = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, targetActorId);
            return new ActorLookAtEditSession(model, owner);
        } catch (IllegalArgumentException | NullPointerException failure) {
            LOGGER.debug("Look target edit unavailable: {}", failure.getMessage());
            return null;
        }
    }

    private void editTarget(Vec3d point) {
        MarkerDragController.getInstance().cancelDrag();
        var session = beginEdit();
        if (session == null) return;
        var clip = session.draft();
        clip.setMode(point == null ? EntityLookAtClip.TargetMode.CAMERA : EntityLookAtClip.TargetMode.WORLD_POINT);
        if (point != null) clip.setWorldPoint((float) point.x, (float) point.y, (float) point.z);
        clip.setStrength(1);
        var committed = session.commit();
        if (committed != null) ActorLookAtSelectionContext.setActiveLookAtClip(committed);
    }

    // =========================================================
    // Utils
    // =========================================================

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

