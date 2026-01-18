package com.chronoblocks.actor;

import com.chronoblocks.actor.rig.ActorRigProfile;
import com.chronoblocks.actor.rig.HumanoidRigProfile;
import com.chronoblocks.actor.rig.QuadrupedRigProfile;

import java.util.Objects;
import java.util.UUID;

/**
 * Actor v1.0
 *
 * <p>Actor = 虚拟演员
 * <ul>
 *   <li>不等同于 Minecraft Entity</li>
 *   <li>只描述"角色是谁"</li>
 * </ul>
 *
 * <p>核心特点：
 * <ul>
 *   <li>❌ 没有位置</li>
 *   <li>❌ 没有时间</li>
 *   <li>❌ 没有 Entity / World</li>
 *   <li>✅ 可以被任意 Timeline / Runtime 使用</li>
 *   <li>✅ 可以被复制 / 克隆（UUID 可换）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class Actor {

    private final UUID actorId;
    private String name;
    private ActorType type;
    private ActorRigProfile rigProfile;

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
        // 根据类型自动设置默认 RigProfile
        this.rigProfile = createDefaultRigProfile(type);
    }

    // ======================
    // Getters / Setters
    // ======================

    public UUID getActorId() {
        return actorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? this.name : name;
    }

    public ActorType getType() {
        return type;
    }

    public void setType(ActorType type) {
        this.type = type != null ? type : this.type;
        // 类型改变时，如果 rigProfile 为空，自动设置默认值
        if (this.rigProfile == null) {
            this.rigProfile = createDefaultRigProfile(this.type);
        }
    }

    public ActorRigProfile getRigProfile() {
        return rigProfile;
    }

    public void setRigProfile(ActorRigProfile rigProfile) {
        this.rigProfile = rigProfile;
    }

    /**
     * 根据 ActorType 创建默认的 RigProfile。
     *
     * @param type 演员类型
     * @return 默认 RigProfile，如果类型未知则返回 HumanoidRigProfile
     */
    private static ActorRigProfile createDefaultRigProfile(ActorType type) {
        if (type == null) {
            return new HumanoidRigProfile();
        }

        return switch (type) {
            case VILLAGER, PLAYER, ZOMBIE, SKELETON -> new HumanoidRigProfile();
            case COW, SHEEP -> new QuadrupedRigProfile();
            default -> new HumanoidRigProfile(); // 默认使用人形
        };
    }

    // ======================
    // Object methods
    // ======================

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
                '}';
    }
}

