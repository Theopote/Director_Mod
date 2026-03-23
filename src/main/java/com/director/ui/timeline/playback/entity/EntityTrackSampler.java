package com.director.ui.timeline.playback.entity;

import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.entity.EntityKeyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * EntityTrackSampler v1.0
 *
 * <p>根据当前时间 sampleTime，从实体轨道中算出实体的最终状态。
 * <p>流程：
 *  1. 找到 sampleTime 前后的两个关键帧
 *  2. 将它们转换成 EntitySample
 *  3. 根据插值方式进行插值（Linear / Smooth / Step）
 *  4. 输出 EntitySample
 *
 * <p>特点：
 *  - 当没有关键帧时输出 "默认禁用" 状态
 *  - 当只有 1 个关键帧时直接返回其实体状态
 *  - 插值逻辑集中在 EntitySample.lerpFrom()
 *
 * @author Director
 * @version 1.0
 */
public class EntityTrackSampler {

    /** 要采样的轨道（属 TimelineModel） */
    private final TimelineTrack track;

    /** 轨道中所有实体关键帧的缓存（按时间排序） */
    private List<EntityKeyframe> keyframes = Collections.emptyList();

    /**
     * 构造函数（接受轨道）。
     */
    public EntityTrackSampler(TimelineTrack track) {
        this.track = track;
        rebuildKeyframeList();
    }

    // ==========================================================
    // 重建缓存（当轨道关键帧变化、删除、添加时调用）
    // ==========================================================

    /**
     * 重建关键帧列表（从轨道中收集所有 EntityKeyframe）。
     */
    public void rebuildKeyframeList() {
        if (track == null) {
            this.keyframes = Collections.emptyList();
            return;
        }

        List<EntityKeyframe> list = new ArrayList<>();
        for (com.director.ui.timeline.model.TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof EntityKeyframe ek) {
                list.add(ek);
            }
        }
        list.sort(Comparator.comparingDouble(EntityKeyframe::getTime));
        this.keyframes = list;
    }

    /**
     * 清空关键帧列表。
     */
    public void clear() {
        this.keyframes = Collections.emptyList();
    }

    /**
     * 检查是否为空。
     */
    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    // ==========================================================
    // 主采样函数 —— TimelineEntityController 会每 tick 调用
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
     * 在给定时间采样实体状态（向后兼容方法）。
     *
     * @param timeSeconds 时间（秒）
     * @return 实体采样结果
     */
    public EntitySample sampleAt(float timeSeconds) {
        // 0) 无关键帧 → 返回默认禁用状态
        if (keyframes.isEmpty()) {
            return defaultDisabledEntity();
        }

        // 1) 只有 1 个关键帧 → 输出其实体状态
        if (keyframes.size() == 1) {
            return toSample(keyframes.getFirst());
        }

        // 2) 找到时间段 [kfA, kfB]
        EntityKeyframe kfA = keyframes.getFirst();
        EntityKeyframe kfB = keyframes.getLast();

        // 如果时间在最前
        if (timeSeconds <= kfA.getTime()) {
            return toSample(kfA);
        }

        // 如果时间在最后
        if (timeSeconds >= kfB.getTime()) {
            return toSample(kfB);
        }

        // 遍历查找区段
        for (int i = 0; i < keyframes.size() - 1; i++) {
            EntityKeyframe a = keyframes.get(i);
            EntityKeyframe b = keyframes.get(i + 1);

            if (timeSeconds >= a.getTime() && timeSeconds <= b.getTime()) {
                return interpolateBetween(a, b, timeSeconds);
            }
        }

        // 理论不会到这里，但为了安全：
        return toSample(kfB);
    }

    // ==========================================================
    // 插值逻辑
    // ==========================================================

    /**
     * 在两个关键帧之间插值。
     */
    private EntitySample interpolateBetween(EntityKeyframe a, EntityKeyframe b, float time) {
        float dt = b.getTime() - a.getTime();
        if (dt <= 0) {
            return toSample(a);
        }

        float t = (time - a.getTime()) / dt;
        t = Math.max(0f, Math.min(1f, t));

        EntitySample sampleA = toSample(a);
        EntitySample sampleB = toSample(b);

        EntitySample out = new EntitySample();
        TimelineInterpolation interp = a.getInterpolation();   // 插值方式读取前关键帧的定义

        out.lerpFrom(sampleA, sampleB, t, interp);

        return out;
    }

    // ==========================================================
    // 将关键帧转换为运行时操作 Sample
    // ==========================================================

    /**
     * 将关键帧转换为采样（单关键帧时使用）。
     */
    private EntitySample toSample(EntityKeyframe kf) {
        EntitySample out = new EntitySample();
        out.enabled = true;

        // 位置和旋转
        out.position.set(kf.getPosition());
        out.rotation.set(kf.getRotation());
        out.scale.set(kf.getScale());

        // 动作和状态
        out.actionType = kf.getActionType();
        out.actionSpeed = kf.getActionSpeed();
        out.actionLoop = kf.isActionLoop();
        out.visibility = kf.getVisibility();

        // 行为和交互
        out.active = kf.isActive();
        out.displayName = kf.getDisplayName();
        out.showNameTag = kf.isShowNameTag();
        out.entityType = kf.getEntityType();

        return out;
    }

    // ==========================================================
    // 默认实体 sample（无关键帧时）
    // ==========================================================

    /**
     * 创建默认禁用实体采样。
     */
    private EntitySample defaultDisabledEntity() {
        EntitySample s = new EntitySample();
        s.enabled = false; // 默认不启用，使用原版行为
        s.entityType = EntityKeyframe.EntityType.CUSTOM;
        s.actionType = EntityKeyframe.ActionType.IDLE;
        s.actionSpeed = 1.0f;
        s.actionLoop = true;
        s.visibility = 1.0f;
        s.active = false;
        s.showNameTag = false;
        return s;
    }

    /**
     * 获取所有关键帧（用于调试）。
     */
    public List<EntityKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
}

