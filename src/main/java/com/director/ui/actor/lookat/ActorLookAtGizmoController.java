package com.director.ui.actor.lookat;

import com.director.core.DirectorServices;
import com.director.actor.ActorRef;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityTimelineClip;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    /** 当前编辑的 ActorTrack（缓存，避免重复查找） */
    private ActorTrack actorTrack;
    private TimelineTrack timelineTrack;

    /** 上次点击时间（用于防抖） */
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_MS = 200; // 200ms 防抖

    /** 上次鼠标左键状态（用于 edge trigger） */
    private boolean lastLeftMouseDown = false;

    /** 默认 LookAtClip 时长（秒） */
    private static final float DEFAULT_CLIP_DURATION = 1.0f;

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
        this.targetActorId = actorId;
        resolveActorTrack();
    }

    /**
     * 清除目标
     */
    public void clearTarget() {
        this.targetActorId = null;
        this.actorTrack = null;
        this.timelineTrack = null;
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
            lastLeftMouseDown = false;
            return;
        }

        GizmoMode mode = gm.getContext().getMode();
        if (mode != GizmoMode.LOOK_AT) {
            lastLeftMouseDown = false;
            return;
        }

        // 2️⃣ 检查是否有目标 Actor
        if (targetActorId == null || actorTrack == null) {
            lastLeftMouseDown = false;
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

        if (!isNewClick) {
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

        HitResult hit = client.crosshairTarget;
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
        if (targetActorId == null || actorTrack == null) {
            LOGGER.warn("[ActorLookAtGizmoController] 无法创建 Camera LookAtClip：未选中 Actor");
            return;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorLookAtGizmoController] TimelineModel 未初始化");
            return;
        }

        float time = model.getCurrentTime();

        // 找到或创建 LookAtClip
        EntityLookAtClip clip = findActiveLookAtClip(time);
        if (clip == null) {
            clip = createLookAtClip(time);
        }

        // 设置为 CAMERA 模式
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(1.0f);

        // 更新选择上下文（供 Drag Controller 使用）
        ActorLookAtSelectionContext.setActiveLookAtClip(clip);

        // 标记模型为 dirty
        model.markDirty();

        LOGGER.debug("[ActorLookAtGizmoController] 创建 Camera LookAtClip: clip={}, time={}",
                clip.getId(), time);
    }

    // =========================================================
    // LookAt Clip Logic
    // =========================================================

    /**
     * 创建或更新 LookAtClip
     *
     * @param worldPoint 世界坐标点
     */
    private void createOrUpdateLookAtClip(Vec3d worldPoint) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        float time = model.getCurrentTime();

        // 找到或创建 LookAtClip
        EntityLookAtClip clip = findActiveLookAtClip(time);
        if (clip == null) {
            clip = createLookAtClip(time);
        }

        // 设置目标
        clip.setMode(EntityLookAtClip.TargetMode.WORLD_POINT);
        clip.setWorldPoint(
                (float) worldPoint.x,
                (float) worldPoint.y,
                (float) worldPoint.z
        );
        clip.setStrength(1.0f);

        // 更新选择上下文（供 Drag Controller 使用）
        ActorLookAtSelectionContext.setActiveLookAtClip(clip);

        // 标记模型为 dirty
        model.markDirty();
    }

    /**
     * 找到覆盖指定时间的 LookAtClip（last-wins 规则）
     *
     * @param time 时间（秒）
     * @return LookAtClip，如果未找到则返回 null
     */
    private EntityLookAtClip findActiveLookAtClip(float time) {
        if (actorTrack == null) {
            return null;
        }

        List<EntityClip> clips = actorTrack.getClips();
        EntityLookAtClip result = null;

        for (EntityClip c : clips) {
            if (c instanceof EntityLookAtClip lc && !lc.isMuted()) {
                if (time >= lc.getStartTime() && time <= lc.getEndTime()) {
                    if (result == null || lc.getStartTime() >= result.getStartTime()) {
                        result = lc;
                    }
                }
            }
        }

        return result;
    }

    /**
     * 创建新的 LookAtClip
     *
     * @param time 开始时间（秒）
     * @return 新创建的 LookAtClip
     */
    private EntityLookAtClip createLookAtClip(float time) {

        EntityLookAtClip clip = new EntityLookAtClip(
                "LookAt",
                time,
                time + DEFAULT_CLIP_DURATION
        );

        // 默认模式为 CAMERA，strength 为 1.0
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(1.0f);

        // 添加到轨道
        actorTrack.addClip(clip);
        if (timelineTrack != null) {
            EntityTimelineClip timelineClip = new EntityTimelineClip(clip, actorTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);
        }

        // 标记模型为 dirty
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }

        return clip;
    }

    // =========================================================
    // Resolve Track
    // =========================================================

    /**
     * 解析目标 Track（缓存，避免重复查找）
     */
    private void resolveActorTrack() {
        this.actorTrack = null;
        this.timelineTrack = null;

        if (targetActorId == null) {
            return;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) {
                continue;
            }

            ActorTrack et = track.getActorTrackPayload();
            if (et == null) {
                continue;
            }

            ActorRef ref = et.getActorRef();
            if (ref != null && Objects.equals(ref.getActorId(), targetActorId)) {
                this.actorTrack = et;
                this.timelineTrack = track;
                return;
            }
        }
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

