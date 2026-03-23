package com.director.ui.timeline.model.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Actor v1.0
 *
 * <p>Actor（演员）是 Director 的第一等公民，是"导演概念"，不是游戏对象。
 *
 * <p>核心原则：
 * <ul>
 *   <li>Actor 存在于 Timeline / Project 中</li>
 *   <li>Actor 有角色、名字、类型</li>
 *   <li>Actor 有表演（EntityTrack + Clips）</li>
 *   <li>Actor ≠ Minecraft Entity（绝不等同）</li>
 * </ul>
 *
 * <p>Minecraft Entity 只是 Actor 的一种"表现形式（Projection）"。
 *
 * <p>设计特点：
 * <ul>
 *   <li>不包含世界信息</li>
 *   <li>不包含位置</li>
 *   <li>不包含 Entity UUID</li>
 *   <li>只描述"我是谁"</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class Actor {

    /**
     * Actor 类型
     */
    public enum ActorType {
        VILLAGER("村民"),
        COW("牛"),
        ZOMBIE("僵尸"),
        SKELETON("骷髅"),
        PLAYER("玩家"),
        CUSTOM("自定义");

        private final String displayName;

        ActorType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 演员ID（唯一标识） */
    private final UUID actorId;

    /** 演员名称（如 "商人A", "守卫B"） */
    private String name;

    /** 演员类型 */
    private ActorType type;

    /** 演员模型（外观，未来扩展） */
    private String modelId; // 例如 "minecraft:villager" 或自定义模型路径

    /**
     * 构造函数
     *
     * @param name 演员名称
     * @param type 演员类型
     */
    public Actor(String name, ActorType type) {
        this(UUID.randomUUID(), name, type);
    }

    /**
     * 完整构造函数
     *
     * @param actorId 演员ID
     * @param name 演员名称
     * @param type 演员类型
     */
    public Actor(UUID actorId, String name, ActorType type) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.name = (name == null || name.isBlank()) ? "Actor" : name;
        this.type = type != null ? type : ActorType.CUSTOM;
        this.modelId = type != null ? getDefaultModelId(type) : "minecraft:villager";
    }

    // ========== Getters and Setters ==========

    public UUID getActorId() {
        return actorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Actor" : name;
    }

    public ActorType getType() {
        return type;
    }

    public void setType(ActorType type) {
        this.type = type != null ? type : ActorType.CUSTOM;
        // 类型改变时，更新默认模型ID
        if (this.modelId == null || this.modelId.equals(getDefaultModelId(this.type))) {
            this.modelId = getDefaultModelId(this.type);
        }
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = (modelId == null || modelId.isBlank()) ? getDefaultModelId(this.type) : modelId;
    }

    // ========== 工具方法 ==========

    /**
     * 获取默认模型ID（根据类型）
     */
    private static String getDefaultModelId(ActorType type) {
        if (type == null) {
            return "minecraft:villager";
        }
        return switch (type) {
            case VILLAGER -> "minecraft:villager";
            case COW -> "minecraft:cow";
            case ZOMBIE -> "minecraft:zombie";
            case SKELETON -> "minecraft:skeleton";
            case PLAYER -> "minecraft:player";
            case CUSTOM -> "minecraft:villager";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Actor actor = (Actor) o;
        return Objects.equals(actorId, actor.actorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId);
    }

    @Override
    public String toString() {
        return "Actor{" +
                "actorId=" + actorId +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", modelId='" + modelId + '\'' +
                '}';
    }
}

