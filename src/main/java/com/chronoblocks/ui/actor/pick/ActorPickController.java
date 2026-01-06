package com.chronoblocks.ui.actor.pick;

import com.chronoblocks.actor.preview.ActorPreviewManager;
import com.chronoblocks.actor.preview.PreviewActorEntity;
import com.chronoblocks.ui.actor.selection.ActorSelectionHub;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * ActorPickController v1.0
 *
 * <p>世界点击 → 选中演员
 * <p>职责：监听鼠标点击，raycast 到实体，如果点中 PreviewActorEntity 就选中它，并同步 Timeline。
 *
 * <p>使用方式：
 * <ul>
 *   <li>在每帧/tick 调用 update()</li>
 *   <li>检查鼠标点击和交叉目标</li>
 *   <li>如果点中 PreviewActorEntity，调用 ActorSelectionHub.selectActor()</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorPickController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPickController.class);
    private static final ActorPickController INSTANCE = new ActorPickController();

    public static ActorPickController getInstance() {
        return INSTANCE;
    }

    /** 是否启用点击检测 */
    private boolean enabled = true;

    /** 上次点击时间（用于防抖） */
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_MS = 100; // 100ms 防抖

    /** 上次鼠标状态（用于检测点击事件） */
    private boolean lastLeftMouseDown = false;

    private ActorPickController() {
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查是否启用
     *
     * @return 如果启用则返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 更新（每帧调用）
     * <p>检查鼠标点击，如果点中 PreviewActorEntity 则选中它
     */
    public void update() {
        if (!enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        // 检查 ImGui 是否捕获了鼠标（如果 UI 正在交互，不处理世界点击）
        if (ImGui.getIO().getWantCaptureMouse()) {
            return;
        }

        // 检查鼠标左键点击（使用 GLFW 直接检测，避免与 Minecraft 输入系统冲突）
        boolean leftMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), 
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        // 检查是否是新的点击（不是持续按住）
        boolean isNewClick = leftMouseDown && !lastLeftMouseDown;
        lastLeftMouseDown = leftMouseDown;

        if (!isNewClick) {
            return;
        }

        // 防抖：避免重复点击
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_MS) {
            return;
        }

        // 获取交叉目标
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHit = (EntityHitResult) hitResult;
        Entity entity = entityHit.getEntity();

        // 检查是否是 PreviewActorEntity
        if (!(entity instanceof PreviewActorEntity)) {
            return;
        }

        PreviewActorEntity previewEntity = (PreviewActorEntity) entity;

        // 反查 Actor ID
        UUID actorId = ActorPreviewManager.getInstance().getActorId(previewEntity);
        if (actorId == null) {
            LOGGER.warn("[ActorPickController] 无法找到 PreviewEntity 对应的 Actor ID");
            return;
        }

        // 选中 Actor
        ActorSelectionHub.selectActor(actorId);
        lastClickTime = currentTime;

        LOGGER.debug("[ActorPickController] 点击选中 Actor: {}", actorId);
    }
}

