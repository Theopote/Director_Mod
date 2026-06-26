package com.director.ui.actor.pick;

import com.director.actor.Actor;
import com.director.actor.preview.ActorPreviewManager;
import com.director.client.input.DirectorInputSystem;
import com.director.ui.actor.placement.ActorPlacementController;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.tools.ToolRegistry;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;

/**
 * ActorPickController v2.0
 *
 * <p>职责：
 * <ul>
 *   <li>监听鼠标点击</li>
 *   <li>Raycast 到世界</li>
 *   <li>如果点中 PreviewActorEntity：
 *       → 解析 actorId
 *       → 通知 ActorSelectionHub</li>
 *   <li>如果正在放置模式（PLACE）：
 *       → 监听右键点击
 *       → 在世界中放置 Actor</li>
 * </ul>
 *
 * <p>⚠️ 设计原则：
 * <ul>
 *   <li>不直接操作 TimelineModel</li>
 *   <li>不直接操作 Gizmo</li>
 *   <li>不维护 selection 状态</li>
 * </ul>
 *
 * <p>这是一个"输入适配器"，不是状态管理器。
 *
 * @author Director Team
 * @version 2.0
 */
public final class ActorPickController {

    private static final ActorPickController INSTANCE = new ActorPickController();

    public static ActorPickController getInstance() {
        return INSTANCE;
    }

    /** 上一帧鼠标左键状态，用于 edge-trigger */
    private boolean lastLeftMouseDown = false;

    /** 上一帧鼠标右键状态，用于 edge-trigger（放置模式） */
    private boolean lastRightMouseDown = false;

    private ActorPickController() {
    }

    // =========================================================
    // Update（每帧调用）
    // =========================================================

    /**
     * 更新（每帧调用）
     * <p>检查鼠标点击：
     * <ul>
     *   <li>如果点中 PreviewActorEntity 则选中它</li>
     *   <li>如果正在放置模式，监听右键点击放置</li>
     * </ul>
     */
    public void update() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            lastLeftMouseDown = false;
            lastRightMouseDown = false;
            return;
        }

        // 检查是否正在放置模式
        if (ActorSelectionHub.isPlacingActor()) {
            handlePlacementMode(client);
            return;
        }

        // ImGui 正在使用鼠标（拖 UI / Gizmo / Timeline），不要抢输入
        if (ImGui.getIO().getWantCaptureMouse()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }
        if (com.director.client.gizmo.GizmoManager.get().isHot()
                || com.director.client.gizmo.GizmoManager.get().isDraggingHandle()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }
        if (!ToolRegistry.isActorToolId(ToolRegistry.get().getActiveNonSelectionToolId())) {
            lastLeftMouseDown = isLeftMouseDown(client);
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        boolean leftDown = isLeftMouseDown(client);

        // 只在"刚按下"的那一帧处理（edge trigger）
        if (leftDown && !lastLeftMouseDown) {
            handleClick(client);
        }

        lastLeftMouseDown = leftDown;
        lastRightMouseDown = isRightMouseDown(client);
    }

    // =========================================================
    // Click Handling
    // =========================================================

    /**
     * 处理点击事件
     *
     * @param client MinecraftClient
     */
    private void handleClick(MinecraftClient client) {
        Entity entity = resolveClickedEntity(client);
        if (entity == null) {
            return;
        }

        ActorPreviewManager previewManager = ActorPreviewManager.getInstance();
        UUID actorId = previewManager.getActorId(entity);
        if (actorId == null) {
            return;
        }

        // 🔥 统一入口：ActorSelectionHub
        ActorSelectionHub.selectActor(actorId);
    }

    private Entity resolveClickedEntity(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult ehr) {
            Entity entity = ehr.getEntity();
            if (ActorPreviewManager.getInstance().getActorId(entity) != null) {
                return entity;
            }
        }

        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
        if (ray == null) {
            return null;
        }

        Vec3d start = ray.origin();
        Vec3d end = start.add(ray.direction().multiply(128.0));

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity entity : ActorPreviewManager.getInstance().getPreviewEntities()) {
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            Box box = entity.getBoundingBox().expand(0.25);
            Optional<Vec3d> hitPos = box.raycast(start, end);
            if (hitPos.isPresent()) {
                double dist = start.squaredDistanceTo(hitPos.get());
                if (dist < closestDist) {
                    closest = entity;
                    closestDist = dist;
                }
            }
        }

        return closest;
    }

    // =========================================================
    // Placement Mode Handling (v2.0 新增)
    // =========================================================

    /**
     * 处理放置模式
     * <p>监听右键点击，在世界中放置 Actor
     *
     * @param client MinecraftClient
     */
    private void handlePlacementMode(MinecraftClient client) {
        // ImGui 正在使用鼠标，不要抢输入
        if (ImGui.getIO().getWantCaptureMouse()) {
            lastRightMouseDown = isRightMouseDown(client);
            return;
        }

        boolean rightDown = isRightMouseDown(client);

        // 只在"刚按下"的那一帧处理（edge trigger）
        if (rightDown && !lastRightMouseDown) {
            Actor pendingActor = ActorSelectionHub.getPendingActor();
            if (pendingActor != null) {
                // 解析点击位置
                BlockHitResult blockHit = resolvePlacementHit(client);
                if (blockHit != null) {
                    // 使用 ActorPlacementController 处理放置逻辑
                    ActorPlacementController placementController = ActorPlacementController.getInstance();
                    // 先启动放置（创建临时 Actor 和 PreviewEntity）
                    if (placementController.startPlacement(pendingActor.getName(), pendingActor.getType())) {
                        // 通过反射调用 placeActorAt（因为它是 private）
                        // TODO: 未来可以将 placeActorAt 改为 public，或提供一个公共方法
                        try {
                            java.lang.reflect.Method placeMethod = ActorPlacementController.class.getDeclaredMethod("placeActorAt", Vec3d.class);
                            placeMethod.setAccessible(true);
                            placeMethod.invoke(placementController, blockHit.getPos());
                        } catch (Exception e) {
                            org.slf4j.LoggerFactory.getLogger(ActorPickController.class)
                                .error("[ActorPickController] 无法调用 placeActorAt", e);
                        }
                        // 退出放置模式
                        ActorSelectionHub.cancelPlace();
                    }
                }
            }
        }

        lastRightMouseDown = rightDown;
    }

    /**
     * 解析放置点击位置
     *
     * @param client MinecraftClient
     * @return BlockHitResult，如果未点击到方块则返回 null
     */
    private BlockHitResult resolvePlacementHit(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit) {
            return blockHit;
        }

        // 如果没有直接命中，尝试从摄像机射线计算
        DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();

        // 简单的射线检测（可以改进为更精确的方块检测）
        // TODO: 实现更精确的方块射线检测
        // 目前使用简单的距离估算
        return null;
    }

    // =========================================================
    // Utils
    // =========================================================

    /**
     * 检查鼠标左键是否按下
     *
     * @param client MinecraftClient
     * @return 如果按下则返回 true
     */
    private boolean isLeftMouseDown(MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        // GLFW_MOUSE_BUTTON_LEFT == 0
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;
    }

    /**
     * 检查鼠标右键是否按下
     *
     * @param client MinecraftClient
     * @return 如果按下则返回 true
     */
    private boolean isRightMouseDown(MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        // GLFW_MOUSE_BUTTON_RIGHT == 1
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
        ) == GLFW.GLFW_PRESS;
    }
}
