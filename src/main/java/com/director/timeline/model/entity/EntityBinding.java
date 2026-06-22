package com.director.timeline.model.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * EntityBinding v1.0
 *
 * <p>描述"演员"如何绑定到世界里的实体。
 *
 * <p>绑定模式：
 * <ul>
 *   <li>SPAWN_NEW - 播放时生成一个实体（导演虚拟演员）</li>
 *   <li>BIND_EXISTING - 绑定已存在实体（比如真实玩家、场景里已有 NPC）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class EntityBinding {

    /**
     * 绑定模式
     */
    public enum Mode {
        /**
         * 生成新实体
         */
        SPAWN_NEW("生成新实体"),

        /**
         * 绑定已存在的实体
         */
        BIND_EXISTING("绑定已存在实体");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Mode mode = Mode.SPAWN_NEW;

    // ========== BIND_EXISTING 用 ==========

    /** 绑定目标实体 UUID（若能获取到） */
    private UUID targetEntityUuid;

    /** 绑定玩家名（用于玩家演员；可选） */
    private String targetPlayerName;

    // ========== SPAWN_NEW 用 ==========

    /** 生成用的实体类型 ID（建议用 Identifier 字符串存，避免直接依赖 MC 类） */
    private String entityTypeId = "minecraft:villager";

    /** 是否持久化：剪辑结束后是否保留实体 */
    private boolean persistent = true;

    /**
     * 默认构造函数
     */
    public EntityBinding() {
    }

    /**
     * 创建生成新实体的绑定
     *
     * @param entityTypeId 实体类型ID（如 "minecraft:villager"）
     * @return 绑定对象
     */
    public static EntityBinding spawnNew(String entityTypeId) {
        EntityBinding b = new EntityBinding();
        b.mode = Mode.SPAWN_NEW;
        b.entityTypeId = Objects.requireNonNull(entityTypeId, "entityTypeId");
        return b;
    }

    /**
     * 创建绑定已存在实体的绑定
     *
     * @param uuid 实体UUID
     * @return 绑定对象
     */
    public static EntityBinding bindExisting(UUID uuid) {
        EntityBinding b = new EntityBinding();
        b.mode = Mode.BIND_EXISTING;
        b.targetEntityUuid = Objects.requireNonNull(uuid, "uuid");
        return b;
    }

    // ========== Getters and Setters ==========

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.SPAWN_NEW;
    }

    public UUID getTargetEntityUuid() {
        return targetEntityUuid;
    }

    public void setTargetEntityUuid(UUID targetEntityUuid) {
        this.targetEntityUuid = targetEntityUuid;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public void setTargetPlayerName(String targetPlayerName) {
        this.targetPlayerName = (targetPlayerName == null || targetPlayerName.isBlank())
            ? null
            : targetPlayerName.trim();
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public void setEntityTypeId(String entityTypeId) {
        this.entityTypeId = (entityTypeId == null || entityTypeId.isBlank())
            ? "minecraft:villager"
            : entityTypeId;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }
}

