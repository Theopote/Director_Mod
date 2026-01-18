package com.chronoblocks.ui.actor.gizmo;

import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.preview.ActorPreviewManager;
import net.minecraft.entity.Entity;
import com.chronoblocks.client.gizmo.GizmoManager;
import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.client.gizmo.GizmoType;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntityClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTransformClip;
import com.chronoblocks.ui.timeline.playback.entity.EntityClipSampler;
import com.chronoblocks.ui.timeline.playback.entity.EntitySample;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
 *   <li>完全遵守方案 C：只操作 ActorId、EntityTrack、EntityTransformClip</li>
 * </ul>
 *
 * @author ChronoBlocks Team
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

    /** 当前编辑的 EntityTrack（缓存，避免重复查找） */
    private EntityTrack entityTrack;
    private TimelineTrack timelineTrack;

    /** 当前 Gizmo 模式 */
    private GizmoMode currentMode = GizmoMode.TRANSLATE;

    /** Gizmo 编辑状态 */
    private EntityTransformClip editingClip;
    private boolean dragging = false;

    /** Drag 基础值（按模式区分） */
    private Vec3d dragBasePos;      // TRANSLATE 模式
    private Vec3d dragBaseRot;       // ROTATE 模式
    private Vec3d dragBaseScale;     // SCALE 模式

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
        this.targetActorId = actorId;
        this.dragging = false;
        this.editingClip = null;
        clearDragBases();

        resolveTargetTrack();
    }

    /**
     * 清除目标
     */
    public void clearTarget() {
        this.targetActorId = null;
        this.entityTrack = null;
        this.timelineTrack = null;
        this.dragging = false;
        this.editingClip = null;
        clearDragBases();

        // 在停用 Gizmo 之前，先清除方块选区
        // 因为 deactivate() 会调用 apply()，会将 Gizmo 的 Box 转换为方块选区
        try {
            com.chronoblocks.core.selection.SelectionManager blockSelectionManager = 
                    com.chronoblocks.core.selection.SelectionManager.get();
            if (blockSelectionManager != null) {
                blockSelectionManager.clearSelection();
            }
        } catch (Exception e) {
            LOGGER.debug("[ActorGizmoController] 清除方块选区失败（可能不在客户端）: {}", e.getMessage());
        }

        GizmoManager.get().deactivate();
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

    public void setPreferredMode(GizmoMode mode) {
        this.currentMode = mode != null ? mode : GizmoMode.TRANSLATE;
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
        if (targetActorId == null) {
            // 如果没有目标，确保 Gizmo 已停用
            if (GizmoManager.get().isActive()) {
                GizmoManager.get().deactivate();
            }
            return;
        }

        Entity preview = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (preview == null) {
            // 如果预览实体不存在，停用 Gizmo
            if (GizmoManager.get().isActive()) {
                GizmoManager.get().deactivate();
            }
            return;
        }

        // 1️⃣ 读取当前 Gizmo 模式
        updateCurrentMode();

        // 2️⃣ 更新 Gizmo 位置（跟随实体）
        updateGizmoPosition(preview);

        // 3️⃣ 处理 Gizmo 拖动（如果发生）
        // 注意：拖动事件通过 GizmoInteractionController 的回调处理
        // 这里只检查状态，实际处理在 handleGizmoDragStart/Update/End 中
    }

    /**
     * 更新当前 Gizmo 模式（从 GizmoManager 读取）
     */
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
     */
    private void updateGizmoPosition(Entity entity) {
        Box box = entity.getBoundingBox();
        if (box == null) {
            return;
        }

        Vec3d pivot = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

        // 如果 Gizmo 未激活，激活它
        if (!GizmoManager.get().isActive()) {
            GizmoManager.get().activate(GizmoType.TRANSFORM, box, pivot);
            // 设置默认模式
            if (GizmoManager.get().getContext() != null) {
                GizmoManager.get().getContext().setMode(currentMode);
            }
            LOGGER.debug("[ActorGizmoController] 已激活 Gizmo: Actor={}, Mode={}", targetActorId, currentMode);
        } else {
            // 如果已激活，更新位置和模式
            GizmoManager.get().setTargetBox(box);
            GizmoManager.get().setPivot(pivot);
            if (GizmoManager.get().getContext() != null) {
                GizmoManager.get().getContext().setMode(currentMode);
            }
        }
    }

    // =========================================================
    // Gizmo → Timeline 写回（由 GizmoInteractionController 调用）
    // =========================================================

    /**
     * 处理 Gizmo 拖动开始
     * <p>由 GizmoInteractionController 在拖动开始时调用
     */
    public void handleGizmoDragStart() {
        if (targetActorId == null || entityTrack == null) {
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorGizmoController] TimelineModel 未初始化");
            return;
        }

        dragging = true;

        float time = model.getCurrentTime();

        // 1️⃣ 采样当前时刻状态
        EntityClipSampler sampler = new EntityClipSampler(entityTrack);
        EntitySample sample = sampler.sampleAt(time);
        if (sample == null || !sample.enabled) {
            LOGGER.warn("[ActorGizmoController] 无法采样 Actor {} 在时间 {} 的状态", targetActorId, time);
            dragging = false;
            return;
        }

        Vec3d sampledPos = new Vec3d(
                sample.position.x,
                sample.position.y,
                sample.position.z
        );

        // 2️⃣ 找到当前时间覆盖的 TransformClip（last-wins）
        editingClip = findActiveTransformClip(entityTrack, time);

        // 3️⃣ 如果没有，则自动创建一个
        if (editingClip == null) {
            editingClip = createTransformClip(entityTrack, time, sampledPos);
            LOGGER.debug("[ActorGizmoController] 创建新的 TransformClip: start={}, end={}",
                    editingClip.getStartTime(), editingClip.getEndTime());
        }

        // 4️⃣ 按 Gizmo 模式缓存 base 值
        switch (currentMode) {
            case LOOK_AT -> {
                // LOOK_AT 模式由 ActorLookAtGizmoController 处理，这里不处理
                dragging = false;
                return;
            }
            case TRANSLATE -> {
                dragBasePos = new Vec3d(
                        editingClip.getFromPos().x,
                        editingClip.getFromPos().y,
                        editingClip.getFromPos().z
                );
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
                // 使用当前 clip 的 fromRot 作为 base（如果 clip 已存在）
                // 否则使用采样值
                if (editingClip.getFromRot().x != 0 || editingClip.getFromRot().y != 0 || editingClip.getFromRot().z != 0) {
                    dragBaseRot = new Vec3d(
                            editingClip.getFromRot().x,
                            editingClip.getFromRot().y,
                            editingClip.getFromRot().z
                    );
                } else {
                    dragBaseRot = new Vec3d(
                            sample.rotation.x,
                            sample.rotation.y,
                            sample.rotation.z
                    );
                    editingClip.setFromRot(
                            sample.rotation.x,
                            sample.rotation.y,
                            sample.rotation.z
                    );
                }
                // 初始化 toRot = fromRot
                editingClip.setToRot(
                        (float) dragBaseRot.x,
                        (float) dragBaseRot.y,
                        (float) dragBaseRot.z
                );
            }
            case SCALE -> {
                // 使用当前 clip 的 fromScale 作为 base（如果 clip 已存在）
                // 否则使用采样值
                if (editingClip.getFromScale().x != 1 || editingClip.getFromScale().y != 1 || editingClip.getFromScale().z != 1) {
                    dragBaseScale = new Vec3d(
                            editingClip.getFromScale().x,
                            editingClip.getFromScale().y,
                            editingClip.getFromScale().z
                    );
                } else {
                    dragBaseScale = new Vec3d(
                            sample.scale.x,
                            sample.scale.y,
                            sample.scale.z
                    );
                    editingClip.setFromScale(
                            sample.scale.x,
                            sample.scale.y,
                            sample.scale.z
                    );
                }
                // 初始化 toScale = fromScale
                editingClip.setToScale(
                        (float) dragBaseScale.x,
                        (float) dragBaseScale.y,
                        (float) dragBaseScale.z
                );
            }
        }

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
                editingClip.setToPos(
                        (float) newPos.x,
                        (float) newPos.y,
                        (float) newPos.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip toPos: ({}, {}, {})",
                        newPos.x, newPos.y, newPos.z);
            }

            // ========================
            // ROTATE
            // ========================
            case ROTATE -> {
                if (dragBaseRot == null) {
                    return;
                }
                // 约定：deltaWorld.x/y/z 映射为 yaw / pitch / roll 的增量（度）
                Vec3d newRot = dragBaseRot.add(
                        deltaWorld.x,
                        deltaWorld.y,
                        deltaWorld.z
                );
                editingClip.setToRot(
                        (float) newRot.x,
                        (float) newRot.y,
                        (float) newRot.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip toRot: ({}, {}, {})",
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
                editingClip.setToScale(
                        (float) newScale.x,
                        (float) newScale.y,
                        (float) newScale.z
                );
                LOGGER.trace("[ActorGizmoController] 更新 TransformClip toScale: ({}, {}, {})",
                        newScale.x, newScale.y, newScale.z);
            }
        }

        // 通知时间线数据已变
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }
    }

    /**
     * 处理 Gizmo 拖动结束
     * <p>由 GizmoInteractionController 在拖动结束时调用
     */
    public void handleGizmoDragEnd() {
        if (editingClip != null) {
            LOGGER.debug("[ActorGizmoController] 结束编辑 TransformClip: clip={}, mode={}",
                    editingClip.getId(), currentMode);

            // 通知时间线数据已变
            TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                model.markDirty();
            }
        }

        dragging = false;
        editingClip = null;
        clearDragBases();
    }

    // =========================================================
    // Clip Helpers
    // =========================================================

    /**
     * 找到覆盖指定时间的 TransformClip（last-wins 规则）
     * <p>last-wins：取 startTime 最大的 TransformClip
     *
     * @param track EntityTrack
     * @param time 时间（秒）
     * @return TransformClip，如果未找到则返回 null
     */
    private EntityTransformClip findActiveTransformClip(EntityTrack track, float time) {
        if (track == null) {
            return null;
        }

        EntityTransformClip result = null;

        List<EntityClip> clips = track.getClips();
        for (EntityClip c : clips) {
            if (c instanceof EntityTransformClip tc && !tc.isMuted()) {
                if (time >= tc.getStartTime() && time <= tc.getEndTime()) {
                    if (result == null || tc.getStartTime() >= result.getStartTime()) {
                        result = tc;
                    }
                }
            }
        }

        return result;
    }

    /**
     * 创建新的 TransformClip
     *
     * @param track EntityTrack
     * @param time 开始时间（秒）
     * @param pos 起始位置
     * @return 新创建的 TransformClip
     */
    private EntityTransformClip createTransformClip(EntityTrack track, float time, Vec3d pos) {

        EntityTransformClip clip = getEntityTransformClip(time, pos);

        // 默认插值类型为 SMOOTH
        clip.setInterpolation(com.chronoblocks.ui.timeline.model.TimelineInterpolation.SMOOTH);

        // 添加到轨道
        track.addClip(clip);
        if (timelineTrack != null) {
            EntityTimelineClip timelineClip = new EntityTimelineClip(clip, track.getColorRgba());
            timelineTrack.addClip(timelineClip);
        }

        // 通知时间线数据已变
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }

        return clip;
    }

    private static @NotNull EntityTransformClip getEntityTransformClip(float time, Vec3d pos) {
        EntityTransformClip clip = new EntityTransformClip(
                "Transform",
                time,
                time + DEFAULT_CLIP_DURATION
        );

        // 初始化所有变换属性
        clip.setFromPos(
                (float) pos.x,
                (float) pos.y,
                (float) pos.z
        );
        clip.setToPos(
                (float) pos.x,
                (float) pos.y,
                (float) pos.z
        );

        // 旋转默认为 0
        clip.setFromRot(0, 0, 0);
        clip.setToRot(0, 0, 0);

        // 缩放默认为 1
        clip.setFromScale(1, 1, 1);
        clip.setToScale(1, 1, 1);
        return clip;
    }

    // =========================================================
    // Resolve Track
    // =========================================================

    /**
     * 解析目标 Track（缓存，避免重复查找）
     */
    private void resolveTargetTrack() {
        this.entityTrack = null;
        this.timelineTrack = null;

        if (targetActorId == null) {
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            return;
        }

        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ENTITY) {
                continue;
            }

            EntityTrack et = track.getEntityTrackPayload();
            if (et == null) {
                continue;
            }

            ActorRef ref = et.getActorRef();
            if (ref != null && Objects.equals(ref.getActorId(), targetActorId)) {
                this.entityTrack = et;
                this.timelineTrack = track;
                return;
            }
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
