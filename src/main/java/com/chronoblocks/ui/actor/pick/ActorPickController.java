package com.chronoblocks.ui.actor.pick;

import com.chronoblocks.actor.preview.ActorPreviewManager;
import com.chronoblocks.actor.preview.PreviewActorEntity;
import com.chronoblocks.ui.actor.selection.ActorSelectionHub;
import com.chronoblocks.ui.tools.ToolRegistry;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * ActorPickController v1.0
 *
 * <p>职责：
 * <ul>
 *   <li>监听鼠标点击</li>
 *   <li>Raycast 到世界</li>
 *   <li>如果点中 PreviewActorEntity：
 *       → 解析 actorId
 *       → 通知 ActorSelectionHub</li>
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
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorPickController {

    private static final ActorPickController INSTANCE = new ActorPickController();

    public static ActorPickController getInstance() {
        return INSTANCE;
    }

    /** 上一帧鼠标左键状态，用于 edge-trigger */
    private boolean lastLeftMouseDown = false;

    private ActorPickController() {
    }

    // =========================================================
    // Update（每帧调用）
    // =========================================================

    /**
     * 更新（每帧调用）
     * <p>检查鼠标点击，如果点中 PreviewActorEntity 则选中它
     */
    public void update() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            lastLeftMouseDown = false;
            return;
        }

        // ImGui 正在使用鼠标（拖 UI / Gizmo / Timeline），不要抢输入
        if (ImGui.getIO().getWantCaptureMouse()) {
            lastLeftMouseDown = isLeftMouseDown(client);
            return;
        }
        if (!ToolRegistry.ACTOR_SELECT_TOOL_ID.equals(ToolRegistry.get().getActiveNonSelectionToolId())) {
            lastLeftMouseDown = isLeftMouseDown(client);
            return;
        }


        boolean leftDown = isLeftMouseDown(client);

        // 只在"刚按下"的那一帧处理（edge trigger）
        if (leftDown && !lastLeftMouseDown) {
            handleClick(client);
        }

        lastLeftMouseDown = leftDown;
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
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult ehr = (EntityHitResult) hit;
        Entity entity = ehr.getEntity();

        if (!(entity instanceof PreviewActorEntity preview)) {
            return;
        }

        // 从 PreviewActorEntity 反查 actorId
        ActorPreviewManager previewManager = ActorPreviewManager.getInstance();

        UUID actorId = previewManager.getActorId(preview);
        if (actorId == null) {
            return;
        }

        // 🔥 统一入口：ActorSelectionHub
        ActorSelectionHub.selectActor(actorId);
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
}
