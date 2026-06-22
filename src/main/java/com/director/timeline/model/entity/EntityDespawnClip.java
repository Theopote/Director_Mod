package com.director.timeline.model.entity;

/**
 * EntityDespawnClip v1.0
 *
 * <p>Despawn：离场片段。
 *
 * <p>控制实体的离场方式。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityDespawnClip extends EntityClip {

    /**
     * 离场方式
     */
    public enum DespawnMode {
        /**
         * 隐藏（不删除实体）
         */
        HIDE("隐藏"),

        /**
         * 删除实体
         */
        REMOVE("删除");

        private final String displayName;

        DespawnMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private DespawnMode mode = DespawnMode.REMOVE;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityDespawnClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.DESPAWN;
    }

    // ========== Getters and Setters ==========

    public DespawnMode getMode() {
        return mode;
    }

    public void setMode(DespawnMode mode) {
        this.mode = mode != null ? mode : DespawnMode.REMOVE;
    }
}

