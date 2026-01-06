package com.chronoblocks.actor.runtime;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.preview.ActorPreviewManager;
import com.chronoblocks.actor.preview.PreviewActorEntity;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.playback.entity.EntityClipSampler;
import com.chronoblocks.ui.timeline.playback.entity.EntitySample;
import com.chronoblocks.ui.timeline.playback.entity.EntityActionEvent;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * ActorRuntimeController v2.0
 *
 * <p>核心职责：
 * <ul>
 *   <li>Actor（虚拟） → PreviewActorEntity（客户端投影）</li>
 *   <li>Timeline + Sampler 驱动演员表演</li>
 * </ul>
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 遍历 Timeline 中的 EntityTrack</li>
 *   <li>✅ 通过 ActorRef → Actor</li>
 *   <li>✅ 调用 EntityClipSampler 得到 EntitySample</li>
 *   <li>✅ 根据 Sample：Spawn / Despawn PreviewActorEntity</li>
 *   <li>✅ Apply Transform / LookAt / State / Action</li>
 *   <li>❌ 不关心世界实体</li>
 *   <li>❌ 不生成真实 MC Entity</li>
 * </ul>
 *
 * <p>整体链路：
 * <pre>
 * TimelineModel
 *  └─ EntityTrack
 *       └─ ActorRef
 *            ↓
 * ActorRegistry
 *            ↓
 * EntityClipSampler
 *            ↓
 * EntitySample
 *            ↓
 * ActorRuntimeController v2
 *            ↓
 * ActorPreviewManager
 *            ↓
 * PreviewActorEntity
 * </pre>
 *
 * @author ChronoBlocks Team
 * @version 2.0
 */
public final class ActorRuntimeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRuntimeController.class);
    private static final ActorRuntimeController INSTANCE = new ActorRuntimeController();

    public static ActorRuntimeController getInstance() {
        return INSTANCE;
    }

    private TimelineModel timelineModel;
    private ActorRegistry actorRegistry;

    /** 每条 EntityTrack 一个 Sampler Runtime */
    private final Map<TimelineTrack, TrackRuntime> runtimes = new IdentityHashMap<>();

    /** 总开关 */
    private boolean enabled = true;

    private ActorRuntimeController() {
    }

    // =========================================================
    // 绑定 Model / Registry
    // =========================================================

    /**
     * 绑定 TimelineModel 和 ActorRegistry
     *
     * @param model TimelineModel
     * @param registry ActorRegistry
     */
    public void bind(TimelineModel model, ActorRegistry registry) {
        this.timelineModel = model;
        this.actorRegistry = registry;
        rebuildRuntimes();
        LOGGER.info("[ActorRuntime] 绑定 TimelineModel 和 ActorRegistry，runtimes={}", runtimes.size());
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            ActorPreviewManager.getInstance().clear();
            LOGGER.info("[ActorRuntime] 已禁用，清空所有预览实体");
        } else {
            LOGGER.info("[ActorRuntime] 已启用");
        }
    }

    /**
     * 检查是否启用
     *
     * @return 如果启用则返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    // =========================================================
    // Update Loop（每帧 / 每 tick）
    // =========================================================

    /**
     * 每帧更新（由 TimelinePanel 调用）
     */
    public void update() {
        if (!enabled || timelineModel == null || actorRegistry == null) {
            return;
        }

        float time = timelineModel.getCurrentTime();

        // 清理已删除的轨道
        pruneDeadTracks();

        // 更新每条轨道的运行时
        for (TrackRuntime rt : runtimes.values()) {
            rt.update(time);
        }
    }

    // =========================================================
    // Runtime 管理
    // =========================================================

    /**
     * 重建运行时（当模型结构变化时调用）
     */
    private void rebuildRuntimes() {
        runtimes.clear();

        if (timelineModel == null) {
            return;
        }

        for (TimelineTrack track : timelineModel.getTracks()) {
            if (isEntityTrack(track)) {
                runtimes.put(track, new TrackRuntime(track));
            }
        }
    }

    /**
     * 清理已删除的轨道
     */
    private void pruneDeadTracks() {
        if (timelineModel == null) {
            return;
        }
        runtimes.keySet().removeIf(t -> !timelineModel.getTracks().contains(t));
    }

    /**
     * 检查是否为实体轨道
     *
     * @param track TimelineTrack
     * @return 如果是实体轨道则返回 true
     */
    private boolean isEntityTrack(TimelineTrack track) {
        return track != null
                && track.getType() != null
                && track.getType() == com.chronoblocks.ui.timeline.model.TrackType.ENTITY;
    }

    // =========================================================
    // TrackRuntime（一条演员轨道的运行时）
    // =========================================================

    /**
     * 一条演员轨道的运行时
     */
    private final class TrackRuntime {

        private final EntityTrack entityTrack;
        private final EntityClipSampler sampler;

        /** 当前绑定的 Actor */
        private Actor actor;

        /** 是否已生成 PreviewEntity */
        private boolean spawned = false;

        /** 上一帧 Sample（用于 Action edge trigger） */
        private EntitySample lastSample;

        /**
         * 构造函数
         *
         * @param track TimelineTrack
         */
        TrackRuntime(TimelineTrack track) {
            this.entityTrack = track.getEntityTrackPayload();
            if (this.entityTrack == null) {
                throw new IllegalArgumentException("Track does not have EntityTrack payload");
            }
            this.sampler = new EntityClipSampler(entityTrack);
        }

        /**
         * 更新运行时（每帧调用）
         *
         * @param time 当前时间（秒）
         */
        void update(float time) {
            // 1️⃣ 解析 Actor
            ActorRef ref = entityTrack.getActorRef();
            if (ref == null) {
                // 没有 ActorRef，无法继续
                if (spawned) {
                    // 如果之前已生成，现在移除
                    ActorPreviewManager.getInstance().removePreview(actor != null ? actor.getActorId() : null);
                    spawned = false;
                    actor = null;
                }
                return;
            }

            // 从 ActorRegistry 解析 Actor
            actorRegistry.resolve(ref).ifPresentOrElse(
                    a -> this.actor = a,
                    () -> handleMissingActor(ref)
            );

            if (actor == null) {
                return;
            }

            // 2️⃣ 采样
            EntitySample sample = sampler.sampleAt(time);
            if (sample == null) {
                lastSample = null;
                return;
            }

            // 3️⃣ Spawn / Despawn
            handleSpawnLifecycle(sample);

            // 4️⃣ Apply Sample
            if (spawned) {
                PreviewActorEntity entity =
                        ActorPreviewManager.getInstance().getPreview(actor.getActorId());
                if (entity != null) {
                    applySample(entity, sample, lastSample);
                }
            }

            lastSample = sample;
        }

        /**
         * 处理缺失的 Actor
         *
         * @param ref ActorRef
         */
        private void handleMissingActor(ActorRef ref) {
            // Actor 被删除，但 Timeline 还在
            // 安全策略：移除 Preview
            if (ref != null) {
                ActorPreviewManager.getInstance().removePreview(ref.getActorId());
            }
            spawned = false;
            actor = null;
        }

        // =====================================================
        // Spawn / Despawn（由 Sample 决定）
        // =====================================================

        /**
         * 处理 Spawn/Despawn 生命周期
         *
         * @param sample EntitySample
         */
        private void handleSpawnLifecycle(EntitySample sample) {
            if (sample == null) {
                return;
            }

            if (sample.active && !spawned) {
                // 需要生成预览实体
                PreviewActorEntity entity = ActorPreviewManager.getInstance().ensurePreview(actor);
                if (entity != null) {
                    spawned = true;
                    LOGGER.debug("[ActorRuntime] Spawn 预览实体: {}", actor.getName());
                }
            }

            if (!sample.active && spawned) {
                // 需要移除预览实体
                ActorPreviewManager.getInstance().removePreview(actor.getActorId());
                spawned = false;
                LOGGER.debug("[ActorRuntime] Despawn 预览实体: {}", actor.getName());
            }
        }

        // =====================================================
        // Apply Sample → PreviewActorEntity
        // =====================================================

        /**
         * 应用 Sample 到 PreviewActorEntity
         *
         * @param entity PreviewActorEntity
         * @param s 当前 Sample
         * @param prev 上一帧 Sample
         */
        private void applySample(
                PreviewActorEntity entity,
                EntitySample s,
                EntitySample prev
        ) {
            // === Visibility ===
            entity.setInvisible(s.visibility <= 0.01f);

            // === Transform ===
            entity.refreshPositionAndAngles(
                    s.position.x,
                    s.position.y,
                    s.position.z,
                    s.rotation.x,
                    s.rotation.y
            );

            // === LookAt（简化：直接调整 yaw/pitch）===
            if (s.lookAtEnabled) {
                applyLookAt(entity, s);
            }

            // === State ===
            applyState(entity, s);

            // === Action（一次性触发）===
            applyActionEvents(s, prev);
        }

        /**
         * 应用 LookAt
         *
         * @param entity PreviewActorEntity
         * @param s EntitySample
         */
        private void applyLookAt(PreviewActorEntity entity, EntitySample s) {
            // 获取实体当前位置
            Vec3d pos = new Vec3d(
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
            );
            
            // 获取目标位置（从 EntitySample）
            Vec3d target = new Vec3d(
                    s.lookAtPoint.x,
                    s.lookAtPoint.y,
                    s.lookAtPoint.z
            );
            
            Vec3d dir = target.subtract(pos);
            double length = dir.length();
            
            if (length < 0.01) {
                // 目标太近，不处理
                return;
            }
            
            dir = dir.normalize();

            float yaw = (float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90;
            float pitch = (float) (-Math.asin(dir.y) * 180 / Math.PI);

            // 使用 lookAtStrength 进行插值
            float currentYaw = entity.getYaw();
            float currentPitch = entity.getPitch();

            entity.setYaw(lerp(currentYaw, yaw, s.lookAtStrength));
            entity.setPitch(lerp(currentPitch, pitch, s.lookAtStrength));
        }

        /**
         * 应用 State
         *
         * @param entity PreviewActorEntity
         * @param s EntitySample
         */
        private void applyState(PreviewActorEntity entity, EntitySample s) {
            // 发光
            entity.setGlowing(s.glowing);

            // 其他状态（sneaking、sprinting 等）需要 LivingEntity
            // 这里暂时只处理通用的状态
        }

        /**
         * 应用 Action 事件（一次性触发）
         *
         * @param s 当前 Sample
         * @param prev 上一帧 Sample
         */
        private void applyActionEvents(EntitySample s, EntitySample prev) {
            if (s.actionEvents == null || s.actionEvents.isEmpty()) {
                return;
            }

            // Preview 阶段：只做可视化 / 提示
            // 真正逻辑行为你未来可交给 Server / Script
            for (EntityActionEvent e : s.actionEvents) {
                LOGGER.debug("[ActorRuntime] Actor {} 触发 Action: {}", actor.getName(), e.type());
                // TODO: 未来可以在这里触发声音、粒子效果等
            }
        }
    }

    // =========================================================
    // Utils
    // =========================================================

    /**
     * 线性插值
     *
     * @param a 起始值
     * @param b 结束值
     * @param t 插值系数 [0-1]
     * @return 插值结果
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}

