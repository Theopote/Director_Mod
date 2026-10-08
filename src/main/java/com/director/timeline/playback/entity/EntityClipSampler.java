package com.director.timeline.playback.entity;

import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.entity.*;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EntityClipSampler v1.0
 *
 * <p>根据当前时间 sampleTime，从 ActorTrack 中算出实体的最终状态。
 *
 * <p>流程：
 * <ol>
 *   <li>找到 sampleTime 覆盖到的所有 clips</li>
 *   <li>按类型分类处理（Spawn, Transform, Animation, Action, State, LookAt, Despawn）</li>
 *   <li>将结果叠加到 EntitySample</li>
 * </ol>
 *
 * <p>特点：
 * <ul>
 *   <li>支持多个 clips 同时覆盖（比如 Transform 和 Animation 可以重叠）</li>
 *   <li>不同类型的 clip 有不同的处理逻辑</li>
 *   <li>Transform Clip 支持插值（from/to）</li>
 *   <li>State Clip 可以叠加</li>
 * </ul>
 *
 * <p>与 ActorTrackSampler 的关系：
 * <ul>
 *   <li>ActorTrackSampler - 处理 EntityKeyframe（关键帧系统）</li>
 *   <li>EntityClipSampler - 处理 EntityClip（Clip 系统）</li>
 *   <li>两者可以并存，优先使用 Clip，如果没有 Clip 则回退到 Keyframe</li>
 * </ul>
 *
 * @author Director
 * @version 1.0
 */
public class EntityClipSampler {

    /** 要采样的演员轨道 */
    private final ActorTrack track;

    /** 用于 Action Clip 的"已触发记录"（防止重复触发） */
    private final ActionTriggerCache actionCache = new ActionTriggerCache();

    /** 用于 AnimationEvent 的"已触发记录"（防止重复触发） */
    private final AnimationEventTriggerCache animationEventCache = new AnimationEventTriggerCache();

    /**
     * 动作推断阈值（blocks/second）
     */
    private static final float WALK_THRESHOLD = 0.02f;
    private static final float RUN_THRESHOLD = 0.12f;

    /**
     * 构造函数（接受演员轨道）。
     *
     * @param track 演员轨道
     */
    public EntityClipSampler(ActorTrack track) {
        this.track = track;
    }

    // ==========================================================
    // 主采样函数
    // ==========================================================

    /**
     * 在给定时间采样实体状态。
     *
     * @param time 时间（秒）
     * @return 实体采样结果
     */
    public EntitySample sample(float time) {
        return sampleAt(time);
    }

    /**
     * 在给定时间采样实体状态。
     *
     * @param timeSeconds 时间（秒）
     * @return 实体采样结果
     */
    public EntitySample sampleAt(float timeSeconds) {
        if (track == null || !track.isEnabled()) {
            return EntitySample.disabled();
        }

        // 找到覆盖该时间的所有 clips
        List<EntityClip> clipsAtTime = track.findClipsAt(timeSeconds);
        if (clipsAtTime.isEmpty()) {
            return EntitySample.disabled();
        }

        // 创建初始采样（从 Spawn Clip 或默认值开始）
        EntitySample result = new EntitySample();
        result.enabled = true;
        result.actionLocalTime = Math.max(0f, timeSeconds);

        // 按类型分类处理 clips
        List<EntitySpawnClip> spawnClips = new ArrayList<>();
        List<EntityTransformClip> transformClips = new ArrayList<>();
        List<EntityAnimationClip> animationClips = new ArrayList<>();
        List<EntityActionClip> actionClips = new ArrayList<>();
        List<EntityStateClip> stateClips = new ArrayList<>();
        List<EntityLookAtClip> lookAtClips = new ArrayList<>();
        List<EntityDespawnClip> despawnClips = new ArrayList<>();
        List<ActorActionClip> actorActionClips = new ArrayList<>();
        List<ActorPoseClip> actorPoseClips = new ArrayList<>();
        List<ActorRouteClip> actorRouteClips = new ArrayList<>();
        List<ActorRelationClip> actorRelationClips = new ArrayList<>();

        // PoseClip 需要轨道上下文来查找前一个目标姿态，不能只收集当前时刻覆盖的片段。
        for (EntityClip clip : track.getClips()) {
            if (clip instanceof ActorPoseClip poseClip && !poseClip.isMuted()) {
                actorPoseClips.add(poseClip);
            }
        }

        for (EntityClip clip : clipsAtTime) {
            if (clip.isMuted()) {
                continue; // 跳过静音的 clip
            }

            switch (clip.getClipType()) {
                case SPAWN:
                    if (clip instanceof EntitySpawnClip spawnClip) {
                        spawnClips.add(spawnClip);
                    }
                    break;
                case TRANSFORM:
                    if (clip instanceof EntityTransformClip transformClip) {
                        transformClips.add(transformClip);
                    }
                    break;
                case ANIMATION:
                    if (clip instanceof EntityAnimationClip animationClip) {
                        animationClips.add(animationClip);
                    }
                    break;
                case ACTION:
                    if (clip instanceof EntityActionClip actionClip) {
                        actionClips.add(actionClip);
                    }
                    break;
                case STATE:
                    if (clip instanceof EntityStateClip stateClip) {
                        stateClips.add(stateClip);
                    }
                    break;
                case LOOK_AT:
                    if (clip instanceof EntityLookAtClip lookAtClip) {
                        lookAtClips.add(lookAtClip);
                    }
                    break;
                case DESPAWN:
                    if (clip instanceof EntityDespawnClip despawnClip) {
                        despawnClips.add(despawnClip);
                    }
                    break;
                case ACTOR_ACTION:
                    if (clip instanceof ActorActionClip actorActionClip) {
                        actorActionClips.add(actorActionClip);
                    }
                    break;
                case ACTOR_POSE:
                    // 已在上方从完整轨道收集，避免丢失刚结束的前一姿态。
                    break;
                case ACTOR_ROUTE:
                    if (clip instanceof ActorRouteClip actorRouteClip) {
                        actorRouteClips.add(actorRouteClip);
                    }
                    break;
                case ACTOR_RELATION:
                    if (clip instanceof ActorRelationClip actorRelationClip) {
                        actorRelationClips.add(actorRelationClip);
                    }
                    break;
            }
        }

        // ========== 处理 Spawn Clip（优先级最高，作为基础）==========
        if (!spawnClips.isEmpty()) {
            // 使用最后一个 Spawn Clip（如果有多个重叠）
            EntitySpawnClip spawnClip = spawnClips.getLast();
            applySpawnClip(result, spawnClip, timeSeconds);
        }

        // ========== 处理 ActorRoute Clip（路线移动）==========
        // 规则：last-wins（取 startTime 最大的那个），Transform 仍可覆盖它
        applyActorRoute(result, actorRouteClips, timeSeconds);

        // ========== 处理 Transform Clip（位置/旋转/缩放）==========
        // 规则：覆盖（最后一个生效，按时间排序）
        applyTransform(result, transformClips, timeSeconds);

        // ========== 处理 Animation Clip（动画）==========
        // 规则：last-wins（最后一个生效，按时间排序）
        applyAnimation(result, animationClips, timeSeconds);

        // ========== 处理 State Clip（状态）==========
        // 规则：last-wins（最后一个生效，按时间排序）
        applyState(result, stateClips);

        // ========== 处理 LookAt Clip（看向目标）==========
        // 规则：叠加（强度最大）
        applyLookAt(result, lookAtClips);

        // ========== 处理 Despawn Clip（离场）==========
        if (!despawnClips.isEmpty()) {
            // 如果有多个 Despawn Clip，使用最后一个
            EntityDespawnClip despawnClip = despawnClips.getLast();
            applyDespawnClip(result, despawnClip, timeSeconds);
        }

        // ========== 处理 Action Clip（行为事件）==========
        // 规则：一次性触发（只在刚进入 Clip 时触发）
        applyActions(result, actionClips, timeSeconds);

        // ========== 处理 ActorAction Clip（动作语义）==========
        // 规则：last-wins（取 startTime 最大的那个）
        applyActorAction(result, actorActionClips, timeSeconds);

        // ========== 处理 ActorPose Clip（局部姿态覆盖）==========
        // 规则：last-wins（取 startTime 最大的那个）
        applyActorPose(result, actorPoseClips, timeSeconds);

        // ========== 处理 ActorRelation Clip（多演员关系）==========
        // 规则：last-wins（取 startTime 最大的那个）
        applyActorRelation(result, actorRelationClips, timeSeconds);

        // ========== 自动动作推断（仅在未显式指定时生效）==========
        resolveAutoAction(result);

        return result;
    }

    // ==========================================================
    // 各类型 Clip 的应用逻辑
    // ==========================================================

    /**
     * 应用 Spawn Clip（入场）。
     */
    private void applySpawnClip(EntitySample result, EntitySpawnClip clip, float time) {
        // Spawn Clip 只在开始时生效，但这里我们始终应用位置和旋转作为基础
        Vector3f pos = clip.getPosition();
        result.position.set(pos);
        result.rotation.set(clip.getYawDeg(), clip.getPitchDeg(), clip.getRollDeg());
        result.scale.set(clip.getScale());
        if (applyRootKeys(result, clip.getKeyframes(), time)) {
            var prev = findPrevKeyframe(clip.getKeyframes(), time);
            var next = findNextKeyframe(clip.getKeyframes(), time);
            result.speed = prev == null || next == null ? 0 : segmentSpeed(prev.getPosition(), next.getPosition(),
                    prev.getTime(), next.getTime(), time, next.getInterpolation());
        }
        result.visibility = clip.isVisibleOnSpawn() ? 1.0f : 0.0f;
        result.active = true;
    }

    /**
     * 应用 Transform Clip（位移/旋转/缩放）。
     * 规则：覆盖（最后一个生效，按时间排序）
     */
    private void applyTransform(EntitySample result, List<EntityTransformClip> clips, float time) {
        EntityTransformClip active = null;

        // 找到最后一个（按开始时间排序）
        for (EntityTransformClip clip : clips) {
            if (!clip.isMuted()) {
                if (active == null || clip.getStartTime() >= active.getStartTime()) {
                    active = clip;
                }
            }
        }

        if (active == null) {
            return;
        }

        applyTransformClip(result, active, time);
        result.routeClip = null;
        result.speed = transformSpeed(active, time);
        result.actionLocalTime = Math.max(0f, time - active.getStartTime());
    }

    /**
     * 应用单个 Transform Clip（位移/旋转/缩放）。
     * <p>优先级：关键帧 > fromPos/toPos 插值
     */
    private boolean applyRootKeys(EntitySample result, List<EntityClipKeyframe> keyframes, float time) {
        // ⭐ 优先使用关键帧插值
        if (!keyframes.isEmpty()) {
            EntityClipKeyframe prev = findPrevKeyframe(keyframes, time);
            EntityClipKeyframe next = findNextKeyframe(keyframes, time);
            
            if (prev != null && next != null) {
                // 在两个关键帧之间插值
                float t = (time - prev.getTime()) / (next.getTime() - prev.getTime());
                t = Math.max(0f, Math.min(1f, t));
                
                // 使用 next 的插值类型
                TimelineInterpolation interp = next.getInterpolation();
                if (interp == TimelineInterpolation.SMOOTH) {
                    t = t * t * (3f - 2f * t);
                } else if (interp == TimelineInterpolation.STEP) {
                    t = 0f; // 保持前一关键帧，直到时间到达下一关键帧
                }
                
                // 位置插值
                result.position.x = lerp(prev.getPosition().x, next.getPosition().x, t);
                result.position.y = lerp(prev.getPosition().y, next.getPosition().y, t);
                result.position.z = lerp(prev.getPosition().z, next.getPosition().z, t);
                
                // 旋转插值（角度插值，处理 360° 环绕）
                result.rotation.x = lerpAngle(prev.getRotation().x, next.getRotation().x, t);
                result.rotation.y = lerpAngle(prev.getRotation().y, next.getRotation().y, t);
                result.rotation.z = lerpAngle(prev.getRotation().z, next.getRotation().z, t);
                
                // 缩放插值
                result.scale.x = lerp(prev.getScale().x, next.getScale().x, t);
                result.scale.y = lerp(prev.getScale().y, next.getScale().y, t);
                result.scale.z = lerp(prev.getScale().z, next.getScale().z, t);
                
                return true;
            } else if (prev != null) {
                // 只有前一个关键帧（在最后一个关键帧之后）
                result.position.set(prev.getPosition());
                result.rotation.set(prev.getRotation());
                result.scale.set(prev.getScale());
                return true;
            } else if (next != null) {
                // 只有下一个关键帧（在第一个关键帧之前）
                result.position.set(next.getPosition());
                result.rotation.set(next.getRotation());
                result.scale.set(next.getScale());
                return true;
            }
        }
        
        return false;
    }

    private void applyTransformClip(EntitySample result, EntityTransformClip clip, float time) {
        if (applyRootKeys(result, clip.getKeyframes(), time)) return;

        // 如果没有关键帧，使用原有的 fromPos/toPos 插值
        // 计算片段内的归一化时间 [0, 1]
        float clipLength = clip.getLength();
        if (clipLength <= 0) {
            return;
        }

        float localTime = time - clip.getStartTime();
        float t = Math.max(0f, Math.min(1f, localTime / clipLength));

        // 应用插值
        TimelineInterpolation interp = clip.getInterpolation();
        if (interp == TimelineInterpolation.STEP) {
            // 保持起始值，在结束时间切换；与关键帧 STEP 一致。
            result.position.set(t < 1f ? clip.getFromPos() : clip.getToPos());
            result.rotation.set(t < 1f ? clip.getFromRot() : clip.getToRot());
            result.scale.set(t < 1f ? clip.getFromScale() : clip.getToScale());
        } else {
            // 线性或平滑插值
            if (interp == TimelineInterpolation.SMOOTH) {
                // Smoothstep
                t = t * t * (3f - 2f * t);
            }

            // 位置插值
            result.position.x = lerp(clip.getFromPos().x, clip.getToPos().x, t);
            result.position.y = lerp(clip.getFromPos().y, clip.getToPos().y, t);
            result.position.z = lerp(clip.getFromPos().z, clip.getToPos().z, t);

            // 旋转插值（角度插值，处理 360° 环绕）
            result.rotation.x = lerpAngle(clip.getFromRot().x, clip.getToRot().x, t);
            result.rotation.y = lerpAngle(clip.getFromRot().y, clip.getToRot().y, t);
            result.rotation.z = lerpAngle(clip.getFromRot().z, clip.getToRot().z, t);

            // 缩放插值
            result.scale.x = lerp(clip.getFromScale().x, clip.getToScale().x, t);
            result.scale.y = lerp(clip.getFromScale().y, clip.getToScale().y, t);
            result.scale.z = lerp(clip.getFromScale().z, clip.getToScale().z, t);
        }

        // TODO: 如果 followPath 为 true 且 pathId 不为 null，使用路径系统
        // 这里先使用简单的 from/to 插值
    }

    /**
     * 查找时间点之前的关键帧
     */
    private EntityClipKeyframe findPrevKeyframe(List<EntityClipKeyframe> keyframes, float time) {
        EntityClipKeyframe prev = null;
        for (EntityClipKeyframe kf : keyframes) {
            if (kf.getTime() <= time) {
                if (prev == null || kf.getTime() > prev.getTime()) {
                    prev = kf;
                }
            }
        }
        return prev;
    }

    /**
     * 查找时间点之后的关键帧
     */
    private EntityClipKeyframe findNextKeyframe(List<EntityClipKeyframe> keyframes, float time) {
        EntityClipKeyframe next = null;
        for (EntityClipKeyframe kf : keyframes) {
            if (kf.getTime() > time) {
                if (next == null || kf.getTime() < next.getTime()) {
                    next = kf;
                }
            }
        }
        return next;
    }

    /**
     * 应用 Animation Clip（动画）。
     * 规则：last-wins（最后一个生效，按时间排序）
     *
     * @param result EntitySample
     * @param clips Animation Clips
     * @param timeSeconds 当前时间（秒）
     */
    private void applyAnimation(EntitySample result, List<EntityAnimationClip> clips, float timeSeconds) {
        EntityAnimationClip active = null;

        // 找到最后一个（按开始时间排序）
        for (EntityAnimationClip clip : clips) {
            if (!clip.isMuted()) {
                if (active == null || clip.getStartTime() >= active.getStartTime()) {
                    active = clip;
                }
            }
        }

        if (active == null) {
            return;
        }

        // 应用动画信息
        result.animationId = active.getAnimationId();
        result.animationLoop = active.isLoop();
        result.animationSpeed = active.getSpeed();
        result.animationLocalTime = Math.max(0.0f, timeSeconds - active.getStartTime());

        // 处理 AnimationEvent（动画事件）
        if (active.hasEvents()) {
            // 计算归一化时间
            float clipDuration = active.getEndTime() - active.getStartTime();
            if (clipDuration > 0.01f) {
                float normalizedTime = (timeSeconds - active.getStartTime()) / clipDuration;
                normalizedTime = Math.max(0.0f, Math.min(1.0f, normalizedTime));

                // 检查并触发事件
                for (com.director.timeline.model.entity.AnimationEvent event : active.getEvents()) {
                    if (animationEventCache.shouldTrigger(active, event, normalizedTime)) {
                        // 触发 Action 事件
                        result.actionEvents.add(event.getAction());
                    }
                }
            }
        }
    }

    /**
     * 应用 State Clip（状态）。
     * 规则：last-wins（最后一个生效，按时间排序）
     */
    private void applyState(EntitySample result, List<EntityStateClip> clips) {
        // 按时间排序，最后一个覆盖前面的
        for (EntityStateClip clip : clips) {
            if (!clip.isMuted()) {
                result.sneaking = clip.isSneaking();
                result.sprinting = clip.isSprinting();
                result.glowing = clip.isGlowing();
                result.invulnerable = clip.isInvulnerable();
                result.visibility = clip.getVisibility();
            }
        }
    }

    /**
     * 应用 LookAt Clip（看向目标）。
     * 规则：叠加（强度最大）
     */
    private void applyLookAt(EntitySample result, List<EntityLookAtClip> clips) {
        EntityLookAtClip best = null;

        // 找到强度最大的
        for (EntityLookAtClip clip : clips) {
            if (!clip.isMuted()) {
                if (best == null || clip.getStrength() >= best.getStrength()) {
                    best = clip;
                }
            }
        }

        if (best == null) {
            return;
        }

        result.lookAtEnabled = true;
        result.lookAtMode = best.getMode().name();
        result.lookAtPoint.set(best.getWorldPoint());
        result.lookAtEntityUuid = best.getTargetEntityUuid();
        result.lookAtStrength = best.getStrength();
    }

    /**
     * 应用 Despawn Clip（离场）。
     */
    private void applyDespawnClip(EntitySample result, EntityDespawnClip clip, float time) {
        // Despawn Clip 在结束时生效
        if (time >= clip.getEndTime() - 0.01f) { // 接近结束时
            if (clip.getMode() == EntityDespawnClip.DespawnMode.HIDE) {
                result.visibility = 0.0f;
            } else if (clip.getMode() == EntityDespawnClip.DespawnMode.REMOVE) {
                result.active = false;
            }
        }
    }

    // ==========================================================
    // 工具方法
    // ==========================================================

    /**
     * 线性插值
     */
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 角度插值（处理 360° 环绕，选择最短路径）。
     */
    private float lerpAngle(float a, float b, float t) {
        // 归一化到 [0, 360)
        while (a < 0f) a += 360f;
        while (a >= 360f) a -= 360f;
        while (b < 0f) b += 360f;
        while (b >= 360f) b -= 360f;

        // 计算两个方向的距离
        float diff = b - a;
        if (diff > 180f) {
            diff -= 360f; // 走反向更短
        } else if (diff < -180f) {
            diff += 360f; // 走反向更短
        }

        float result = a + diff * t;
        // 归一化结果
        while (result < 0f) result += 360f;
        while (result >= 360f) result -= 360f;
        return result;
    }

    /**
     * 应用 Action Clip（行为事件）。
     * 规则：一次性触发（只在刚进入 Clip 时触发）
     */
    private void applyActions(EntitySample result, List<EntityActionClip> clips, float time) {
        for (EntityActionClip clip : clips) {
            if (!clip.isMuted() && actionCache.shouldTrigger(clip, time)) {
                result.actionEvents.add(
                    new EntityActionEvent(clip.getActionType(), new java.util.HashMap<>(clip.getParams()))
                );
            }
        }
    }

    /**
     * 应用 ActorAction Clip（动作语义）。
     * 规则：在时间 t，取 startTime ≤ t ≤ endTime 的 ActorActionClip 中，startTime 最大的那个
     *
     * @param result EntitySample
     * @param clips ActorAction Clips
     * @param timeSeconds 当前时间（秒）
     */
    private void sampleAction(EntitySample result, List<ActorActionClip> clips, float timeSeconds) {
        ActorActionClip active = null;

        for (ActorActionClip clip : clips) {
            if (clip.isMuted()) {
                continue;
            }

            if (timeSeconds >= clip.getStartTime() && timeSeconds <= clip.getEndTime()) {
                if (active == null || clip.getStartTime() > active.getStartTime()) {
                    active = clip;
                }
            }
        }

        if (active != null) {
            result.actionExplicit = true;
            result.actionLocalTime = Math.max(0f, timeSeconds - active.getStartTime());
            result.action = active.getActionType();
            result.actionIntensity = active.getIntensity();
        }
    }

    /**
     * 应用 ActorAction Clip（动作语义）。
     * 这是对外的公共方法名，内部调用 sampleAction。
     */
    private void applyActorAction(EntitySample result, List<ActorActionClip> clips, float timeSeconds) {
        sampleAction(result, clips, timeSeconds);
    }

    private void applyActorPose(EntitySample result, List<ActorPoseClip> clips, float timeSeconds) {
        ActorPoseClip active = null;
        for (ActorPoseClip clip : clips) {
            if (clip.isMuted()) {
                continue;
            }
            if (timeSeconds >= clip.getStartTime() && timeSeconds <= clip.getEndTime()) {
                if (active == null || clip.getStartTime() > active.getStartTime()) {
                    active = clip;
                }
            }
        }
        if (active == null) {
            return;
        }

        ActorPoseClip previous = null;
        for (ActorPoseClip clip : clips) {
            if (clip != active && !clip.isMuted() && clip.getStartTime() < active.getStartTime()
                && (previous == null || clip.getStartTime() > previous.getStartTime())) {
                previous = clip;
            }
        }

        result.poseOverrideEnabled = true;
        result.poseOverrideBody = active.isOverrideBody();
        result.poseOverrideHead = active.isOverrideHead();
        result.poseOverrideArms = active.isOverrideArms();
        result.poseOverrideLegs = active.isOverrideLegs();
        float blend = 1f;
        if (previous != null) {
            float duration = active.getEndTime() - active.getStartTime();
            if (duration > 1.0e-5f) {
                blend = clamp01((timeSeconds - active.getStartTime()) / duration);
            }
        }

        result.poseBodyPitch = blend(previous != null && previous.isOverrideBody(), previous == null ? 0f : previous.getBodyPitch(), active.getBodyPitch(), blend);
        result.poseBodyYaw = blend(previous != null && previous.isOverrideBody(), previous == null ? 0f : previous.getBodyYaw(), active.getBodyYaw(), blend);
        result.poseBodyRoll = blend(previous != null && previous.isOverrideBody(), previous == null ? 0f : previous.getBodyRoll(), active.getBodyRoll(), blend);
        result.poseHeadPitch = blend(previous != null && previous.isOverrideHead(), previous == null ? 0f : previous.getHeadPitch(), active.getHeadPitch(), blend);
        result.poseHeadYaw = blend(previous != null && previous.isOverrideHead(), previous == null ? 0f : previous.getHeadYaw(), active.getHeadYaw(), blend);
        result.poseLeftArmPitch = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getLeftArmPitch(), active.getLeftArmPitch(), blend);
        result.poseLeftArmYaw = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getLeftArmYaw(), active.getLeftArmYaw(), blend);
        result.poseLeftArmRoll = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getLeftArmRoll(), active.getLeftArmRoll(), blend);
        result.poseRightArmPitch = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getRightArmPitch(), active.getRightArmPitch(), blend);
        result.poseRightArmYaw = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getRightArmYaw(), active.getRightArmYaw(), blend);
        result.poseRightArmRoll = blend(previous != null && previous.isOverrideArms(), previous == null ? 0f : previous.getRightArmRoll(), active.getRightArmRoll(), blend);
        result.poseLeftLegPitch = blend(previous != null && previous.isOverrideLegs(), previous == null ? 0f : previous.getLeftLegPitch(), active.getLeftLegPitch(), blend);
        result.poseRightLegPitch = blend(previous != null && previous.isOverrideLegs(), previous == null ? 0f : previous.getRightLegPitch(), active.getRightLegPitch(), blend);
    }

    private static float blend(boolean hasPreviousOverride, float previous, float current, float amount) {
        return hasPreviousOverride ? previous + (current - previous) * amount : current;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void applyActorRoute(EntitySample result, List<ActorRouteClip> clips, float timeSeconds) {
        ActorRouteClip active = null;
        for (ActorRouteClip clip : clips) {
            if (clip.isMuted()) {
                continue;
            }
            if (timeSeconds >= clip.getStartTime() && timeSeconds <= clip.getEndTime()) {
                if (active == null || clip.getStartTime() > active.getStartTime()) {
                    active = clip;
                }
            }
        }
        if (active == null || active.getWaypoints().isEmpty()) {
            return;
        }

        result.routeClip = active;

        List<Vector3f> points = active.getWaypoints();
        if (points.size() == 1) {
            result.position.set(points.getFirst());
            return;
        }

        float clipLength = Math.max(0.0001f, active.getLength());
        float localTime = Math.max(0.0f, timeSeconds - active.getStartTime());
        float routeDuration = computeRouteDuration(active);
        float duration = routeDuration > 0.0f ? routeDuration : clipLength;
        float travelTime = resolveRouteTime(active, localTime, duration);
        float distance = travelTime * Math.max(0.05f, active.getSpeed());

        ActorRouteSampler.RouteSample sample = ActorRouteSampler.sampleAtDistance(
                points, distance, active.getInterpolationMode());
        result.position.set(sample.position());
        result.actionLocalTime = localTime;
        result.speed = routeDuration <= 0f || (active.getLoopMode() == ActorRouteClip.LoopMode.ONCE
                && localTime >= routeDuration) ? 0f : Math.max(0.05f, active.getSpeed());
        Vector3f direction = sample.direction();
        if (active.getLoopMode() == ActorRouteClip.LoopMode.PING_PONG && duration > 0f
                && localTime % (2f * duration) >= duration) direction.negate();
        if (active.isAlignRotationToRoute() && direction.lengthSquared() > 1.0e-6f) {
            result.rotation.x = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            result.rotation.y = (float) Math.toDegrees(Math.atan2(-direction.y, horizontal));
        }
    }

    private void applyActorRelation(EntitySample result, List<ActorRelationClip> clips, float timeSeconds) {
        ActorRelationClip active = null;
        for (ActorRelationClip clip : clips) {
            if (clip.isMuted()) {
                continue;
            }
            if (timeSeconds >= clip.getStartTime() && timeSeconds <= clip.getEndTime()) {
                if (active == null || clip.getStartTime() > active.getStartTime()) {
                    active = clip;
                }
            }
        }
        if (active == null || active.getTargetActorId() == null) {
            return;
        }

        result.relationEnabled = true;
        result.relationType = active.getRelationType().name();
        result.relationTargetActorId = active.getTargetActorId();
        result.relationDistance = active.getDistance();
        result.relationAngle = active.getAngle();
        result.relationHeightOffset = active.getHeightOffset();
        result.relationMaintainLookAt = active.isMaintainLookAt();
    }

    private float computeRouteDuration(ActorRouteClip clip) {
        float distance = ActorRouteSampler.computeCurveLength(clip.getWaypoints(), clip.getInterpolationMode());
        return distance / Math.max(0.05f, clip.getSpeed());
    }

    private float resolveRouteTime(ActorRouteClip clip, float localTime, float duration) {
        if (duration <= 0.0f) {
            return 0.0f;
        }
        return switch (clip.getLoopMode()) {
            case ONCE -> Math.min(localTime, duration);
            case LOOP -> localTime % duration;
            case PING_PONG -> {
                float cycle = duration * 2.0f;
                float t = localTime % cycle;
                yield t <= duration ? t : cycle - t;
            }
        };
    }

    /** Derivative of the selected transform segment; never samples events or previous frames. */
    private float transformSpeed(EntityTransformClip clip, float time) {
        var keys = clip.getKeyframes();
        if (!keys.isEmpty()) {
            var previous = findPrevKeyframe(keys, time);
            var next = findNextKeyframe(keys, time);
            if (previous == null || next == null) return 0f;
            return segmentSpeed(previous.getPosition(), next.getPosition(), previous.getTime(),
                    next.getTime(), time, next.getInterpolation());
        }
        return segmentSpeed(clip.getFromPos(), clip.getToPos(), clip.getStartTime(),
                clip.getEndTime(), time, clip.getInterpolation());
    }

    private float segmentSpeed(Vector3f from, Vector3f to, float start, float end,
                               float time, TimelineInterpolation interpolation) {
        if (end <= start || time < start || time >= end || interpolation == TimelineInterpolation.STEP) return 0f;
        float t = (time - start) / (end - start);
        float derivative = interpolation == TimelineInterpolation.SMOOTH ? 6f * t * (1f - t) : 1f;
        return from.distance(to) / (end - start) * derivative;
    }

    /**
     * 自动动作推断（仅在未显式指定 ActorActionClip 时生效）。
     *
     * <p>优先级规则：
     * <ol>
     *   <li>ActorActionClip（导演显式指定）→ 完全尊重，不自动推断</li>
     *   <li>位移速度自动推断（WALK / RUN）</li>
     *   <li>默认 IDLE</li>
     * </ol>
     *
     * @param result 采样结果
     */
    public static void resolveAutoAction(EntitySample result) {
        // 1️⃣ 如果导演明确指定了 ActionClip，完全尊重，不自动推断
        if (result.actionExplicit) {
            return;
        }

        // 2️⃣ 否则根据速度判断
        float v = result.speed;

        if (v >= RUN_THRESHOLD) {
            // 跑步
            result.action = ActorActionType.RUN;
            // 强度：基于速度超过阈值的程度
            float excess = v - RUN_THRESHOLD;
            result.actionIntensity = clamp(
                    (excess / RUN_THRESHOLD) * 0.5f + 0.5f, // 0.5 ~ 1.0
                    0.5f,
                    1.0f
            );
        } else if (v >= WALK_THRESHOLD) {
            // 行走
            result.action = ActorActionType.WALK;
            // 强度：基于速度在 WALK 和 RUN 之间的比例
            float ratio = (v - WALK_THRESHOLD) / (RUN_THRESHOLD - WALK_THRESHOLD);
            result.actionIntensity = clamp(
                    ratio * 0.5f + 0.3f, // 0.3 ~ 0.8
                    0.3f,
                    0.8f
            );
        } else {
            // 静止
            result.action = ActorActionType.IDLE;
            result.actionIntensity = 1.0f;
        }
    }

    /**
     * 限制值在指定范围内。
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 清除 Action 触发缓存（用于重置/跳转时间）。
     */
    public void clearActionCache() {
        actionCache.clear();
    }

    /**
     * 清除 AnimationEvent 触发缓存（用于重置/跳转时间）。
     */
    public void clearAnimationEventCache() {
        animationEventCache.clear();
    }

    /**
     * 清除所有触发缓存（用于重置/跳转时间）。
     */
    public void clearAllCaches() {
        actionCache.clear();
        animationEventCache.clear();
    }

    /**
     * 检查是否为空。
     *
     * @return 如果轨道为空或没有 clips 则返回 true
     */
    public boolean isEmpty() {
        return track == null || !track.hasClips();
    }

    /**
     * 获取所有 clips（用于调试）。
     *
     * @return clips 列表
     */
    public List<EntityClip> getClips() {
        if (track == null) {
            return Collections.emptyList();
        }
        return track.getClips();
    }
}

