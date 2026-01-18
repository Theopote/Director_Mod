package com.chronoblocks.actor.runtime;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.actor.preview.ActorPreviewManager;
import com.chronoblocks.actor.rig.ActorRigProfile;
import net.minecraft.entity.Entity;
import com.chronoblocks.actor.prop.*;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.playback.entity.EntityClipSampler;
import com.chronoblocks.ui.timeline.playback.entity.EntitySample;
import com.chronoblocks.ui.timeline.playback.entity.EntityActionEvent;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

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
    private TimelineModel.ModelChangeListener modelChangeListener;

    /** 每条 EntityTrack 一个 Sampler Runtime */
    private final Map<TimelineTrack, TrackRuntime> runtimes = new IdentityHashMap<>();

    /** 每个 Actor 一个 PropRuntime */
    private final Map<UUID, ActorPropRuntime> propRuntimes = new HashMap<>();

    /** 投掷物管理器 */
    private final ProjectileRuntimeManager projectileManager = ProjectileRuntimeManager.getInstance();

    /** 上一帧时间（用于计算 deltaTime） */
    private float lastUpdateTime = 0f;

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
        if (this.timelineModel != null && modelChangeListener != null) {
            this.timelineModel.removeModelChangeListener(modelChangeListener);
        }
        this.timelineModel = model;
        this.actorRegistry = registry;
        if (this.timelineModel != null) {
            modelChangeListener = changeType -> {
                if ("track_added".equals(changeType) || "track_removed".equals(changeType)) {
                    rebuildRuntimes();
                }
            };
            this.timelineModel.addModelChangeListener(modelChangeListener);
        } else {
            modelChangeListener = null;
        }
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
            ProjectileRuntimeManager.getInstance().clear();
            LOGGER.info("[ActorRuntime] 已禁用，清空所有预览实体和投掷物");
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
        float deltaTime = time - lastUpdateTime;
        if (deltaTime < 0f) {
            // 时间倒流（rewind），重置 deltaTime
            deltaTime = 0f;
        }
        lastUpdateTime = time;

        // 清理已删除的轨道
        pruneDeadTracks();

        // 更新每条轨道的运行时
        for (TrackRuntime rt : runtimes.values()) {
            rt.update(time);
        }

        // 更新投掷物（每帧更新，使用 deltaTime）
        projectileManager.update(deltaTime);
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
                // 检查是否有 EntityTrack payload
                EntityTrack entityTrack = track.getEntityTrackPayload();
                if (entityTrack != null) {
                    try {
                        runtimes.put(track, new TrackRuntime(track));
                    } catch (IllegalArgumentException e) {
                        // 如果轨道没有有效的 EntityTrack payload，跳过它
                        LOGGER.warn("[ActorRuntime] 跳过无效的实体轨道: {} - {}", track.getName(), e.getMessage());
                    }
                } else {
                    LOGGER.warn("[ActorRuntime] 跳过没有 EntityTrack payload 的轨道: {}", track.getName());
                }
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

        /** 上一帧时间（用于计算 deltaTime） */
        private float lastTime = -1f;

        /** ActorRuntime（身体控制参数） */
        private final ActorRuntime actorRuntime = new ActorRuntime();

        /**
         * 构造函数
         *
         * @param track TimelineTrack
         * @throws IllegalArgumentException 如果轨道没有 EntityTrack payload
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
                Entity entity =
                        ActorPreviewManager.getInstance().getPreview(actor.getActorId());
                if (entity != null) {
                    // 计算 deltaTime
                    float deltaTime;
                    if (lastTime < 0f) {
                        // 第一帧
                        deltaTime = 1f / 20f; // 默认 20 TPS
                    } else {
                        deltaTime = time - lastTime;
                        if (deltaTime <= 0f || deltaTime > 1f) {
                            deltaTime = 1f / 20f; // 限制在合理范围内
                        }
                    }

                    applySample(entity, sample, lastSample, deltaTime);
                    
                    // 5️⃣ 将 ActorRuntime 传递给 PreviewEntity（供 Renderer 使用）
                    if (entity instanceof com.chronoblocks.actor.preview.PreviewActorEntity previewEntity) {
                        previewEntity.setActorRuntime(actorRuntime);
                    }
                    
                    // 6️⃣ 更新 Prop Runtime（每帧更新道具 Transform） */
                    ActorPropRuntime propRuntime = propRuntimes.get(actor.getActorId());
                    if (propRuntime != null) {
                        propRuntime.update();
                    }
                }
            }

            lastSample = sample;
            lastTime = time;
        }

        /**
         * 处理缺失的 Actor
         *
         * @param ref ActorRef
         */
        private void handleMissingActor(ActorRef ref) {
            // Actor 被删除，但 Timeline 还在
            // 安全策略：移除 Preview 和 PropRuntime
            if (ref != null) {
                UUID actorId = ref.getActorId();
                ActorPreviewManager.getInstance().removePreview(actorId);
                
                // 清理 PropRuntime
                ActorPropRuntime propRuntime = propRuntimes.remove(actorId);
                if (propRuntime != null) {
                    propRuntime.dispose();
                }
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
                Entity entity = ActorPreviewManager.getInstance().ensurePreview(actor);
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
         * @param deltaTime 时间增量（秒）
         */
        private void applySample(
                Entity entity,
                EntitySample s,
                EntitySample prev,
                float deltaTime
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

            // === RigProfile：应用动作语义到 Runtime 表现 ===
            applyRigProfile(s, deltaTime);

            // === Action（一次性触发）===
            applyActionEvents(s, prev);
        }

        /**
         * 应用 RigProfile（动作语义 → Runtime 表现）。
         *
         * <p>这是整个"多物种统一导演系统"的核心。
         *
         * @param s 当前 Sample
         * @param deltaTime 时间增量（秒）
         */
        private void applyRigProfile(EntitySample s, float deltaTime) {
            if (actor == null) {
                return;
            }

            ActorRigProfile rig = actor.getRigProfile();
            if (rig == null) {
                return;
            }

            ActorActionType action = s.action;
            if (action == null) {
                action = ActorActionType.IDLE;
            }

            // 检查 Rig 是否支持该动作
            if (!rig.supports(action)) {
                // 不支持的动作，重置姿态
                actorRuntime.resetPose();
                return;
            }

            // 应用动作语义到 Runtime 表现
            rig.applyAction(
                    actorRuntime,
                    action,
                    s.actionIntensity,
                    deltaTime
            );

            // 注意：actorRuntime 的参数会通过 PreviewActorEntity.getActorRuntime() 传递给 Renderer
            // Renderer 层负责将这些抽象参数映射到具体的 ModelPart（参见 Renderer Mixin 示例）
        }

        /**
         * 应用 LookAt
         *
         * @param entity PreviewActorEntity
         * @param s EntitySample
         */
        private void applyLookAt(Entity entity, EntitySample s) {
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

            // 同时更新 ActorRuntime 的 headYaw 和 headPitch（供 RigProfile / Renderer 使用）
            if (entity instanceof com.chronoblocks.actor.preview.PreviewActorEntity previewEntity) {
                com.chronoblocks.actor.runtime.ActorRuntime runtime = previewEntity.getActorRuntime();
                if (runtime != null) {
                    // 转换为弧度
                    runtime.setHeadYaw((float) Math.toRadians(yaw - currentYaw));
                    runtime.setHeadPitch((float) Math.toRadians(pitch - currentPitch));
                }
            }
        }

        /**
         * 应用 State
         *
         * @param entity PreviewActorEntity
         * @param s EntitySample
         */
        private void applyState(Entity entity, EntitySample s) {
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
            if (s.actionEvents.isEmpty()) {
                return;
            }

            Entity entity = ActorPreviewManager.getInstance().getPreview(actor.getActorId());
            if (entity == null) {
                return;
            }

            // 处理 Action 事件
            for (EntityActionEvent e : s.actionEvents) {
                LOGGER.debug("[ActorRuntime] Actor {} 触发 Action: {}", actor.getName(), e.type());
                
                // 处理 Prop 相关 Action
                if (e.type() == com.chronoblocks.ui.timeline.model.entity.EntityActionClip.ActionType.ATTACH_PROP
                        || e.type() == com.chronoblocks.ui.timeline.model.entity.EntityActionClip.ActionType.DETACH_PROP
                        || e.type() == com.chronoblocks.ui.timeline.model.entity.EntityActionClip.ActionType.SWITCH_PROP) {
                    applyPropAction(actor, entity, e);
                }
                
                // TODO: 未来可以在这里触发声音、粒子效果等
            }
        }

        /**
         * 应用 Prop Action
         *
         * @param actor Actor
         * @param entity PreviewActorEntity
         * @param event Action 事件
         */
        private void applyPropAction(Actor actor, Entity entity, EntityActionEvent event) {
            ActorPropRuntime propRuntime = getOrCreatePropRuntime(actor);
            propRuntime.setActorEntity(entity);

            var params = event.params();
            if (params == null) {
                return;
            }

            switch (event.type()) {
                case ATTACH_PROP -> {
                    String propId = (String) params.get("propId");
                    String slotStr = (String) params.get("slot");
                    
                    if (propId == null || slotStr == null) {
                        LOGGER.warn("[ActorRuntime] ATTACH_PROP 缺少必要参数: propId={}, slot={}", propId, slotStr);
                        return;
                    }

                    PropRegistry registry = PropRegistry.getInstance();
                    var descriptorOpt = registry.get(propId);
                    if (descriptorOpt.isEmpty()) {
                        LOGGER.warn("[ActorRuntime] 道具不存在: {}", propId);
                        return;
                    }

                    ActorPropSlot slot;
                    try {
                        slot = ActorPropSlot.valueOf(slotStr);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[ActorRuntime] 无效的槽位: {}", slotStr);
                        return;
                    }

                    // 获取局部偏移和旋转（可选）
                    Vector3f offset = new Vector3f(0, 0, 0);
                    Vector3f rotation = new Vector3f(0, 0, 0);
                    
                    if (params.containsKey("offsetX")) {
                        offset.x = ((Number) params.get("offsetX")).floatValue();
                    }
                    if (params.containsKey("offsetY")) {
                        offset.y = ((Number) params.get("offsetY")).floatValue();
                    }
                    if (params.containsKey("offsetZ")) {
                        offset.z = ((Number) params.get("offsetZ")).floatValue();
                    }
                    
                    if (params.containsKey("rotationX")) {
                        rotation.x = ((Number) params.get("rotationX")).floatValue();
                    }
                    if (params.containsKey("rotationY")) {
                        rotation.y = ((Number) params.get("rotationY")).floatValue();
                    }
                    if (params.containsKey("rotationZ")) {
                        rotation.z = ((Number) params.get("rotationZ")).floatValue();
                    }

                    RuntimeProp prop = new RuntimeProp(descriptorOpt.get(), slot);
                    prop.setLocalOffset(offset.x, offset.y, offset.z);
                    prop.setLocalRotation(rotation.x, rotation.y, rotation.z);

                    propRuntime.attach(slot, prop);
                    LOGGER.info("[ActorRuntime] 装备道具: {} -> {} ({})", actor.getName(), propId, slot);
                }

                case DETACH_PROP -> {
                    String slotStr = (String) params.get("slot");
                    if (slotStr == null) {
                        LOGGER.warn("[ActorRuntime] DETACH_PROP 缺少 slot 参数");
                        return;
                    }

                    ActorPropSlot slot;
                    try {
                        slot = ActorPropSlot.valueOf(slotStr);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[ActorRuntime] 无效的槽位: {}", slotStr);
                        return;
                    }

                    propRuntime.detach(slot);
                    LOGGER.info("[ActorRuntime] 卸下道具: {} -> {}", actor.getName(), slot);
                }

                case SWITCH_PROP -> {
                    // 切换道具 = 先卸下，再装备
                    String oldSlotStr = (String) params.get("oldSlot");
                    String newPropId = (String) params.get("newPropId");
                    String newSlotStr = (String) params.get("newSlot");

                    if (oldSlotStr != null) {
                        try {
                            ActorPropSlot oldSlot = ActorPropSlot.valueOf(oldSlotStr);
                            propRuntime.detach(oldSlot);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("[ActorRuntime] 无效的旧槽位: {}", oldSlotStr);
                        }
                    }

                    if (newPropId != null && newSlotStr != null) {
                        // 创建新的 ATTACH_PROP 事件并处理
                        var attachParams = new java.util.HashMap<String, Object>();
                        attachParams.put("propId", newPropId);
                        attachParams.put("slot", newSlotStr);
                        // 复制偏移和旋转参数（如果有）
                        if (params.containsKey("offsetX")) attachParams.put("offsetX", params.get("offsetX"));
                        if (params.containsKey("offsetY")) attachParams.put("offsetY", params.get("offsetY"));
                        if (params.containsKey("offsetZ")) attachParams.put("offsetZ", params.get("offsetZ"));
                        if (params.containsKey("rotationX")) attachParams.put("rotationX", params.get("rotationX"));
                        if (params.containsKey("rotationY")) attachParams.put("rotationY", params.get("rotationY"));
                        if (params.containsKey("rotationZ")) attachParams.put("rotationZ", params.get("rotationZ"));

                        EntityActionEvent attachEvent = new EntityActionEvent(
                                com.chronoblocks.ui.timeline.model.entity.EntityActionClip.ActionType.ATTACH_PROP,
                                attachParams
                        );
                        applyPropAction(actor, entity, attachEvent);
                    }

                    LOGGER.info("[ActorRuntime] 切换道具: {} -> {} -> {}", actor.getName(), oldSlotStr, newPropId);
                }

                case THROW_PROP -> {
                    // 投掷道具
                    String slotStr = (String) params.get("slot");
                    if (slotStr == null) {
                        LOGGER.warn("[ActorRuntime] THROW_PROP 缺少 slot 参数");
                        return;
                    }

                    ActorPropSlot slot;
                    try {
                        slot = ActorPropSlot.valueOf(slotStr);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[ActorRuntime] 无效的槽位: {}", slotStr);
                        return;
                    }

                    // 1. 从 Actor 手中卸下道具
                    RuntimeProp prop = propRuntime.getProp(slot);
                    if (prop == null) {
                        LOGGER.warn("[ActorRuntime] THROW_PROP: Actor {} 的槽位 {} 没有道具", actor.getName(), slot);
                        return;
                    }

                    // 获取当前道具位置（作为起始位置）
                    Vec3d startPos = prop.getWorldPosition();
                    if (startPos == null) {
                        // 如果道具还没有世界位置，使用 Actor 位置
                        startPos = new Vec3d(
                                entity.getX(),
                                entity.getY(),
                                entity.getZ()
                        );
                    }

                    // 2. 获取目标位置
                    Object targetXObj = params.get("targetX");
                    Object targetYObj = params.get("targetY");
                    Object targetZObj = params.get("targetZ");

                    if (targetXObj == null || targetYObj == null || targetZObj == null) {
                        LOGGER.warn("[ActorRuntime] THROW_PROP 缺少目标位置参数");
                        return;
                    }

                    Vec3d targetPos = new Vec3d(
                            ((Number) targetXObj).doubleValue(),
                            ((Number) targetYObj).doubleValue(),
                            ((Number) targetZObj).doubleValue()
                    );

                    // 3. 获取抛物线参数
                    float arcHeight = 1.5f;
                    if (params.containsKey("arcHeight")) {
                        arcHeight = ((Number) params.get("arcHeight")).floatValue();
                    }

                    float flightTime = 1.0f;
                    if (params.containsKey("flightTime")) {
                        flightTime = ((Number) params.get("flightTime")).floatValue();
                    }

                    // 4. 卸下道具（从 Actor 手中移除）
                    propRuntime.detach(slot);

                    // 5. 创建投掷物
                    RuntimeProjectile projectile = new RuntimeProjectile(
                            prop,
                            startPos,
                            targetPos,
                            arcHeight,
                            flightTime
                    );

                    projectileManager.spawn(projectile);
                    LOGGER.info("[ActorRuntime] 投掷道具: {} -> {} ({}, {}, {})",
                            actor.getName(), prop.getDescriptor().getId(),
                            targetPos.x, targetPos.y, targetPos.z);
                }

                default -> {
                    // 其他 Action 类型不处理
                }
            }
        }

        /**
         * 获取或创建 Actor 的 PropRuntime
         *
         * @param actor Actor
         * @return ActorPropRuntime
         */
        private ActorPropRuntime getOrCreatePropRuntime(Actor actor) {
            if (actor == null) {
                return null;
            }

            return propRuntimes.computeIfAbsent(
                    actor.getActorId(),
                    k -> new ActorPropRuntime()
            );
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

