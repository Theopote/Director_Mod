package com.chronoblocks.ui.actor.gizmo;

import com.chronoblocks.actor.preview.ActorPreviewManager;
import com.chronoblocks.actor.preview.PreviewActorEntity;
import com.chronoblocks.client.gizmo.GizmoManager;
import com.chronoblocks.client.gizmo.GizmoType;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTransformClip;
import com.chronoblocks.ui.timeline.playback.entity.EntityClipSampler;
import com.chronoblocks.ui.timeline.playback.entity.EntitySample;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * ActorGizmoController v1.0
 *
 * <p>Gizmo 拖动 → 写 TransformClip
 * <p>职责：
 * <ul>
 *   <li>如果当前选中了 actor：构造 GizmoContext（pivot + targetBox）给 GizmoRenderer</li>
 *   <li>处理 gizmo 拖动事件：把拖动 delta 写回 EntityTransformClip</li>
 * </ul>
 *
 * <p>编辑策略：
 * <ul>
 *   <li>当前时间 t = timelineModel.getCurrentTime()</li>
 *   <li>找到 EntityTrack 中覆盖 t 的 TransformClip（last-wins）</li>
 *   <li>如果没有：创建一个新的 TransformClip，默认时长比如 1.0s（或 0.5s）</li>
 *   <li>fromPos = sampler.sample(t).position</li>
 *   <li>toPos = fromPos</li>
 *   <li>插值默认 smooth</li>
 *   <li>拖动过程中：实时更新 toPos = fromPos + draggedDelta</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorGizmoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGizmoController.class);
    private static final ActorGizmoController INSTANCE = new ActorGizmoController();

    public static ActorGizmoController getInstance() {
        return INSTANCE;
    }

    /** 当前选中的 Actor ID */
    private UUID targetActorId = null;

    /** 正在编辑的 TransformClip（拖动时使用） */
    private EntityTransformClip editingClip = null;

    /** 拖动开始时的基础位置（fromPos） */
    private Vector3f basePos = new Vector3f();

    /**
     * 检查是否正在编辑
     *
     * @return 如果正在编辑则返回 true
     */
    public boolean isEditing() {
        return editingClip != null;
    }

    /** 默认 TransformClip 时长（秒） */
    private static final float DEFAULT_CLIP_DURATION = 1.0f;

    private ActorGizmoController() {
        // 设置 Gizmo 应用回调（当 Gizmo 被应用时调用）
        GizmoManager.get().setOnApply(this::onGizmoApply);
    }

    /**
     * 初始化（在系统启动时调用）
     * <p>设置自定义 TransformApplier 以拦截拖动事件
     */
    public void initialize() {
        // 这里不需要额外初始化，因为 GizmoManager 的 onApply 回调已经设置
        // 拖动事件通过 GizmoInteractionController 的 applyTransform 方法处理
        // 我们需要在 GizmoInteractionController 中注入自定义逻辑
        LOGGER.debug("[ActorGizmoController] 已初始化");
    }

    /**
     * 设置目标 Actor
     *
     * @param actorId Actor ID，如果为 null 则清除目标
     */
    public void setTargetActor(UUID actorId) {
        if (actorId == null) {
            clearTarget();
            return;
        }

        this.targetActorId = actorId;
        updateGizmo();
        LOGGER.debug("[ActorGizmoController] 设置目标 Actor: {}", actorId);
    }

    /**
     * 清除目标
     */
    public void clearTarget() {
        this.targetActorId = null;
        this.editingClip = null;
        GizmoManager.get().deactivate();
        LOGGER.debug("[ActorGizmoController] 清除目标");
    }

    /**
     * 更新 Gizmo（每帧调用，确保 Gizmo 跟随实体位置）
     */
    public void update() {
        if (targetActorId == null) {
            return;
        }

        // 如果 Gizmo 未激活，尝试激活
        if (!GizmoManager.get().isActive()) {
            updateGizmo();
        } else {
            // 如果已激活，更新 Gizmo 位置（跟随实体）
            updateGizmoPosition();
        }
    }

    /**
     * 更新 Gizmo（激活或更新位置）
     */
    private void updateGizmo() {
        if (targetActorId == null) {
            return;
        }

        PreviewActorEntity previewEntity = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (previewEntity == null) {
            LOGGER.warn("[ActorGizmoController] 未找到 Actor {} 的 PreviewEntity", targetActorId);
            return;
        }

        // 获取实体包围盒
        Box boundingBox = previewEntity.getBoundingBox();
        if (boundingBox == null) {
            LOGGER.warn("[ActorGizmoController] PreviewEntity 的 boundingBox 为 null");
            return;
        }

        // 获取实体位置（作为 pivot）
        Vec3d pivot = new Vec3d(previewEntity.getX(), previewEntity.getY(), previewEntity.getZ());

        // 激活 Gizmo
        GizmoManager.get().activate(GizmoType.TRANSFORM, boundingBox, pivot);
        LOGGER.debug("[ActorGizmoController] 已激活 Gizmo: Actor={}, Box={}, Pivot={}", 
                targetActorId, boundingBox, pivot);
    }

    /**
     * 更新 Gizmo 位置（跟随实体）
     */
    private void updateGizmoPosition() {
        if (targetActorId == null) {
            return;
        }

        PreviewActorEntity previewEntity = ActorPreviewManager.getInstance().getPreview(targetActorId);
        if (previewEntity == null) {
            return;
        }

        Box boundingBox = previewEntity.getBoundingBox();
        Vec3d pivot = new Vec3d(previewEntity.getX(), previewEntity.getY(), previewEntity.getZ());

        if (boundingBox != null && pivot != null) {
            GizmoManager.get().setTargetBox(boundingBox);
            GizmoManager.get().setPivot(pivot);
        }
    }

    /**
     * Gizmo 拖动开始
     * <p>找到或创建 TransformClip，准备编辑
     */
    public void onGizmoDragStart() {
        if (targetActorId == null) {
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorGizmoController] TimelineModel 未初始化");
            return;
        }

        TimelineTrack track = model.findEntityTrackByActorId(targetActorId);
        if (track == null) {
            LOGGER.warn("[ActorGizmoController] 未找到 Actor {} 对应的 EntityTrack", targetActorId);
            return;
        }

        EntityTrack entityTrack = track.getEntityTrackPayload();
        if (entityTrack == null) {
            LOGGER.warn("[ActorGizmoController] EntityTrack payload 为 null");
            return;
        }

        float currentTime = model.getCurrentTime();

        // 1️⃣ 采样当前时间的位置
        EntityClipSampler sampler = new EntityClipSampler(entityTrack);
        EntitySample sample = sampler.sampleAt(currentTime);
        if (sample == null || !sample.enabled) {
            LOGGER.warn("[ActorGizmoController] 无法采样 Actor {} 在时间 {} 的状态", targetActorId, currentTime);
            return;
        }

        // 2️⃣ 找到覆盖当前时间的 TransformClip（last-wins）
        EntityTransformClip activeClip = findActiveTransformClip(entityTrack, currentTime);

        // 3️⃣ 如果没有，创建一个新的
        if (activeClip == null) {
            activeClip = createTransformClip(entityTrack, currentTime, sample.position);
            LOGGER.debug("[ActorGizmoController] 创建新的 TransformClip: start={}, end={}", 
                    activeClip.getStartTime(), activeClip.getEndTime());
        }

        // 4️⃣ 设置编辑状态
        this.editingClip = activeClip;
        this.basePos.set(sample.position);

        // 5️⃣ 初始化 toPos = fromPos（开始拖动时）
        activeClip.setToPos(sample.position.x, sample.position.y, sample.position.z);
        activeClip.setFromPos(sample.position.x, sample.position.y, sample.position.z);

        LOGGER.debug("[ActorGizmoController] 开始编辑 TransformClip: clip={}, basePos={}", 
                activeClip.getId(), basePos);
    }

    /**
     * Gizmo 拖动更新
     * <p>实时更新 TransformClip 的 toPos
     *
     * @param deltaWorld 世界坐标增量
     */
    public void onGizmoDragUpdate(Vec3d deltaWorld) {
        if (editingClip == null) {
            return;
        }

        // 计算新的 toPos = basePos + deltaWorld
        float newX = basePos.x + (float) deltaWorld.x;
        float newY = basePos.y + (float) deltaWorld.y;
        float newZ = basePos.z + (float) deltaWorld.z;

        editingClip.setToPos(newX, newY, newZ);

        // 标记模型为 dirty（触发保存）
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }

        LOGGER.trace("[ActorGizmoController] 更新 TransformClip toPos: ({}, {}, {})", newX, newY, newZ);
    }

    /**
     * Gizmo 拖动结束
     */
    public void onGizmoDragEnd() {
        if (editingClip != null) {
            LOGGER.debug("[ActorGizmoController] 结束编辑 TransformClip: clip={}, toPos=({}, {}, {})", 
                    editingClip.getId(), 
                    editingClip.getToPos().x, 
                    editingClip.getToPos().y, 
                    editingClip.getToPos().z);

            // 标记模型为 dirty（触发保存）
            TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
            if (model != null) {
                model.markDirty();
            }

            editingClip = null;
            basePos.set(0, 0, 0);
        }
    }

    /**
     * Gizmo 应用回调（当 Gizmo 被应用时调用）
     * <p>这里我们不需要做任何事情，因为拖动过程中已经实时更新了
     */
    private void onGizmoApply(com.chronoblocks.client.gizmo.GizmoContext context) {
        // 拖动过程中已经实时更新了 TransformClip，这里不需要额外操作
        LOGGER.debug("[ActorGizmoController] Gizmo 应用回调（已实时更新，无需额外操作）");
    }

    /**
     * 处理 Gizmo 拖动开始
     * <p>由 GizmoInteractionController 在拖动开始时调用
     */
    public void handleGizmoDragStart() {
        onGizmoDragStart();
    }

    /**
     * 处理 Gizmo 拖动更新
     * <p>由 GizmoInteractionController 在拖动过程中调用
     *
     * @param deltaWorld 世界坐标增量
     */
    public void handleGizmoDragUpdate(Vec3d deltaWorld) {
        onGizmoDragUpdate(deltaWorld);
    }

    /**
     * 处理 Gizmo 拖动结束
     * <p>由 GizmoInteractionController 在拖动结束时调用
     */
    public void handleGizmoDragEnd() {
        onGizmoDragEnd();
    }

    /**
     * 找到覆盖指定时间的 TransformClip（last-wins 规则）
     *
     * @param entityTrack EntityTrack
     * @param time 时间（秒）
     * @return TransformClip，如果未找到则返回 null
     */
    private EntityTransformClip findActiveTransformClip(EntityTrack entityTrack, float time) {
        List<com.chronoblocks.ui.timeline.model.entity.EntityClip> clipsAtTime = entityTrack.findClipsAt(time);

        EntityTransformClip active = null;
        for (com.chronoblocks.ui.timeline.model.entity.EntityClip clip : clipsAtTime) {
            if (clip instanceof EntityTransformClip transformClip && !transformClip.isMuted()) {
                if (active == null || transformClip.getStartTime() >= active.getStartTime()) {
                    active = transformClip;
                }
            }
        }

        return active;
    }

    /**
     * 创建新的 TransformClip
     *
     * @param entityTrack EntityTrack
     * @param time 开始时间（秒）
     * @param position 起始位置
     * @return 新创建的 TransformClip
     */
    private EntityTransformClip createTransformClip(EntityTrack entityTrack, float time, Vector3f position) {
        EntityTransformClip clip = new EntityTransformClip(
                "Transform",
                time,
                time + DEFAULT_CLIP_DURATION
        );

        // 设置起始和结束位置（开始时相同）
        clip.setFromPos(position.x, position.y, position.z);
        clip.setToPos(position.x, position.y, position.z);

        // 默认插值类型为 SMOOTH
        clip.setInterpolation(com.chronoblocks.ui.timeline.model.TimelineInterpolation.SMOOTH);

        // 添加到轨道
        entityTrack.addClip(clip);

        // 标记模型为 dirty
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }

        return clip;
    }
}

