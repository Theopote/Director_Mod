package com.director.timeline.playback.entity;

import com.director.ui.timeline.model.entity.AnimationEvent;
import com.director.ui.timeline.model.entity.EntityAnimationClip;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * AnimationEventTriggerCache v1.0
 *
 * <p>用于管理 AnimationEvent 的触发状态，防止重复触发。
 *
 * <p>规则：
 * <ul>
 *   <li>只在动画进度达到事件时间点时触发</li>
 *   <li>同一事件只触发一次（除非 rewind）</li>
 *   <li>支持 rewind / loop（当时间离开 Clip 时清除记录）</li>
 * </ul>
 *
 * <p>与 ActionTriggerCache 的区别：
 * <ul>
 *   <li>ActionTriggerCache - 管理 ActionClip 的触发（基于 Clip 时间）</li>
 *   <li>AnimationEventTriggerCache - 管理 AnimationEvent 的触发（基于归一化时间）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationEventTriggerCache {

    /** 已触发的事件（clipId + normalizedTime 组合作为 key） */
    private final Set<String> triggered = new HashSet<>();

    /**
     * 检查是否应该触发 AnimationEvent
     *
     * @param clip Animation Clip
     * @param event Animation Event
     * @param normalizedTime 当前归一化时间（0.0 ~ 1.0）
     * @return 如果应该触发则返回 true
     */
    public boolean shouldTrigger(EntityAnimationClip clip, AnimationEvent event, float normalizedTime) {
        if (clip == null || event == null) {
            return false;
        }

        String key = buildKey(clip.getId(), event.getNormalizedTime());
        boolean reached = normalizedTime >= event.getNormalizedTime();
        boolean already = triggered.contains(key);

        if (reached && !already) {
            // 达到时间点且未触发过 → 触发
            triggered.add(key);
            return true;
        }

        if (normalizedTime < event.getNormalizedTime()) {
            // 时间回退到事件之前 → 清除记录（支持 rewind / loop）
            triggered.remove(key);
        }

        return false;
    }

    /**
     * 清除所有触发记录（用于重置/跳转时间）
     */
    public void clear() {
        triggered.clear();
    }

    /**
     * 清除特定 Clip 的所有事件触发记录
     *
     * @param clipId Clip ID
     */
    public void clearClip(UUID clipId) {
        if (clipId == null) {
            return;
        }
        String prefix = clipId.toString() + ":";
        triggered.removeIf(key -> key.startsWith(prefix));
    }

    /**
     * 构建唯一键
     *
     * @param clipId Clip ID
     * @param normalizedTime 归一化时间
     * @return 唯一键
     */
    private String buildKey(UUID clipId, float normalizedTime) {
        return clipId.toString() + ":" + normalizedTime;
    }
}

