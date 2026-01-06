package com.chronoblocks.ui.timeline.playback.entity;

import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.entity.*;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EntityClipSampler v1.0
 *
 * <p>根据当前时间 sampleTime，从 EntityTrack 中算出实体的最终状态。
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
 * <p>与 EntityTrackSampler 的关系：
 * <ul>
 *   <li>EntityTrackSampler - 处理 EntityKeyframe（关键帧系统）</li>
 *   <li>EntityClipSampler - 处理 EntityClip（Clip 系统）</li>
 *   <li>两者可以并存，优先使用 Clip，如果没有 Clip 则回退到 Keyframe</li>
 * </ul>
 *
 * @author ChronoBlocks
 * @version 1.0
 */
public class EntityClipSampler {

    /** 要采样的实体轨道 */
    private final EntityTrack track;

    /** 用于 Action Clip 的"已触发记录"（防止重复触发） */
    private final ActionTriggerCache actionCache = new ActionTriggerCache();

    /**
     * 构造函数（接受实体轨道）。
     *
     * @param track 实体轨道
     */
    public EntityClipSampler(EntityTrack track) {
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

        // 按类型分类处理 clips
        List<EntitySpawnClip> spawnClips = new ArrayList<>();
        List<EntityTransformClip> transformClips = new ArrayList<>();
        List<EntityAnimationClip> animationClips = new ArrayList<>();
        List<EntityActionClip> actionClips = new ArrayList<>();
        List<EntityStateClip> stateClips = new ArrayList<>();
        List<EntityLookAtClip> lookAtClips = new ArrayList<>();
        List<EntityDespawnClip> despawnClips = new ArrayList<>();

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
            }
        }

        // ========== 处理 Spawn Clip（优先级最高，作为基础）==========
        if (!spawnClips.isEmpty()) {
            // 使用最后一个 Spawn Clip（如果有多个重叠）
            EntitySpawnClip spawnClip = spawnClips.get(spawnClips.size() - 1);
            applySpawnClip(result, spawnClip, timeSeconds);
        }

        // ========== 处理 Transform Clip（位置/旋转/缩放）==========
        // 规则：覆盖（最后一个生效，按时间排序）
        applyTransform(result, transformClips, timeSeconds);

        // ========== 处理 Animation Clip（动画）==========
        // 规则：last-wins（最后一个生效，按时间排序）
        applyAnimation(result, animationClips);

        // ========== 处理 State Clip（状态）==========
        // 规则：last-wins（最后一个生效，按时间排序）
        applyState(result, stateClips);

        // ========== 处理 LookAt Clip（看向目标）==========
        // 规则：叠加（强度最大）
        applyLookAt(result, lookAtClips);

        // ========== 处理 Despawn Clip（离场）==========
        if (!despawnClips.isEmpty()) {
            // 如果有多个 Despawn Clip，使用最后一个
            EntityDespawnClip despawnClip = despawnClips.get(despawnClips.size() - 1);
            applyDespawnClip(result, despawnClip, timeSeconds);
        }

        // ========== 处理 Action Clip（行为事件）==========
        // 规则：一次性触发（只在刚进入 Clip 时触发）
        applyActions(result, actionClips, timeSeconds);

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
        result.rotation.set(clip.getYawDeg(), clip.getPitchDeg(), 0f);
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
    }

    /**
     * 应用单个 Transform Clip（位移/旋转/缩放）。
     */
    private void applyTransformClip(EntitySample result, EntityTransformClip clip, float time) {
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
            // 步进：使用结束值
            result.position.set(clip.getToPos());
            result.rotation.set(clip.getToRot());
            result.scale.set(clip.getToScale());
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
     * 应用 Animation Clip（动画）。
     * 规则：last-wins（最后一个生效，按时间排序）
     */
    private void applyAnimation(EntitySample result, List<EntityAnimationClip> clips) {
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

        result.animationId = active.getAnimationId();
        result.animationLoop = active.isLoop();
        result.animationSpeed = active.getSpeed();
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
     * 清除 Action 触发缓存（用于重置/跳转时间）。
     */
    public void clearActionCache() {
        actionCache.clear();
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

