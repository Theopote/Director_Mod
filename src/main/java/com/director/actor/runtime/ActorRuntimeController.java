package com.director.actor.runtime;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRef;
import com.director.actor.action.ActorActionType;
import com.director.actor.animation.AnimationDataSampler;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.rig.*;
import net.minecraft.entity.Entity;
import com.director.actor.prop.*;
import com.director.timeline.actor.ActorTimelineSource;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;
import com.director.timeline.playback.entity.EntityActionEvent;
import net.minecraft.util.math.Vec3d;
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
 *   <li>✅ 遍历 Timeline 中的 ActorTrack</li>
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
 *  └─ ActorTrack
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
 * @author Director Team
 * @version 2.0
 */
public final class ActorRuntimeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRuntimeController.class);
    private static final ActorRuntimeController INSTANCE = new ActorRuntimeController();

    static boolean previewActive(EntitySample sample) { return sample!=null && sample.enabled && sample.active; }

    public static ActorRuntimeController getInstance() {
        return INSTANCE;
    }

    private ActorTimelineSource timelineSource;
    private ActorRegistry actorRegistry;
    private ActorTimelineSource.TrackStructureListener trackStructureListener;

    /** 每条 ActorTrack 一个 Sampler Runtime */
    private final Map<TimelineTrack, TrackRuntime> runtimes = new IdentityHashMap<>();

    /** 每个 Actor 一个 PropRuntime */
    private final Map<UUID, ActorPropRuntime> propRuntimes = new HashMap<>();

    /** 投掷物管理器 */
    private final ProjectileRuntimeManager projectileManager = ProjectileRuntimeManager.getInstance();

    /** 上一帧时间（用于计算 deltaTime） */
    private float lastUpdateTime = 0f;
    private long previewWorldRevision=-1;

    private boolean synchronizePreviewWorld() {
        var previews=ActorPreviewManager.getInstance();previews.syncWorld();
        if(previewWorldRevision==previews.worldRevision()) return false;
        releaseWorldResources();previewWorldRevision=previews.worldRevision();return true;
    }
    /** Runs even when the timeline is paused or the Director screen is closed. */
    public void tickWorld() {
        if(synchronizePreviewWorld() && ActorPreviewManager.getInstance().hasWorld()) update();
    }
    public void clearWorldPreviews() {
        var previews=ActorPreviewManager.getInstance();previews.disconnectWorld();
        releaseWorldResources();previewWorldRevision=previews.worldRevision();
    }
    private void releaseWorldResources() {
        if(timelineSource!=null) timelineSource.invalidateActorEditContext();
        diagnostics.clear();
        propRuntimes.values().forEach(ActorPropRuntime::dispose);propRuntimes.clear();
        projectileManager.clear();
        com.director.actor.model.CustomModelPreviewRenderer.getInstance().clear();
        for(var runtime:runtimes.values()) runtime.resetWorldResources();
        lastUpdateTime=timelineSource==null?0:timelineSource.getCurrentTime();
    }


    /** 总开关 */
    private boolean enabled = true;
    private final ActorRuntimeDiagnostics diagnostics = new ActorRuntimeDiagnostics();
    public ActorRuntimeDiagnostics diagnostics() { return diagnostics; }

    /** Re-evaluate pose/constraints with a pure animation snapshot; never consume or dispatch events. */
    public boolean refreshPosePreview(UUID actorId) {
        synchronizePreviewWorld();
        if (!ActorPreviewManager.getInstance().hasWorld()) return false;
        if (!enabled || timelineSource==null || actorId==null
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()) return false;
        var boneGizmo=com.director.ui.actor.pose.ActorBoneGizmoController.getInstance();
        var boneDraft=boneGizmo.isDragging()?boneGizmo.previewAnimationDraft(actorId,timelineSource.getCurrentTime()):null;
        if(boneGizmo.isDragging() && boneDraft==null) return false;
        TrackRuntime target=null;
        for (var runtime:runtimes.values()) if(runtime.actor!=null && actorId.equals(runtime.actor.getActorId())) {
            if(target!=null) return false;
            target=runtime;
        }
        if(target==null || target.lastSample==null || target.lastTime!=timelineSource.getCurrentTime()
                || !target.lastSample.enabled || !target.lastSample.active || target.lastSample.visibility<=.01f) return false;
        var entity=ActorPreviewManager.getInstance().getPreview(actorId);
        if(entity==null) return false;
        var sample=target.lastSample.copy();
        var animation=target.sampler.sampleSnapshot(timelineSource.getCurrentTime());
        sample.animationId=animation.animationId;sample.animationIsPreset=animation.animationIsPreset;sample.animationSource=animation.animationSource;
        sample.animationLocalTime=animation.animationLocalTime;sample.animationLoop=animation.animationLoop;
        sample.animationSpeed=animation.animationSpeed;sample.animationBlendSettings=animation.animationBlendSettings;
        sample.animationBlendEnvelope=animation.animationBlendEnvelope;
        sample.presetAnimation=animation.presetAnimation;
        // Root offsets must always start at the sampled world position, never the prior preview offset.
        entity.refreshPositionAndAngles(sample.position.x,sample.position.y,sample.position.z,sample.rotation.x,sample.rotation.y);
        target.applyRigProfile(sample,0,boneDraft);
        target.updateGaze();
        target.syncCustomModel(entity);
        return true;
    }

    private ActorRuntimeController() {
    }

    // =========================================================
    // 绑定 Model / Registry
    // =========================================================

    /**
     * 绑定时间线源和 ActorRegistry
     *
     * @param source 演员轨道视图（通常为 {@link com.director.timeline.model.TimelineModel}）
     * @param registry ActorRegistry
     */
    public void bind(ActorTimelineSource source, ActorRegistry registry) {
        if (this.timelineSource != null && trackStructureListener != null) {
            this.timelineSource.removeTrackStructureListener(trackStructureListener);
        }
        this.timelineSource = source;
        this.actorRegistry = registry;
        if (this.timelineSource != null) {
            trackStructureListener = this::rebuildRuntimes;
            this.timelineSource.addTrackStructureListener(trackStructureListener);
        } else {
            trackStructureListener = null;
        }
        rebuildRuntimes();
        LOGGER.info("[ActorRuntime] 绑定 ActorTimelineSource 和 ActorRegistry，runtimes={}", runtimes.size());
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            diagnostics.clear();
            ActorPreviewManager.getInstance().clear();
            ProjectileRuntimeManager.getInstance().clear();
            com.director.actor.model.CustomModelPreviewRenderer.getInstance().clear();
            propRuntimes.values().forEach(ActorPropRuntime::dispose); propRuntimes.clear();
            LOGGER.info("[ActorRuntime] 已禁用，清空所有预览实体和投掷物");
        } else {
            LOGGER.info("[ActorRuntime] 已启用");
        }
    }

    /*
      检查是否启用

      @return 如果启用则返回 true
     */
    /** Release only prop visual resources when leaving a client world. */
    public void clearPropPreviews() {
        diagnostics.clear();
        propRuntimes.values().forEach(ActorPropRuntime::dispose); propRuntimes.clear();
    }

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
        synchronizePreviewWorld();
        if (!ActorPreviewManager.getInstance().hasWorld()) return;
        if (!enabled || timelineSource == null || actorRegistry == null) {
            diagnostics.clear(); return;
        }

        float time = timelineSource.getCurrentTime();
        diagnostics.begin(time);
        if (!Float.isFinite(time) || time < 0) {
            propRuntimes.values().forEach(ActorPropRuntime::dispose); propRuntimes.clear();
            return;
        }
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

        // Resolve gaze only after every actor has its current-time root position.
        for (TrackRuntime rt : runtimes.values()) rt.updateGaze();
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
        diagnostics.clear();
        runtimes.clear();
        pruneDeadTracks();
        propRuntimes.values().forEach(ActorPropRuntime::dispose); propRuntimes.clear();

        if (timelineSource == null) {
            return;
        }

        for (TimelineTrack track : timelineSource.getTracks()) {
            if (isActorTrack(track)) {
                // 检查是否有 ActorTrack payload
                ActorTrack actorTrack = track.getActorTrackPayload();
                if (actorTrack != null) {
                    try {
                        runtimes.put(track, new TrackRuntime(track));
                    } catch (IllegalArgumentException e) {
                        // 如果轨道没有有效的 ActorTrack payload，跳过它
                        LOGGER.warn("[ActorRuntime] 跳过无效的演员轨道: {} - {}", track.getName(), e.getMessage());
                    }
                } else {
                    LOGGER.warn("[ActorRuntime] 跳过没有 ActorTrack payload 的轨道: {}", track.getName());
                }
            }
        }
    }

    /**
     * 清理已删除的轨道
     */
    private void pruneDeadTracks() {
        if (timelineSource == null) {
            ActorPreviewManager.getInstance().clear();
            com.director.actor.model.CustomModelPreviewRenderer.getInstance().clear();
            return;
        }
        var tracks=java.util.List.copyOf(timelineSource.getTracks());
        var trackSet=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TimelineTrack,Boolean>());
        trackSet.addAll(tracks);
        runtimes.keySet().removeIf(t -> !trackSet.contains(t));
        var liveActors = new java.util.HashSet<UUID>();
        for (var track : tracks) {
            var payload = track.getActorTrackPayload();
            if (isActorTrack(track) && !track.isMuted() && payload != null && payload.getActorRef() != null
                    && actorRegistry != null && actorRegistry.resolve(payload.getActorRef()).isPresent()
                    && payload.isEnabled() && payload.getClips().stream().anyMatch(clip ->
                        clip instanceof com.director.timeline.model.entity.EntitySpawnClip && !clip.isMuted()
                        && timelineSource.getCurrentTime() >= clip.getStartTime()
                        && timelineSource.getCurrentTime() <= clip.getEndTime()))
                liveActors.add(payload.getActorRef().getActorId());
        }
        for (var id : ActorPreviewManager.getInstance().previewActorIds()) if (!liveActors.contains(id)) {
            ActorPreviewManager.getInstance().removePreview(id);
            com.director.actor.model.CustomModelPreviewRenderer.getInstance().remove(id);
        }
        for (var id : new java.util.ArrayList<>(propRuntimes.keySet())) if (!liveActors.contains(id))
            propRuntimes.remove(id).dispose();
    }

    /**
     * 检查是否为演员轨道
     *
     * @param track TimelineTrack
     * @return 如果是演员轨道则返回 true
     */
    private boolean isActorTrack(TimelineTrack track) {
        return track != null
                && track.getType() != null
                && track.getType() == com.director.timeline.model.TrackType.ACTOR;
    }

    // =========================================================
    // TrackRuntime（一条演员轨道的运行时）
    // =========================================================

    /**
     * 一条演员轨道的运行时
     */
    private final class TrackRuntime {

        private final ActorTrack actorTrack;
        private final TimelineTrack ownerTrack;
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
        private com.director.actor.path.GroundRoute groundRoute;
        private com.director.timeline.model.entity.ActorRouteClip groundClip;
        private long groundRevision = -1;
        private net.minecraft.world.World groundWorld;
        private com.director.actor.path.ActorCollisionDimensions groundDimensions;

        /**
         * 构造函数
         *
         * @param track TimelineTrack
         * @throws IllegalArgumentException 如果轨道没有 ActorTrack payload
         */
        TrackRuntime(TimelineTrack track) {
            this.ownerTrack = track;
            this.actorTrack = track.getActorTrackPayload();
            if (this.actorTrack == null) {
                throw new IllegalArgumentException("Track does not have ActorTrack payload");
            }
            this.sampler = new EntityClipSampler(actorTrack);
        }

        /**
         * 更新运行时（每帧调用）
         *
         * @param time 当前时间（秒）
         */
        private void resetWorldResources() {
            spawned=false;lastSample=null;lastTime=-1;
            groundRoute=null;groundClip=null;groundWorld=null;groundDimensions=null;groundRevision=-1;
            // Keep sampler event caches: loading a new world is not rewinding or replaying events.
        }

        void update(float time) {
            if (ownerTrack.isMuted()) return;
            // 1️⃣ 解析 Actor
            ActorRef ref = actorTrack.getActorRef();
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
                diagnostics.report(ref.getActorId(),ownerTrack,time,"binding",true,"轨道绑定的演员不存在，请重新绑定演员");
                return;
            }

            // 2️⃣ 采样
            EntitySample sample = sampler.sampleAt(time);
            if (sample == null) {
                handleSpawnLifecycle(EntitySample.disabled());
                var staleProps = propRuntimes.remove(actor.getActorId());
                if (staleProps != null) staleProps.dispose();
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

                    applyGroundRoute(actor.getActorId(), sample, time);
                    ActorRelationTimeResolver.resolveInto(actor.getActorId(),sample,time,this::sampleRelationTarget,this::constrainRelationPlacement,
                            (id,message)->reportPlacement(id,time,"relation",false,message));
                    restoreRelationJump(sample,time);
                    applySample(entity, sample, lastSample, deltaTime);
                    
                    // 5️⃣ 将 ActorRuntime 传递给 PreviewEntity（供 Renderer 使用）
                    if (entity instanceof com.director.actor.preview.ActorPosePreview previewEntity) {
                        previewEntity.setActorRuntime(actorRuntime);
                    }
                    
                    // 6️⃣ 更新 Prop Runtime（每帧更新道具 Transform） */
                    ActorPropRuntime propRuntime = getOrCreatePropRuntime(actor);
                    propRuntime.setActorEntity(entity);
                    var propState = com.director.actor.prop.ActorPropTimelineSampler.sampleTransforms(actorTrack,time,
                            (eventTime,slot,attachment) -> samplePropLaunchTransform(entity,eventTime,slot,attachment),
                            (eventTime,reason)->reportPlacement(actor.getActorId(),eventTime,"prop",true,reason.legacyMessage()));
                    propRuntime.sync(propState);
                    propRuntime.setHeldVisible(sample.visibility > .01f);
                    if (propRuntime != null) {
                        propRuntime.update(reason->reportPlacement(actor.getActorId(),time,"prop",true,reason.legacyMessage()));
                    }
                }
            }

            if (!spawned) {
                var staleProps = propRuntimes.remove(actor.getActorId());
                if (staleProps != null) staleProps.dispose();
            }
            lastSample = sample;
            lastTime = time;
        }

        private void reportPlacement(UUID id,float time,String stage,boolean hidden,String message) {
            for(var track:timelineSource.getTracks()) {
                var payload=track.getActorTrackPayload();
                if(!track.isMuted() && payload!=null && payload.getActorRef()!=null && id.equals(payload.getActorRef().getActorId())) {
                    diagnostics.report(id,track,time,stage,hidden,message); return;
                }
            }
        }

        private org.joml.Vector3f constrainRelationPlacement(UUID id,float time,EntitySample sample,org.joml.Vector3f requested) {
            var terrain=terrainFor(id,sample);
            if (terrain==null) { reportPlacement(id,time,"relation",true,"关系地形或演员碰撞尺寸尚未就绪"); return null; }
            var result=com.director.actor.path.ActorRelationGroundConstraint.project(
                    new Vec3d(sample.position.x,sample.position.y,sample.position.z),
                    new Vec3d(requested.x,requested.y,requested.z),terrain);
            reportPlacement(id,time,"relation",result.position()==null,result.warning());
            if (!result.warning().isEmpty()) { sample.speed=0; EntityClipSampler.resolveAutoAction(sample); }
            if (result.position()==null) return null;
            var point=result.position(); return new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z);
        }

        private EntitySample sampleRelationTarget(UUID id, Float time) {
            if (actorRegistry.getActor(id).isEmpty()) return null;
            // Timeline order chooses the binding; runtime-map iteration must not choose it.
            for (var track : timelineSource.getTracks()) {
                var payload=track.getActorTrackPayload();
                if (track.isMuted() || payload==null || payload.getActorRef()==null || !id.equals(payload.getActorRef().getActorId())) continue;
                var target=new EntityClipSampler(payload).sampleSnapshot(time);
                var runtime=runtimes.get(track);
                if (target!=null && runtime!=null) runtime.applyGroundRoute(id,target,time);
                return target;
            }
            return null;
        }

        private org.joml.Matrix4f samplePropLaunchTransform(Entity entity, float time, ActorPropSlot slot,
                com.director.actor.prop.ActorPropTimelineSampler.Attachment attachment) {
            var at = new EntityClipSampler(actorTrack).sampleSnapshot(time);
            var rig = com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
            if (at == null || rig == null || !com.director.actor.ActorBehaviorCapabilities.supportsLegacyPose(actor)) return null;
            applyGroundRoute(actor.getActorId(),at,time);
            ActorRelationTimeResolver.resolveInto(actor.getActorId(),at,time,this::sampleRelationTarget,this::constrainRelationPlacement,
                            (id,message)->reportPlacement(id,time,"relation",false,message));
            restoreRelationJump(at,time);
            if (at.visibility<=.01f) return null;
            ActorRelationTimeResolver.applyWorldLookRotation(at);
            var base = ActorRouteGait.sample(at,actor.getRigProfile(),new ActorRuntime()).pose;
            base=ActorRouteGait.settle(base,at,actor.getRigProfile());
            var pose = com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter.convert(base,rig);
            if(actor.getType()==com.director.actor.ActorType.PLAYER) {
                ActorRouteGait.applyJoints(pose,at,actor.getRigProfile(),!at.poseOverrideArms,!at.poseOverrideLegs);
                ActorRouteTurnPose.apply(pose,at);
            }
            var asset = com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(at.animationId)
                    .filter(value -> com.director.actor.rig.ActorRigCompatibility.accepts(actor,value.rigId()));
            var primaryClock=ActorPresetClock.resolve(com.director.timeline.playback.entity.ActorAnimationLayerSample.from(at),at,actor.getRigProfile());
            var authored = asset.isPresent()
                    ? asset.get().sample(at.animationLocalTime*Math.max(.01f,at.animationSpeed),
                            at.animationLoop?asset.get().loopMode():com.director.actor.animation.asset.AnimationLoopMode.ONCE)
                    : AnimationDataSampler.sampleRig(primaryClock.id(),rig,primaryClock.localTime(),primaryClock.loop(),primaryClock.speed());
            pose=ActorRigPoseComposition.prependPreset(pose,ActorPresetClock.resolve(at.presetAnimation,at,actor.getRigProfile()),
                    diagnostic -> reportPlacement(actor.getActorId(),time,"preset-layer",false,
                            "预设动作骨架与当前手工动画不兼容，已跳过预设。"),new java.util.HashMap<>());
            if (authored != null) {
                pose=ActorRigPoseComposition.compose(pose,authored,
                        value -> applyRigPoseOverrides(value,at),diagnostic -> reportPlacement(actor.getActorId(),time,
                                "pose-layer",false,"骨骼动作层与当前演员不兼容，已跳过该层。"),at.animationBlendSettings,primaryClock.envelope());
            }
            if (authored == null) applyRigPoseOverrides(pose,at);
            pose.setLimbBendMode(actor.getLimbBendMode());
            var root=pose.localTransform(com.director.actor.rig.skeleton.HumanoidRig.ROOT);
            if(root.translation().lengthSquared()>1e-10f) {
                var finalPosition=finalRootPlacement(new Vec3d(at.position.x,at.position.y,at.position.z),root.translation(),at.rotation.x,at,time);
                if(finalPosition.position()==null) return null;
                var feet=finalPosition.position(); at.position.set((float)feet.x,(float)feet.y,(float)feet.z);
                pose.setLocalTransform(com.director.actor.rig.skeleton.HumanoidRig.ROOT,
                        new com.director.actor.rig.skeleton.BoneTransform(new org.joml.Vector3f(),root.rotation(),root.scale()));
            }
            var world = new org.joml.Matrix4f().translate(at.position)
                    .rotateY((float)Math.toRadians(-at.rotation.x)).scale(at.scale);
            var socket = com.director.actor.prop.ActorPropSocketResolver.resolve(pose,world,slot,
                    new org.joml.Vector3f(attachment.x(),attachment.y(),attachment.z()),
                    new org.joml.Vector3f(attachment.rx(),attachment.ry(),attachment.rz()));
            return socket.orElse(null);
        }

        private com.director.actor.path.ActorGroundTerrain terrainFor(UUID id,EntitySample sample) {
            var client=net.minecraft.client.MinecraftClient.getInstance();
            var definition=actorRegistry.getActor(id).orElse(null);
            if(client==null || client.world==null || definition==null) return null;
            try {
                return new com.director.actor.path.ActorGroundTerrain(client.world,
                        com.director.actor.path.ActorCollisionDimensions.forActor(definition,sample.scale));
            } catch(IllegalArgumentException invalid) { return null; }
        }

        private void applyGroundRoute(UUID id, EntitySample sample, float time) {
            // Authored actor paths are exact 3D curves, independent of world terrain.
            if (sample.routeClip != null) sample.routeClip.setTerrainWarning("");
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
                com.director.actor.model.CustomModelPreviewRenderer.getInstance().remove(actorId);
                
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

            if (previewActive(sample) && (!spawned || !ActorPreviewManager.getInstance().hasPreview(actor.getActorId()))) {
                // 需要生成预览实体
                Entity entity = ActorPreviewManager.getInstance().ensurePreview(actor);
                if (entity != null) {
                    spawned = true;
                    LOGGER.debug("[ActorRuntime] Spawn 预览实体: {}", actor.getName());
                } else {
                    diagnostics.report(actor.getActorId(),ownerTrack,timelineSource.getCurrentTime(),"preview",true,"预览实体无法创建，请检查世界加载状态和演员类型");
                }
            }

            if (!previewActive(sample) && (spawned || ActorPreviewManager.getInstance().hasPreview(actor.getActorId()))) {
                // 需要移除预览实体
                ActorPreviewManager.getInstance().removePreview(actor.getActorId());
                com.director.actor.model.CustomModelPreviewRenderer.getInstance().remove(actor.getActorId());
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
            // Gizmo 拖动期间跳过 Transform 写回，避免与 applyLivePreview 打架
            UUID actorId = actor != null ? actor.getActorId() : null;
            boolean gizmoDragging = actorId != null
                    && com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                    && actorId.equals(com.director.ui.actor.gizmo.ActorGizmoController.getInstance().getTargetActorId());
            var liveScale=com.director.ui.actor.gizmo.ActorGizmoController.getInstance().previewScale(actorId);
            ActorPreviewManager.getInstance().setRootScale(entity,liveScale==null ? s.scale : liveScale);
            if (entity instanceof com.director.actor.preview.ActorPosePreview previewEntity) {
                var scaleDraft = com.director.ui.actor.gizmo.ActorGizmoController.getInstance().previewScale(actorId);
                previewEntity.setActorTransformScale(scaleDraft == null ? s.scale : scaleDraft);
            }

            // === Visibility ===
            entity.setInvisible(s.visibility <= 0.01f);

            // === Transform ===
            if (!gizmoDragging) {
                entity.setBodyYaw(s.rotation.x);
                entity.setHeadYaw(s.rotation.x);
                entity.refreshPositionAndAngles(
                        s.position.x,
                        s.position.y,
                        s.position.z,
                        s.rotation.x,
                        s.rotation.y
                );
            }

            // Runtime samples already interpolate in scene time. Vanilla tick interpolation
            // must not blend against stale previous angles on client-only actors.
            entity.lastYaw=entity.getYaw(); entity.lastPitch=entity.getPitch();
            if(entity instanceof net.minecraft.entity.LivingEntity living) {
                living.lastBodyYaw=living.bodyYaw; living.lastHeadYaw=living.headYaw;
            }
            // === State ===
            applyState(entity, s);

            // === RigProfile：应用动作语义到 Runtime 表现 ===
            applyRigProfile(s, deltaTime);
            entity.lastYaw=entity.getYaw(); entity.lastPitch=entity.getPitch();
            if(entity instanceof net.minecraft.entity.LivingEntity living) {
                living.lastBodyYaw=living.bodyYaw; living.lastHeadYaw=living.headYaw;
            }

            // === Action（一次性触发）===
            applyActionEvents(s, prev);

            // === 自定义方块拼接模型（非人形拓扑骨架的可视化回路）===
            if (s.visibility > 0.01f) syncCustomModel(entity);
            else if (actor != null) com.director.actor.model.CustomModelPreviewRenderer.getInstance().remove(actor.getActorId());
        }

        /**
         * 若演员绑定了自定义方块模型，按当前骨架世界姿态重新定位各个 cuboid，
         * 并隐藏作为锦点的 ArmorStand 宿主实体。
         */
        private void syncCustomModel(Entity entity) {
            if (actor == null || entity == null) return;
            String modelId = actor.getCustomModelId();
            if (modelId == null) return;
            var model = com.director.actor.model.CustomModelLibrary.getInstance().find(modelId).orElse(null);
            if (model == null) return;
            var rig = com.director.actor.rig.skeleton.RigDefinitions.find(model.rigId()).orElse(null);
            if (rig == null) return;
            com.director.actor.rig.skeleton.RigPose pose = null;
            if (entity instanceof com.director.actor.preview.ActorPosePreview preview) {
                var current = preview.getFinalRigPose();
                if (current != null && current.rig().boneCount() == rig.boneCount()) pose = current;
                entity.setInvisible(true); // 宿主 ArmorStand 仅作锦点，真正外观由 cuboid 提供
            }
            if (pose == null) pose = new com.director.actor.rig.skeleton.RigPose(rig);
            var actorTransform = new org.joml.Matrix4f()
                    .translate((float) entity.getX(), (float) entity.getY(), (float) entity.getZ())
                    .rotateY((float) Math.toRadians(-entity.getYaw()));
            var worldPose = com.director.actor.rig.skeleton.RigWorldPose.resolve(pose, actorTransform);
            if (entity.getEntityWorld() instanceof net.minecraft.client.world.ClientWorld clientWorld) {
                com.director.actor.model.CustomModelPreviewRenderer.getInstance()
                        .sync(actor.getActorId(), clientWorld, worldPose, model);
            }
        }

        /**
         * 应用 RigProfile（动作语义 → Runtime 表现）。
         *
         * <p>这是整个"多物种统一导演系统"的核心。
         *
         * <p>现在支持 Animation ↔ RigProfile 混合：
         * <ul>
         *   <li>有 Animation → 使用 Animation 的姿态（按部位）</li>
         *   <li>无 Animation → 使用 RigProfile 的姿态</li>
         *   <li>两者可混合 → Animation 覆盖它控制的部位</li>
         * </ul>
         *
         * @param s 当前 Sample
         * @param deltaTime 时间增量（秒）
         */
        private void applyRigProfile(EntitySample s, float deltaTime) {
            if(actor==null) return;
            applyRigProfile(s,deltaTime,com.director.ui.actor.pose.ActorBoneGizmoController.getInstance()
                    .previewAnimationDraft(actor.getActorId(),timelineSource.getCurrentTime()));
        }
        private void applyRigProfile(EntitySample s,float deltaTime,com.director.ui.actor.pose.ActorBoneAnimationDraft boneDraft) {
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

            // Sampling is a pure function of the timeline phase, including paused/rewound frames.
            Pose base = new Pose();
            RigPoseResult sampledRig = ActorRouteGait.sample(s,rig,actorRuntime);
            RigPoseResult rigPose = new RigPoseResult(ActorRouteGait.settle(sampledRig.pose,s,rig),sampledRig.mask);

            if(boneDraft!=null && !com.director.actor.rig.ActorRigCompatibility.accepts(actor,boneDraft.rig().id())) boneDraft=null;
            var presetInput=s.presetAnimation;
            com.director.ui.actor.pose.ActorBoneAnimationDraft presetDraft=null;
            if (boneDraft!=null && boneDraft.animationId()!=null) {
                if (presetInput!=null && boneDraft.animationId().equals(presetInput.id())) {
                    presetDraft=boneDraft; boneDraft=null;
                } else if(presetInput==null && s.animationId!=null && !boneDraft.animationId().equals(s.animationId)) {
                    presetInput=com.director.timeline.playback.entity.ActorAnimationLayerSample.from(s);
                }
            }
            presetInput=ActorPresetClock.resolve(presetInput,s,rig);
            var primaryClock=ActorPresetClock.resolve(com.director.timeline.playback.entity.ActorAnimationLayerSample.from(s),s,rig);
            var rigAsset = com.director.actor.animation.asset.AnimationAssetLibrary.getInstance()
                    .find(s.animationId)
                    .filter(asset -> com.director.actor.rig.ActorRigCompatibility.accepts(actor, asset.rigId()));
            var legacyRig = com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
            var legacyPose = rigAsset.isEmpty() && boneDraft == null
                    ? AnimationDataSampler.sampleRig(primaryClock.id(), legacyRig, primaryClock.localTime(),
                            primaryClock.loop(), primaryClock.speed()) : null;
            if (rigAsset.isPresent() || boneDraft!=null || legacyPose!=null || s.presetAnimation!=null && legacyRig!=null) {
                var definition=boneDraft!=null?boneDraft.rig():rigAsset.isPresent()?rigAsset.get().rig():legacyRig;
                var blend=boneDraft!=null?boneDraft.settings():s.animationBlendSettings;
                float envelope=boneDraft!=null?boneDraft.envelope():primaryClock.envelope();
                var proceduralPose = com.director.actor.rig.skeleton.HumanoidTopology.matches(definition)
                        ? com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter.convert(rigPose.pose, definition)
                        : null;
                if (actor.getType() == com.director.actor.ActorType.PLAYER)
                    ActorRouteGait.applyJoints(proceduralPose,s,rig,!s.poseOverrideArms,!s.poseOverrideLegs);
                if (actor.getType() == com.director.actor.ActorType.PLAYER) ActorRouteTurnPose.apply(proceduralPose,s);
                com.director.actor.rig.skeleton.RigPose authoredPose;
                if(boneDraft!=null) authoredPose=boneDraft.authoredPose();
                else if(legacyPose!=null) authoredPose=legacyPose;
                else if(rigAsset.isEmpty()) authoredPose=new com.director.actor.rig.skeleton.RigPose(definition);
                else {
                    var asset=rigAsset.orElseThrow();
                    var mode=s.animationLoop?asset.loopMode():com.director.actor.animation.asset.AnimationLoopMode.ONCE;
                    authoredPose=com.director.ui.actor.pose.ActorBonePropertiesPanel.previewAuthoredPose(
                            actor.getActorId(),asset.id(),asset.sample(s.animationLocalTime*Math.max(.01f,s.animationSpeed),mode));
                }
                var authoredWeights = new java.util.HashMap<String,Float>();
                if (proceduralPose == null) proceduralPose = new com.director.actor.rig.skeleton.RigPose(definition);
                String presetId=presetInput==null?null:presetInput.id();
                proceduralPose=ActorRigPoseComposition.prependPreset(proceduralPose,presetInput,
                        diagnostic -> reportPlacement(actor.getActorId(),timelineSource.getCurrentTime(),
                                "preset-layer",false,"预设动作骨架与当前手工动画不兼容，已跳过预设。"),authoredWeights,presetDraft,
                        pose->com.director.ui.actor.pose.ActorBonePropertiesPanel.previewAuthoredPose(actor.getActorId(),presetId,pose));
                var finalRigPose = ActorRigPoseComposition.compose(proceduralPose,
                        authoredPose, pose -> applyRigPoseOverrides(pose, s),
                        diagnostic -> reportPlacement(actor.getActorId(),timelineSource.getCurrentTime(),
                                "pose-layer",false,"骨骼动作层与当前演员不兼容，已跳过该层。"),blend,envelope);
                finalRigPose.setLimbBendMode(actor.getLimbBendMode());
                if (actor.getType() == com.director.actor.ActorType.PLAYER) {
                    for (int i=0;i<authoredPose.rig().boneCount();i++)
                        if (blend.contributes(authoredPose,i,envelope))
                            authoredWeights.merge(authoredPose.rig().bone(i).id(),blend.effectiveWeight(envelope),Math::max);
                    ActorRouteFootContact.apply(finalRigPose,s,rig,authoredWeights);
                }
                applyPoseToRuntime(rigPose.pose);
                Entity preview = ActorPreviewManager.getInstance().getPreview(actor.getActorId());
                if (preview instanceof com.director.actor.preview.ActorPosePreview previewEntity) {
                    boolean boneDragging = com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                            && actor.getActorId().equals(com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().getActorId());
                    if (!boneDragging || boneDraft!=null) {
                        applyRootAnimationOffset((Entity)previewEntity, finalRigPose,s);
                        previewEntity.setFinalRigPose(finalRigPose);
                    }
                }
                return;
            }

            // 3️⃣ Animation pose（明确动画，可能为 null）
            AnimationPoseResult animPose = getAnimationPose(s);

            // 4️⃣ Blend（混合）
            Pose finalPose;
            if (animPose != null) {
                // 有动画时，按部位混合
                finalPose = PoseBlender.blend(base, rigPose, animPose);
            } else {
                // 无动画时，直接使用 Rig
                finalPose = rigPose.pose;
            }

            applyPoseOverrides(finalPose, s);

            // 5️⃣ 将最终姿态应用到 ActorRuntime（向后兼容）
            applyPoseToRuntime(finalPose);

            // 6️⃣ 将最终姿态传递给 PreviewEntity（供 Renderer 使用）
            Entity entity = ActorPreviewManager.getInstance().getPreview(actor.getActorId());
            if (entity instanceof com.director.actor.preview.ActorPosePreview previewEntity) {
                boolean boneDragging = com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                        && actor.getActorId().equals(com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().getActorId());
                if (!boneDragging) {
                    if (actor.getType() == com.director.actor.ActorType.PLAYER) {
                        var contactPose = com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter.convert(finalPose,
                                com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION);
                        contactPose.setLimbBendMode(actor.getLimbBendMode());
                        if (animPose == null) ActorRouteGait.applyJoints(contactPose,s,rig,!s.poseOverrideArms,!s.poseOverrideLegs);
                        if (animPose == null) ActorRouteTurnPose.apply(contactPose,s);
                        if (animPose == null) ActorRouteFootContact.apply(contactPose,s,rig,java.util.Map.of());
                        previewEntity.setFinalRigPose(contactPose);
                    } else previewEntity.setFinalPose(finalPose);
                }
            }
        }

        private void applyPoseOverrides(Pose pose, EntitySample s) {
            if (pose == null || s == null || !s.poseOverrideEnabled
                    || !com.director.actor.ActorBehaviorCapabilities.supportsLegacyPose(actor)) {
                return;
            }
            if (s.poseOverrideBody) {
                pose.bodyPitch = s.poseBodyPitch;
                pose.bodyYaw = s.poseBodyYaw;
                pose.bodyRoll = s.poseBodyRoll;
            }
            if (s.poseOverrideHead) {
                pose.headPitch = s.poseHeadPitch;
                pose.headYaw = s.poseHeadYaw;
            }
            if (s.poseOverrideArms) {
                pose.leftArmPitch = s.poseLeftArmPitch;
                pose.leftArmYaw = s.poseLeftArmYaw;
                pose.leftArmRoll = s.poseLeftArmRoll;
                pose.rightArmPitch = s.poseRightArmPitch;
                pose.rightArmYaw = s.poseRightArmYaw;
                pose.rightArmRoll = s.poseRightArmRoll;
            }
            if (s.poseOverrideLegs) {
                pose.leftLegPitch = s.poseLeftLegPitch;
                pose.rightLegPitch = s.poseRightLegPitch;
            }
        }

        private void restoreRelationJump(EntitySample sample,float time) {
            // Route height is authored on the curve; legacy procedural hops are not applied.
        }

        private com.director.actor.path.ActorRelationGroundConstraint.Result finalRootPlacement(
                Vec3d base,org.joml.Vector3f offset,float yaw,EntitySample sample,float time) {
            if (sample.routeClip != null) {
                var worldOffset = new org.joml.Matrix4f().rotateY((float)Math.toRadians(-yaw)).scale(sample.scale)
                        .transformDirection(new org.joml.Vector3f(offset));
                return new com.director.actor.path.ActorRelationGroundConstraint.Result(
                        base.add(worldOffset.x,worldOffset.y,worldOffset.z), "");
            }
            var terrain=terrainFor(actor.getActorId(),sample);
            if(terrain==null) { reportPlacement(actor.getActorId(),time,"root",true,"动作地形尚未就绪");
                return new com.director.actor.path.ActorRelationGroundConstraint.Result(null,"动作地形尚未就绪"); }
            var worldOffset=new org.joml.Matrix4f().rotateY((float)Math.toRadians(-yaw)).scale(sample.scale)
                    .transformDirection(new org.joml.Vector3f(offset));
            double jump=sample.routeClip==null?0:com.director.actor.path.GroundRoute.jumpOffset(
                    Math.max(0,time-sample.routeClip.getStartTime()),sample.routeClip.getJumpStart(),sample.routeClip.getJumpDuration(),sample.routeClip.getJumpHeight());
            var result=com.director.actor.path.ActorFinalGroundPlacement.project(base,
                    new Vec3d(worldOffset.x,worldOffset.y,worldOffset.z),jump,terrain);
            reportPlacement(actor.getActorId(),time,"root",result.position()==null,result.warning());
            return result;
        }
        private void applyRootAnimationOffset(Entity entity,com.director.actor.rig.skeleton.RigPose pose,EntitySample sample) {
            if(entity==null || pose==null || pose.rig().findBoneIndex(com.director.actor.rig.skeleton.HumanoidRig.ROOT)<0) return;
            var gizmo=com.director.ui.actor.gizmo.ActorGizmoController.getInstance();
            if(gizmo.isDragging() && actor.getActorId().equals(gizmo.getTargetActorId())) return;
            var offset=pose.localTransform(com.director.actor.rig.skeleton.HumanoidRig.ROOT).translation();
            if(offset.lengthSquared()<=1e-10f) return;
            var result=finalRootPlacement(new Vec3d(entity.getX(),entity.getY(),entity.getZ()),offset,entity.getYaw(),sample,timelineSource.getCurrentTime());
            if(!result.warning().isEmpty() && sample.routeClip!=null) sample.routeClip.setTerrainWarning(result.warning());
            if(result.position()==null) { sample.visibility=0; sample.speed=0; entity.setInvisible(true); return; }
            var feet=result.position(); entity.refreshPositionAndAngles(feet.x,feet.y,feet.z,entity.getYaw(),entity.getPitch());
            var root=pose.localTransform(com.director.actor.rig.skeleton.HumanoidRig.ROOT);
            pose.setLocalTransform(com.director.actor.rig.skeleton.HumanoidRig.ROOT,
                    new com.director.actor.rig.skeleton.BoneTransform(new org.joml.Vector3f(),root.rotation(),root.scale()));
        }

        private void applyRigPoseOverrides(com.director.actor.rig.skeleton.RigPose pose, EntitySample s) {
            if (pose == null || s == null || !s.poseOverrideEnabled
                    || !com.director.actor.ActorBehaviorCapabilities.supportsLegacyPose(actor)
                    || !com.director.actor.rig.skeleton.HumanoidTopology.matches(pose.rig())) return;
            if (s.poseOverrideBody) setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.BODY,
                    s.poseBodyPitch, s.poseBodyYaw, s.poseBodyRoll);
            if (s.poseOverrideHead) setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.HEAD,
                    s.poseHeadPitch, s.poseHeadYaw, 0f);
            if (s.poseOverrideArms) {
                setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.LEFT_ARM,
                        s.poseLeftArmPitch, s.poseLeftArmYaw, s.poseLeftArmRoll);
                setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.RIGHT_ARM,
                        s.poseRightArmPitch, s.poseRightArmYaw, s.poseRightArmRoll);
            }
            if (s.poseOverrideLegs) {
                setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.LEFT_LEG, s.poseLeftLegPitch, 0f, 0f);
                setRigRotation(pose, com.director.actor.rig.skeleton.HumanoidRig.RIGHT_LEG, s.poseRightLegPitch, 0f, 0f);
            }
        }

        private void setRigRotation(com.director.actor.rig.skeleton.RigPose pose, String boneId,
                                    float pitch, float yaw, float roll) {
            var current = pose.localTransform(boneId);
            pose.setLocalTransform(pose.rig().requireBoneIndex(boneId), current.withRotation(
                    new org.joml.Quaternionf().rotateXYZ(pitch, yaw, roll)),
                    com.director.actor.rig.skeleton.RigPose.ROTATION);
        }

        /**
         * 获取当前动画姿态结果。
         *
         * <p>直接根据片段参数和绝对本地时间采样，不保留播放器状态。
         *
         * @param s 当前 Sample
         * @return AnimationPoseResult，如果没有动画则返回 null
         */
        private AnimationPoseResult getAnimationPose(EntitySample s) {
            if (actor == null || s == null) return null;
            var clock=ActorPresetClock.resolve(com.director.timeline.playback.entity.ActorAnimationLayerSample.from(s),s,actor.getRigProfile());
            return AnimationDataSampler.sample(clock.id(),clock.localTime(),clock.loop(),clock.speed());
        }

        /**
         * 将 Pose 应用到 ActorRuntime（向后兼容）。
         *
         * <p>注意：这是临时方案，用于保持向后兼容。
         * 未来可以直接将 Pose 传递给 PreviewEntity，由 Renderer 直接读取。
         *
         * @param pose 最终姿态
         */
        private void applyPoseToRuntime(Pose pose) {
            if (pose == null) {
                return;
            }

            // 将 Pose 映射到 ActorRuntime 的字段（用于向后兼容）
            actorRuntime.setBodyLean(pose.bodyPitch);
            actorRuntime.setBodyTwist(pose.bodyYaw);
            actorRuntime.setHeadPitch(pose.headPitch);
            actorRuntime.setHeadYaw(pose.headYaw);

            // 手臂和腿的摆动幅度（简化处理）
            float armSwing = Math.max(Math.abs(pose.leftArmPitch), Math.abs(pose.rightArmPitch));
            float legSwing = Math.max(Math.abs(pose.leftLegPitch), Math.abs(pose.rightLegPitch));
            actorRuntime.setArmSwing(armSwing);
            actorRuntime.setLegSwing(legSwing);
        }

        /**
         * 应用 LookAt
         *
         */
        void updateGaze() {
            if (ownerTrack.isMuted() || actor == null || !spawned || lastSample == null
                    || !lastSample.lookAtEnabled || lastSample.visibility <= .01f) return;
            var gizmo = com.director.ui.actor.gizmo.ActorGizmoController.getInstance();
            if (gizmo.isDragging() && actor.getActorId().equals(com.director.ui.actor.selection.ActorSelectionHub.getSelectedActorId())) return;
            var entity = ActorPreviewManager.getInstance().getPreview(actor.getActorId());
            if (entity == null) return;
            applyLookAt(entity, lastSample);
            entity.lastPitch = entity.getPitch();
            if (entity instanceof net.minecraft.entity.LivingEntity living) living.lastHeadYaw = living.headYaw;
            var props = propRuntimes.get(actor.getActorId());
            if (props != null) props.update();
        }

        private void applyLookAt(Entity entity, EntitySample s) {
            Vec3d pos = entity.getEyePos();
            var lookDraft=com.director.ui.actor.lookat.MarkerDragController.getInstance().previewPoint(actor.getActorId(),null);
            var point=lookDraft==null?s.lookAtPoint:new org.joml.Vector3f((float)lookDraft.x,(float)lookDraft.y,(float)lookDraft.z);
            Vec3d target = ActorLookAtTargetResolver.resolveClient(s.lookAtMode, point,
                    s.lookAtEntityUuid, actor.getActorId());
            if (target == null) return;

            Vec3d dir = target.subtract(pos);
            double length = dir.length();
            
            if (length < 0.01) {
                // 目标太近，不处理
                return;
            }
            
            dir = dir.normalize();

            float yaw = (float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90;
            float pitch = (float) (-Math.asin(dir.y) * 180 / Math.PI);

            float strength= Math.clamp(s.lookAtStrength, 0f, 1f);
            if(!Float.isFinite(strength) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
            // Gaze changes the head, never the actor root or armor-stand base plate.
            float relativeYaw=net.minecraft.util.math.MathHelper.wrapDegrees(yaw-s.rotation.x)*strength;
            float headPitch=pitch*strength;
            entity.setHeadYaw(s.rotation.x+relativeYaw);
            if(entity instanceof com.director.actor.preview.ActorPosePreview preview) {
                var pose=preview.getFinalRigPose();
                if(pose!=null && pose.rig().findBoneIndex("head")>=0) {
                    var head=pose.localTransform("head");
                    pose.setLocalTransform(pose.rig().requireBoneIndex("head"),
                        new com.director.actor.rig.skeleton.BoneTransform(head.translation(),
                            new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(relativeYaw),
                                (float)Math.toRadians(headPitch),0),head.scale()),
                        com.director.actor.rig.skeleton.RigPose.ROTATION);
                    preview.setFinalRigPose(pose);
                }
            } else entity.setPitch(headPitch);
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
                if (com.director.actor.prop.PropEventParameters.supports(e.type())) {
                    // Prop state is reconstructed from timeline time below.
                }
                
                // TODO: 未来可以在这里触发声音、粒子效果等
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
        return a + (b - a) * Math.clamp(t, 0f, 1f);
    }
}

