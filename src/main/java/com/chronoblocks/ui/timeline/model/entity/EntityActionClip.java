package com.chronoblocks.ui.timeline.model.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * EntityActionClip v1.0
 *
 * <p>Action：一次行为事件（可在片段内部触发一次或多次）。
 *
 * <p>v1 简化：默认在片段开始触发一次。
 * v2 可扩展：actionMarkers（多个触发点）
 *
 * <p>示例：
 * <ul>
 *   <li>挥剑</li>
 *   <li>放置方块（建议是"导演动作"，不一定真的 setBlock）</li>
 *   <li>对话（字幕轨道联动）</li>
 *   <li>发出声音（音频轨道联动）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EntityActionClip extends EntityClip {

    public enum ActionType {
        INTERACT("交互"),
        ATTACK("攻击"),
        PLACE_BLOCK("放置方块"),
        BREAK_BLOCK("破坏方块"),
        PLAY_SOUND("播放声音"),
        SAY_LINE("对话"),
        CUSTOM("自定义");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private ActionType actionType = ActionType.INTERACT;

    /** 参数表：比如 {soundId:"minecraft:block.stone.place", volume:0.8} */
    private final Map<String, Object> params = new HashMap<>();

    /** 是否只触发一次（默认 true），否则可按 tick/间隔触发（后续扩展） */
    private boolean triggerOnce = true;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityActionClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTION;
    }

    // ========== Getters and Setters ==========

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType != null ? actionType : ActionType.INTERACT;
    }

    /**
     * 获取参数表
     *
     * @return 参数表（可修改）
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 设置参数
     *
     * @param key 参数键
     * @param value 参数值
     */
    public void setParam(String key, Object value) {
        if (key != null && !key.isBlank()) {
            if (value == null) {
                params.remove(key);
            } else {
                params.put(key, value);
            }
        }
    }

    /**
     * 获取参数
     *
     * @param key 参数键
     * @return 参数值，如果不存在则返回 null
     */
    public Object getParam(String key) {
        return params.get(key);
    }

    public boolean isTriggerOnce() {
        return triggerOnce;
    }

    public void setTriggerOnce(boolean triggerOnce) {
        this.triggerOnce = triggerOnce;
    }
}

