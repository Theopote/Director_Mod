package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.EntityActionClip;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ActionTriggerCache v1.0
 *
 * <p>用于管理 Action Clip 的触发状态，防止重复触发。
 *
 * <p>规则：
 * <ul>
 *   <li>只在"刚进入 Clip 的那一帧"触发</li>
 *   <li>同一 Clip 只触发一次（除非 rewind）</li>
 *   <li>支持 rewind / loop（当时间离开 Clip 时清除记录）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class ActionTriggerCache {

    /** 已触发的 Clip ID 集合 */
    private final Set<UUID> triggered = new HashSet<>();

    /**
     * 检查是否应该触发 Action Clip。
     *
     * @param clip Action Clip
     * @param time 当前时间（秒）
     * @return 如果应该触发则返回 true
     */
    public boolean shouldTrigger(EntityActionClip clip, float time) {
        if (clip == null) {
            return false;
        }

        UUID key = clip.getId();
        boolean inside = time >= clip.getStartTime() && time <= clip.getEndTime();
        boolean already = triggered.contains(key);

        if (inside && !already) {
            // 刚进入 Clip 且未触发过 → 触发
            triggered.add(key);
            return true;
        }

        if (!inside) {
            // 时间离开 Clip → 清除记录（支持 rewind / loop）
            triggered.remove(key);
        }

        return false;
    }

    /**
     * 清除所有触发记录（用于重置/跳转时间）。
     */
    public void clear() {
        triggered.clear();
    }

    /**
     * 清除特定 Clip 的触发记录。
     *
     * @param clipId Clip ID
     */
    public void clearClip(UUID clipId) {
        triggered.remove(clipId);
    }
}

