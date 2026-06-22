package com.director.ui.actor.placement;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.preview.ActorPreviewManager;
import net.minecraft.entity.Entity;
import com.director.client.input.DirectorInputSystem;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.playback.camera.CameraAccessor;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActorPlacementController v1.0
 *
 * <p>Actor 放置控制器：处理 Actor 的鼠标附着预览和右键放置
 *
 * <p>职责：
 * <ul>
 *   <li>✅ 在鼠标位置显示 Actor 预览（创建时）</li>
 *   <li>✅ 监听右键点击放置 Actor</li>
 *   <li>✅ 创建 ActorTrack 和 EntitySpawnClip</li>
 *   <li>❌ 不直接操作 TimelineModel（通过命令系统）</li>
 * </ul>
 *
 * <p>工作流程：
 * <ol>
 *   <li>用户在 UI 中点击"增加演员"</li>
 *   <li>系统创建临时 Actor 并附着到鼠标</li>
 *   <li>用户右键点击世界中的位置</li>
 *   <li>系统创建 ActorTrack 和 EntitySpawnClip</li>
 *   <li>如果用户取消（ESC），则清理临时 Actor</li>
 * </ol>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorPlacementController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPlacementController.class);
    private static final ActorPlacementController INSTANCE = new ActorPlacementController();

    public static ActorPlacementController getInstance() {
        return INSTANCE;
    }

    /** 是否正在放置模式 */
    private boolean isPlacing = false;

    /** 临时 Actor（正在放置的） */
    private Actor pendingActor = null;

    /** 临时 PreviewEntity（附着到鼠标的） */
    private Entity previewEntity = null;

    /** Last right-click state for edge trigger. */
    private boolean lastRightMouseDown = false;

    /** 默认片段长度（秒） */
    private static final float DEFAULT_CLIP_DURATION = 5.0f;

    private ActorPlacementController() {
    }

    // =========================================================
    // Public API
    // =========================================================

    /**
     * 开始放置 Actor
     * <p>创建临时 Actor 并附着到鼠标
     *
     * @param actorName Actor 名称
     * @param actorType Actor 类型
     * @return 如果成功开始放置则返回 true
     */
    public boolean startPlacement(String actorName, ActorType actorType) {
        if (isPlacing) {
            LOGGER.warn("[ActorPlacement] 已经在放置模式，取消之前的放置");
            cancelPlacement();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[ActorPlacement] MinecraftClient 未初始化");
            return false;
        }

        try {
            // 1. 创建临时 Actor（不保存到 Registry，只是预览）
            pendingActor = new Actor(actorName.trim(), actorType);
            
            // 2. 使用 ActorPreviewManager 创建临时 PreviewEntity（避免渲染系统类型错误）
            ActorPreviewManager previewManager = ActorPreviewManager.getInstance();
            
            previewEntity = previewManager.ensurePreview(pendingActor);
            if (previewEntity == null) {
                LOGGER.warn("[ActorPlacement] 无法创建预览实体");
                return false;
            }

            isPlacing = true;
            lastRightMouseDown = false;
            LOGGER.info("[ActorPlacement] 开始放置 Actor: {} ({})", actorName, actorType);
            return true;

        } catch (Throwable e) {
            LOGGER.error("[ActorPlacement] 开始放置失败", e);
            cancelPlacement();
            return false;
        }
    }

    /**
     * 取消放置
     * <p>清理临时 Actor 和 PreviewEntity
     */
    public void cancelPlacement() {
        if (previewEntity != null && pendingActor != null) {
            // 使用 ActorPreviewManager 清理（确保正确清理映射）
            ActorPreviewManager previewManager = ActorPreviewManager.getInstance();
            previewManager.removePreview(pendingActor.getActorId());
            previewEntity = null;
        }

        pendingActor = null;
        isPlacing = false;
        lastRightMouseDown = false;
        LOGGER.debug("[ActorPlacement] 取消放置");
    }

    /**
     * 检查是否正在放置
     *
     * @return 如果正在放置则返回 true
     */
    public boolean isPlacing() {
        return isPlacing;
    }

    /**
     * 更新（每帧调用）
     * <p>更新鼠标附着位置，监听右键点击
     */
    public void update() {
        if (!isPlacing || previewEntity == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }

        // 1. Update preview position from mouse raycast.
        updatePreviewPosition(client);

        // 2. Cancel on ESC.
        if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            cancelPlacement();
            return;
        }

        // 3. Check right-click placement.
        checkRightClick(client);
    }

    // =========================================================
    // Private Methods
    // =========================================================

    private boolean isRightMouseDown(MinecraftClient client) {
        return GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
        ) == GLFW.GLFW_PRESS;
    }

    private boolean isMouseOverUi() {
        return ImGui.isAnyItemHovered() || ImGui.isAnyItemActive();
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

    /**
     * Update preview position based on mouse raycast.
     */
    private void updatePreviewPosition(MinecraftClient client) {
        if (previewEntity == null) {
            return;
        }

        // Sample camera state.
        var cameraSample = CameraAccessor.sampleFromGameCamera(client);
        if (cameraSample == null) {
            return;
        }

        Vec3d previewPos;
        BlockHitResult blockHit = resolvePlacementHit(client);
        if (blockHit != null) {
            previewPos = blockHit.getPos();
        } else {
            Vec3d cameraPos = cameraSample.position();
            Vec3d cameraDir = getCameraDirection(cameraSample.yaw(), cameraSample.pitch());

            previewPos = cameraPos.add(cameraDir.multiply(3.0));

            if (client.world != null) {
                double groundY = client.world.getTopY(
                        net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                        (int) previewPos.x,
                        (int) previewPos.z
                );
                if (previewPos.y < groundY + 1.0) {
                    previewPos = new Vec3d(previewPos.x, groundY + 1.0, previewPos.z);
                }
            }
        }
        previewEntity.refreshPositionAndAngles(
                previewPos.x,
                previewPos.y,
                previewPos.z,
                cameraSample.yaw(),
                cameraSample.pitch()
        );
    }

    /**
     * Build camera forward direction.
     */
    private Vec3d getCameraDirection(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3d(x, y, z).normalize();
    }

    /**
     * Handle right-click placement.
     */
    private void checkRightClick(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }

        boolean rightDown = isRightMouseDown(client);
        if (isMouseOverUi()) {
            lastRightMouseDown = rightDown;
            return;
        }

        if (rightDown && !lastRightMouseDown) {
            BlockHitResult blockHit = resolvePlacementHit(client);
            if (blockHit != null) {
                placeActorAt(blockHit.getPos());
            }
        }

        lastRightMouseDown = rightDown;
    }

    private void placeActorAt(Vec3d position) {
        if (pendingActor == null || previewEntity == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        TimelineModel timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
        
        if (timelineModel == null) {
            LOGGER.warn("[ActorPlacement] ActorRegistry 或 TimelineModel 未初始化");
            cancelPlacement();
            return;
        }

        try {
            // 0. 生成唯一的名称
            String uniqueName = generateUniqueActorName(pendingActor.getName(), actorRegistry, timelineModel);

            // 1. 正式创建 Actor（保存到 Registry）
            Actor actor = actorRegistry.createActor(uniqueName, pendingActor.getType());
            ActorRef ref = new ActorRef(actor.getActorId());

            // 2. 创建 ActorTrack
            ActorTrack actorTrack = new ActorTrack();
            actorTrack.setActorRef(ref);
            actorTrack.setDisplayName(uniqueName);

            // 3. 创建 TimelineTrack
            TimelineTrack timelineTrack = new TimelineTrack(uniqueName, TrackType.ACTOR);
            timelineTrack.setActorTrackPayload(actorTrack);

            // 4. 添加到 TimelineModel
            timelineModel.addTrack(timelineTrack);

            // 5. 创建 EntitySpawnClip（默认 5 秒，可调整）
            float currentTime = timelineModel.getCurrentTime();
            EntitySpawnClip spawnClip = new EntitySpawnClip(
                    "Spawn",
                    currentTime,
                    currentTime + DEFAULT_CLIP_DURATION
            );
            spawnClip.setPosition(
                    (float) position.x,
                    (float) position.y,
                    (float) position.z
            );
            
            // 使用摄像机朝向
            var cameraSample = CameraAccessor.sampleFromGameCamera(client);
            if (cameraSample != null) {
                spawnClip.setYawDeg(cameraSample.yaw());
                spawnClip.setPitchDeg(cameraSample.pitch());
            }

            spawnClip.setVisibleOnSpawn(true);
            actorTrack.addClip(spawnClip);
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, actorTrack.getColorRgba());
            timelineModel.addClip(timelineTrack, timelineClip);

            // 6. 标记为 dirty（触发保存）
            timelineModel.markDirty();

            // 7. 清理临时预览实体（使用 ActorPreviewManager）
            ActorPreviewManager previewManager = ActorPreviewManager.getInstance();
            previewManager.removePreview(pendingActor.getActorId());
            previewEntity = null;
            pendingActor = null;
            isPlacing = false;

            LOGGER.info("[ActorPlacement] 成功放置 Actor: {} 在位置 ({}, {}, {})",
                    actor.getName(), position.x, position.y, position.z);

        } catch (Throwable e) {
            LOGGER.error("[ActorPlacement] 放置 Actor 失败", e);
            cancelPlacement();
        }
    }

    /**
     * 生成唯一的演员名称（检查演员和轨道名称）
     *
     * @param baseName 基础名称
     * @param actorRegistry ActorRegistry
     * @param timelineModel TimelineModel
     * @return 唯一的名称
     */
    private String generateUniqueActorName(String baseName, ActorRegistry actorRegistry, TimelineModel timelineModel) {
        if (baseName == null || baseName.isBlank()) {
            baseName = "Actor";
        }
        String trimmed = baseName.trim();
        String candidate = trimmed;
        int index = 1;

        // 检查名称是否已存在（检查演员和轨道）
        while (isNameTaken(candidate, actorRegistry, timelineModel)) {
            candidate = trimmed + index;
            index++;
        }

        return candidate;
    }

    /**
     * 检查名称是否已被使用（检查演员和轨道）
     *
     * @param name 名称
     * @param actorRegistry ActorRegistry
     * @param timelineModel TimelineModel
     * @return 如果名称已被使用则返回 true
     */
    private boolean isNameTaken(String name, ActorRegistry actorRegistry, TimelineModel timelineModel) {
        // 检查演员名称
        if (actorRegistry.hasActorWithName(name)) {
            return true;
        }

        // 检查轨道名称（特别是演员轨道）
        if (timelineModel != null) {
            for (TimelineTrack track : timelineModel.getTracks()) {
                if (track != null && name.equals(track.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

}

