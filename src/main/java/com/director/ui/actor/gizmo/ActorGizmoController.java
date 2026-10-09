package com.director.ui.actor.gizmo;

import com.director.core.DirectorServices;
import com.director.actor.preview.ActorPreviewManager;
import net.minecraft.entity.Entity;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.client.gizmo.GizmoType;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityTransformClip;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * ActorGizmoController v2.0
 *
 * <p>Step 4B+ 核心：支持三种 Gizmo 模式
 * <ul>
 *   <li>TRANSLATE - 编辑位置 (fromPos/toPos)</li>
 *   <li>ROTATE - 编辑朝向 (fromRot/toRot)</li>
 *   <li>SCALE - 编辑缩放 (fromScale/toScale)</li>
 * </ul>
 *
 * <p>导演心智模型：
 * <ul>
 *   <li>"我现在用的是什么 Gizmo，我就在时间线上编辑什么属性"</li>
 *   <li>不混合、不猜测、不魔法</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不渲染 Gizmo（GizmoManager 已存在）</li>
 *   <li>不处理选中逻辑（ActorSelectionHub 已处理）</li>
 *   <li>只负责"编辑 TransformClip"</li>
 *   <li>完全遵守方案 C：只操作 ActorId、ActorTrack、EntityTransformClip</li>
 * </ul>
 *
 * @author Director Team
 * @version 2.0 - 支持三种 Gizmo 模式
 */
public final class ActorGizmoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGizmoController.class);
    private static final ActorGizmoController INSTANCE = new ActorGizmoController();

    public static ActorGizmoController getInstance() {
        return INSTANCE;
    }

    /** 当前选中的 Actor */
    private UUID targetActorId;

    /** 当前编辑的 ActorTrack（缓存，避免重复查找） */
    private ActorTrack actorTrack;
    private TimelineTrack timelineTrack;

    /** 当前 Gizmo 模式 */
    private GizmoMode currentMode = GizmoMode.TRANSLATE;

    /** Gizmo 编辑状态 */
    private EntityTransformClip editingClip;
    private boolean dragging = false;
    private ActorTransformEditSession dragSession;
    private GizmoMode dragMode;
    /** 是否由 ActorGizmoController 激活并持有当前 Gizmo。 */
    private boolean ownsGizmo = false;

    /** Drag 基础值（按模式区分） */
    private Vec3d dragBasePos;      // TRANSLATE 模式
    private Vec3d dragBaseRot;       // ROTATE 模式
    private Vec3d dragBaseScale;     // SCALE 模式
    private Vec3d dragPreviewPosition;
    private Vec3d dragPreviewRotation;

    /** Gizmo 位置缓存（用于优化更新频率） */
    private Box lastGizmoBox = null;
    private Vec3d lastGizmoPivot = null;
    private static final double BOX_CHANGE_THRESHOLD_SQ = 0.0001; // 0.01 blocks

    /** 默认 TransformClip 时长（秒） */
    private static final float DEFAULT_CLIP_DURATION = 1.0f;

    /** Scale 的最小值和最大值 */
    private static final double MIN_SCALE = 0.01;
    private static final double MAX_SCALE = 100.0;

    private ActorGizmoController() {
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
        if (Objects.equals(targetActorId, actorId)) return;
        if (dragging) cancelGizmoDrag();
        // ⭐ 清除方块选区（Gizmo 激活前）
        // Actor Gizmo 不应该与方块选区有任何关联
        try {
            com.director.core.selection.SelectionManager blockSelectionManager = 
                DirectorServices.get().selection();
            if (blockSelectionManager != null) {
                blockSelectionManager.clearSelection();
            }
        } catch (Exception e) {
            LOGGER.debug("[ActorGizmoController] 清除方块选区失败: {}", e.getMessage());
        }
        
        this.targetActorId = actorId;
        this.dragging = false;
        this.editingClip = null;
        this.ownsGizmo = false;
        clearDragBases();
        
        // 重置位置缓存，强制下次更新时重新计算
        lastGizmoBox = null;
        lastGizmoPivot = null;

        resolveTargetTrack();
    }

    /**
     * 清除目标
     */
    public void clearTarget() {
        if (dragging) cancelGizmoDrag();
        this.targetActorId = null;
        this.actorTrack = null;
        this.timelineTrack = null;
        this.dragging = false;
        this.editingClip = null;
        clearDragBases();

        // 在停用 Gizmo 之前，先清除方块选区
        // 因为 deactivate() 会调用 apply()，会将 Gizmo 的 Box 转换为方块选区
        try {
            com.director.core.selection.SelectionManager blockSelectionManager = 
                    DirectorServices.get().selection();
            if (blockSelectionManager != null) {
                blockSelectionManager.clearSelection();
            }
        } catch (Exception e) {
            LOGGER.debug("[ActorGizmoController] 清除方块选区失败（可能不在客户端）: {}", e.getMessage());
        }

        deactivateActorTransformGizmoIfPresent();
        ownsGizmo = false;
    }

    /**
     * 获取目标 Actor ID（用于外部查询）
     *
     * @return Actor ID，如果未设置则返回 null
     */
    public UUID getTargetActorId() {
        return targetActorId;
    }

    /**
     * 检查是否正在编辑
     *
     * @return 如果正在编辑则返回 true
     */
    public boolean isEditing() {
        return editingClip != null;
    }

    /**
     * 是否正在拖动 Gizmo 编辑该演员（供 Runtime 避免覆盖实时预览）。
     */
    public boolean isDragging() {
        return dragging;
    }

    public GizmoMode getPreferredMode() { return currentMode; }

    public void setPreferredMode(GizmoMode mode) {
        var nextMode = mode != null ? mode : GizmoMode.TRANSLATE;
        if (dragging && currentMode != nextMode) cancelGizmoDrag();
        this.currentMode = nextMode;
        if (GizmoManager.get().getContext() != null) {
            GizmoManager.get().getContext().setMode(this.currentMode);
        }
    }

    // =========================================================
    // Update Loop（每帧调用）
    // =========================================================

    /**
     * 更新（每帧调用）
     * <p>需要在客户端每帧调用（和 ActorPickController 类似）
     */
    public void update() {
        if (dragging) {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            boolean escape = client != null && client.getWindow() != null
                    && org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (escape || !isDragContextCurrent()) cancelGizmoDrag();
        }
        if (targetActorId == null) {
            // 只停用自己持有的 Gizmo，避免干扰 Camera Path 等其他工具。
            if (ownsGizmo && GizmoManager.get().isActive()) {
                GizmoManager.get().deactivate();
            }
            ownsGizmo = false;
            return;
        }

        var model=resolveTimelineModel();
        resolveTargetTrack();
        if(model==null || model.isPlaying() || model.isScrubbing() || timelineTrack==null
                || timelineTrack.isLocked() || timelineTrack.isMuted()) {
            cancelGizmoDrag();
            deactivateActorTransformGizmoIfPresent();
            ownsGizmo=false;
            return;
        }

        Entity preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (preview == null) {
            cancelGizmoDrag();
            // 只停用自己持有的 Gizmo，避免干扰 Camera Path 等其他工具。
            if (ownsGizmo && GizmoManager.get().isActive()) {
                GizmoManager.get().deactivate();
            }
            ownsGizmo = false;
            return;
        }

        // 1️⃣ 更新 Gizmo 位置（跟随实体）；模式由 setPreferredMode / 工具切换驱动，不从 Gizmo 上下文反向覆盖
        updateGizmoPosition(preview);
    }

    /**
     * @deprecated 模式由工具/快捷键 {@link #setPreferredMode} 驱动，不再从 Gizmo 上下文反向读取
     */
    @Deprecated
    private void updateCurrentMode() {
        GizmoManager gm = GizmoManager.get();
        if (gm != null && gm.isActive() && gm.getContext() != null) {
            GizmoMode mode = gm.getContext().getMode();
            if (mode != null) {
                currentMode = mode;
            }
        }
    }

    // =========================================================
    // Gizmo Position Update
    // =========================================================

    /**
     * 更新 Gizmo 位置（跟随实体）
     * <p>优化：只在 Box 或 Pivot 真正变化时更新，拖动时不更新
     */
    private void updateGizmoPosition(Entity entity) {
        // ⭐ 拖动时不要更新位置，避免干扰拖动操作
        if (dragging) {
            return;
        }

        if (actorTrack == null) {
            resolveTargetTrack();
        }

        Box box = entity.getBoundingBox();
        if (box == null) {
            return;
        }

        Vec3d pivot = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        GizmoManager gm = GizmoManager.get();

        boolean needsFullActivate = !gm.isActive() || gm.getType() != GizmoType.TRANSFORM;

        if (needsFullActivate) {
            if (gm.isActive()) {
                gm.deactivate();
            }
            gm.activate(GizmoType.TRANSFORM, box, pivot);
            ownsGizmo = true;
            if (gm.getContext() != null) {
                gm.getContext().setMode(currentMode);
            }
            configureActorTransformGizmo(gm);
            lastGizmoBox = box;
            lastGizmoPivot = pivot;
            LOGGER.debug("[ActorGizmoController] 已激活 Actor Transform Gizmo: actor={}, mode={}",
                    targetActorId, currentMode);
            return;
        }

        ownsGizmo = true;
        ensureActorTransformApplier(gm);

        boolean boxChanged = lastGizmoBox == null || !boxesEqual(box, lastGizmoBox);
        boolean pivotChanged = lastGizmoPivot == null
                || lastGizmoPivot.squaredDistanceTo(pivot) > BOX_CHANGE_THRESHOLD_SQ;

        if (boxChanged) {
            gm.setTargetBox(box);
            lastGizmoBox = box;
        }
        if (pivotChanged) {
            gm.setPivot(pivot);
            lastGizmoPivot = pivot;
        }

        configureActorTransformGizmo(gm);
    }

    private void configureActorTransformGizmo(GizmoManager gm) {
        ensureActorTransformApplier(gm);
        gm.ensureRotationHandlesForTransform();
        var controller = gm.getController();
        if (controller != null) controller.setRotationHandles(controller.getRotationHandles().stream()
                .filter(handle -> handle.getAxis() == com.director.client.gizmo.Axis.Y).toList());
        if (gm.getContext() != null) {
            gm.getContext().setMode(currentMode);
        }
    }

    private static boolean hasActorTransformApplier(GizmoManager gm) {
        com.director.client.gizmo.GizmoInteractionController controller = gm.getController();
        return controller != null
                && controller.getTransformApplier() instanceof ActorTransformApplier;
    }

    private void ensureActorTransformApplier(GizmoManager gm) {
        com.director.client.gizmo.GizmoInteractionController controller = gm.getController();
        if (controller == null) {
            return;
        }
        if (controller.getTransformApplier() instanceof ActorTransformApplier) {
            return;
        }
        com.director.core.selection.SelectionManager selectionManager = DirectorServices.get().selection();
        controller.setTransformApplier(new ActorTransformApplier(selectionManager, this));
        LOGGER.debug("[ActorGizmoController] 已设置 ActorTransformApplier");
    }

    private void deactivateActorTransformGizmoIfPresent() {
        GizmoManager gm = GizmoManager.get();
        if (!gm.isActive() || gm.getType() != GizmoType.TRANSFORM) {
            return;
        }
        if (ownsGizmo || hasActorTransformApplier(gm)) {
            gm.deactivate();
        }
    }
    
    /**
     * 检查两个 Box 是否相等（容差范围内）
     */
    private boolean boxesEqual(Box a, Box b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        double threshold = Math.sqrt(BOX_CHANGE_THRESHOLD_SQ);
        return Math.abs(a.minX - b.minX) < threshold &&
               Math.abs(a.minY - b.minY) < threshold &&
               Math.abs(a.minZ - b.minZ) < threshold &&
               Math.abs(a.maxX - b.maxX) < threshold &&
               Math.abs(a.maxY - b.maxY) < threshold &&
               Math.abs(a.maxZ - b.maxZ) < threshold;
    }

    // =========================================================
    // Gizmo → Timeline 写回（由 GizmoInteractionController 调用）
    // =========================================================

    /**
     * 处理 Gizmo 拖动开始
     * <p>由 GizmoInteractionController 在拖动开始时调用
     */
    public void handleGizmoDragStart() {
        if (dragging) return;
        resolveTargetTrack();
        if (targetActorId == null) {
            return;
        }
        if (actorTrack == null) {
            resolveTargetTrack();
        }
        if (actorTrack == null) {
            LOGGER.warn("[ActorGizmoController] 无法开始 Gizmo 拖动：未找到 Actor {} 的轨道", targetActorId);
            dragging = false;
            return;
        }

        TimelineModel model = resolveTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorGizmoController] TimelineModel 未初始化");
            dragging = false;
            return;
        }

        if (timelineTrack == null || timelineTrack.isLocked() || timelineTrack.isMuted() || model.isPlaying()) return;
        if (currentMode == GizmoMode.LOOK_AT) return;
        dragging = true;
        var initialPreview = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (initialPreview != null) {
            dragPreviewPosition = new Vec3d(initialPreview.getX(), initialPreview.getY(), initialPreview.getZ());
            dragPreviewRotation = new Vec3d(initialPreview.getYaw(), initialPreview.getPitch(), 0);
        }

        float time = model.getCurrentTime();

        // 1️⃣ 采样当前时刻状态（优先使用时间线采样，失败时回退到预览实体）
        EntityClipSampler sampler = new EntityClipSampler(actorTrack);
        EntitySample sample = sampler.sampleAt(time);

        Vec3d sampledPos;
        Vec3d sampledRot;
        Vec3d sampledScale;

        if (sample != null && sample.enabled) {
            sampledPos = new Vec3d(sample.position.x, sample.position.y, sample.position.z);
            sampledRot = new Vec3d(sample.rotation.x, sample.rotation.y, sample.rotation.z);
            sampledScale = new Vec3d(sample.scale.x, sample.scale.y, sample.scale.z);
        } else {
            // 回退：直接从 PreviewActorEntity 读取当前位置，使用默认旋转/缩放
            Entity preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
            if (preview == null) {
                LOGGER.warn("[ActorGizmoController] 无法采样 Actor {} 在时间 {} 的状态，且不存在预览实体", targetActorId, time);
                dragging = false;
                return;
            }
            sampledPos = new Vec3d(preview.getX(), preview.getY(), preview.getZ());
            // 旋转和缩放在没有采样数据时使用默认值；后续旋转/缩放拖动会基于 Gizmo 增量
            sampledRot = Vec3d.ZERO;
            sampledScale = new Vec3d(1.0, 1.0, 1.0);

            LOGGER.debug("[ActorGizmoController] 使用预览实体回退采样 Actor {} 在时间 {} 的状态: pos={}",
                    targetActorId, time, sampledPos);
        }

        var source = ActorTransformEditSession.resolveSource(model, timelineTrack);
        boolean activeClipAtCurrentTime = source != null && time >= source.getStartTime() && time <= source.getEndTime();
        try {
            dragSession = new ActorTransformEditSession(model, timelineTrack, source);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            dragging = false;
            return;
        }
        dragMode = currentMode;
        editingClip = dragSession.draft();
        if (source == null) {
            editingClip.setFromPos((float) sampledPos.x, (float) sampledPos.y, (float) sampledPos.z);
            editingClip.setToPos((float) sampledPos.x, (float) sampledPos.y, (float) sampledPos.z);
            editingClip.setFromRot((float) sampledRot.x, (float) sampledRot.y, (float) sampledRot.z);
            editingClip.setToRot((float) sampledRot.x, (float) sampledRot.y, (float) sampledRot.z);
            editingClip.setFromScale((float) sampledScale.x, (float) sampledScale.y, (float) sampledScale.z);
            editingClip.setToScale((float) sampledScale.x, (float) sampledScale.y, (float) sampledScale.z);
        } else if (!activeClipAtCurrentTime) {
            editingClip.setTimeRange(Math.min(source.getStartTime(), time), Math.max(source.getEndTime(), time + DEFAULT_CLIP_DURATION));
        }

        // 4️⃣ 按 Gizmo 模式缓存 base 值
        switch (currentMode) {
            case LOOK_AT -> {
                // LOOK_AT 模式由 ActorLookAtGizmoController 处理，这里不处理
                dragging = false;
                return;
            }
            case TRANSLATE -> {
                dragBasePos = new Vec3d(sampledPos.x, sampledPos.y, sampledPos.z);
                // 确保 fromPos 和 toPos 初始化为当前采样位置
                editingClip.setFromPos(
                        (float) sampledPos.x,
                        (float) sampledPos.y,
                        (float) sampledPos.z
                );
                editingClip.setToPos(
                        (float) sampledPos.x,
                        (float) sampledPos.y,
                        (float) sampledPos.z
                );
            }
            case ROTATE -> {
                dragBaseRot = sampledRot;
                editingClip.setFromRot((float) sampledRot.x, (float) sampledRot.y, (float) sampledRot.z);
                // 初始化 toRot = fromRot
                editingClip.setToRot(
                        (float) dragBaseRot.x,
                        (float) dragBaseRot.y,
                        (float) dragBaseRot.z
                );
            }
            case SCALE -> {
                dragBaseScale = sampledScale;
                editingClip.setFromScale((float) sampledScale.x, (float) sampledScale.y, (float) sampledScale.z);
                // 初始化 toScale = fromScale
                editingClip.setToScale(
                        (float) dragBaseScale.x,
                        (float) dragBaseScale.y,
                        (float) dragBaseScale.z
                );
            }
        }

        dragSession.arm();
        LOGGER.debug("[ActorGizmoController] 开始编辑 TransformClip: clip={}, mode={}, base={}",
                editingClip.getId(), currentMode,
                currentMode == GizmoMode.TRANSLATE ? dragBasePos :
                currentMode == GizmoMode.ROTATE ? dragBaseRot : dragBaseScale);
    }

    /**
     * 处理 Gizmo 拖动更新
     * <p>由 GizmoInteractionController 在拖动过程中调用
     *
     * @param deltaWorld 世界坐标增量
     */
    public void handleGizmoDragUpdate(Vec3d deltaWorld) {
        if (dragging && !isDragContextCurrent()) { cancelGizmoDrag(); return; }
        if (deltaWorld == null || !Double.isFinite(deltaWorld.x) || !Double.isFinite(deltaWorld.y)
                || !Double.isFinite(deltaWorld.z)) { cancelGizmoDrag(); return; }
        if (!dragging || editingClip == null) {
            return;
        }

        switch (currentMode) {
            case LOOK_AT -> {
                // LOOK_AT 模式由 ActorLookAtGizmoController 处理，这里不处理
                return;
            }
            // ========================
            // TRANSLATE
            // ========================
            case TRANSLATE -> {
                if (dragBasePos == null) {
                    return;
                }
                Vec3d newPos = dragBasePos.add(deltaWorld);
                // 播放头处立即生效：from/to 同步，避免采样在 t=0 时仍落在 fromPos 而把演员拉回
                editingClip.setFromPos(
                        (float) newPos.x,
                        (float) newPos.y,
                        (float) newPos.z
                );
                editingClip.setToPos(
                        (float) newPos.x,
                        (float) newPos.y,
                        (float) newPos.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip pos: ({}, {}, {})",
                        newPos.x, newPos.y, newPos.z);
            }

            // ========================
            // ROTATE
            // ========================
            case ROTATE -> {
                if (dragBaseRot == null) {
                    return;
                }
                Vec3d newRot = dragBaseRot.add(
                        deltaWorld.x,
                        deltaWorld.y,
                        deltaWorld.z
                );
                editingClip.setFromRot(
                        (float) newRot.x,
                        (float) newRot.y,
                        (float) newRot.z
                );
                editingClip.setToRot(
                        (float) newRot.x,
                        (float) newRot.y,
                        (float) newRot.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip rot: ({}, {}, {})",
                        newRot.x, newRot.y, newRot.z);
            }

            // ========================
            // SCALE
            // ========================
            case SCALE -> {
                if (dragBaseScale == null) {
                    return;
                }
                // scale 是乘法语义，更直觉
                // deltaWorld 表示缩放因子增量（例如 0.1 表示增加 10%）
                Vec3d newScale = new Vec3d(
                        dragBaseScale.x * (1.0 + deltaWorld.x),
                        dragBaseScale.y * (1.0 + deltaWorld.y),
                        dragBaseScale.z * (1.0 + deltaWorld.z)
                );
                // 限制缩放范围
                newScale = new Vec3d(
                        clamp(newScale.x),
                        clamp(newScale.y),
                        clamp(newScale.z)
                );
                editingClip.setFromScale(
                        (float) newScale.x,
                        (float) newScale.y,
                        (float) newScale.z
                );
                editingClip.setToScale(
                        (float) newScale.x,
                        (float) newScale.y,
                        (float) newScale.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip toScale: ({}, {}, {})",
                        newScale.x, newScale.y, newScale.z);
            }
        }

        if (!editingClip.getToPos().isFinite() || !editingClip.getToRot().isFinite()
                || !editingClip.getToScale().isFinite()) { cancelGizmoDrag(); return; }
        applyLivePreview();
        // Persistent tracks remain untouched until the release command.
    }

    /**
     * 拖动过程中立即更新预览实体，避免等待下一帧采样才看到位移。
     */
    private void applyLivePreview() {
        if (targetActorId == null || editingClip == null) {
            return;
        }
        Entity preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (preview == null) {
            return;
        }

        switch (currentMode) {
            case TRANSLATE -> {
                var to = editingClip.getToPos();
                preview.refreshPositionAndAngles(
                        to.x, to.y, to.z,
                        preview.getYaw(), preview.getPitch());
                GizmoManager gm = GizmoManager.get();
                if (gm.isActive()) {
                    Vec3d pos = new Vec3d(to.x, to.y, to.z);
                    gm.setPivot(pos);
                    Box box = preview.getBoundingBox();
                    if (box != null) {
                        gm.setTargetBox(box);
                    }
                }
            }
            case ROTATE -> {
                var to = editingClip.getToRot();
                preview.refreshPositionAndAngles(
                        preview.getX(), preview.getY(), preview.getZ(),
                        to.x, to.y);
                preview.setBodyYaw(to.x); preview.setHeadYaw(to.x);
                preview.lastYaw=to.x; preview.lastPitch=to.y;
                if(preview instanceof net.minecraft.entity.LivingEntity living) {
                    living.lastBodyYaw=living.bodyYaw; living.lastHeadYaw=living.headYaw;
                }
            }
            case SCALE -> {
                if (preview instanceof com.director.actor.preview.ActorPosePreview previewEntity)
                    previewEntity.setActorTransformScale(editingClip.getToScale());
            }
            default -> {
            }
        }
    }

    private TimelineModel resolveTimelineModel() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != null) {
            return model;
        }
        return com.director.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
    }

    /**
     * 处理 Gizmo 拖动结束
     * <p>由 GizmoInteractionController 在拖动结束时调用
     */
    public void handleGizmoDragEnd() {
        if (dragging && isDragContextCurrent()) dragSession.commit();
        else if (dragging) { cancelGizmoDrag(); return; }
        dragging = false;
        editingClip = null;
        dragSession = null;
        dragMode = null;
        clearDragBases();

        // 拖动结束后立即把 Gizmo 拉回演员身上，避免 pivot 与实体脱节
        if (targetActorId != null) {
            Entity preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
            if (preview != null) {
                lastGizmoBox = null;
                lastGizmoPivot = null;
                updateGizmoPosition(preview);
            }
        }
    }

    private boolean isDragContextCurrent() {
        if (dragSession == null || !dragSession.isCurrent() || dragMode != currentMode
                || dragSession.model() != resolveTimelineModel() || !dragSession.actorId().equals(targetActorId)) return false;
        try {
            var selected = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(dragSession.model(), targetActorId);
            var gm = GizmoManager.get();
            return selected == dragSession.owner() && gm.isActive() && hasActorTransformApplier(gm)
                    && gm.getContext() != null && gm.getContext().getMode() == dragMode;
        } catch (IllegalArgumentException unavailable) { return false; }
    }

    public void cancelGizmoDrag() {
        var originalPosition = dragPreviewPosition;
        var originalRotation = dragPreviewRotation;
        var originalModel = dragSession == null ? null : dragSession.model();
        if (dragSession != null) dragSession.cancel();
        dragging = false;
        editingClip = null;
        dragSession = null;
        dragMode = null;
        clearDragBases();
        lastGizmoBox = null;
        lastGizmoPivot = null;
        // Restore directly without persistence; runtime will apply the full pose on its next tick.
        if (actorTrack != null && targetActorId != null) {
            var model = resolveTimelineModel();
            var preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
            if (model != null && model == originalModel && preview != null) {
                var sample = new EntityClipSampler(actorTrack).sampleAt(model.getCurrentTime());
                if (sample != null && sample.enabled) {
                    preview.refreshPositionAndAngles(sample.position.x, sample.position.y, sample.position.z, sample.rotation.x, sample.rotation.y);
                    if (preview instanceof com.director.actor.preview.ActorPosePreview previewEntity)
                        previewEntity.setActorTransformScale(sample.scale);
                } else if (originalPosition != null && originalRotation != null) {
                    preview.refreshPositionAndAngles(originalPosition.x, originalPosition.y, originalPosition.z,
                            (float) originalRotation.x, (float) originalRotation.y);
                }
            }
        }
        // The interaction controller also owns a handle drag. Discard it so it cannot
        // continue delivering deltas after a history operation or mode switch.
        deactivateActorTransformGizmoIfPresent();
        ownsGizmo=false;
    }

    public org.joml.Vector3f previewScale(UUID actorId) {
        if (!dragging || currentMode != GizmoMode.SCALE || editingClip == null
                || !Objects.equals(targetActorId, actorId) || !isDragContextCurrent()) return null;
        return new org.joml.Vector3f(editingClip.getToScale());
    }

    // =========================================================
    // Resolve Track
    // =========================================================

    /**
     * 解析目标 Track（缓存，避免重复查找）
     */
    private void resolveTargetTrack() {
        this.actorTrack = null;
        this.timelineTrack = null;

        if (targetActorId == null) {
            return;
        }

        TimelineModel model = resolveTimelineModel();
        if (model == null) {
            return;
        }

        try {
            timelineTrack = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, targetActorId);
            actorTrack = timelineTrack.getActorTrackPayload();
        } catch (IllegalArgumentException unavailable) {
            LOGGER.debug("Actor transform target unavailable: {}", unavailable.getMessage());
        }
    }

    // =========================================================
    // Utils
    // =========================================================

    /**
     * 清除所有 drag base 值
     */
    private void clearDragBases() {
        dragBasePos = null;
        dragBaseRot = null;
        dragBaseScale = null;
        dragPreviewPosition = null;
        dragPreviewRotation = null;
    }

    /**
     * 限制值在指定范围内
     *
     * @param value 值
     * @return 限制后的值
     */
    private static double clamp(double value) {
        return Math.max(ActorGizmoController.MIN_SCALE, Math.min(ActorGizmoController.MAX_SCALE, value));
    }
}
