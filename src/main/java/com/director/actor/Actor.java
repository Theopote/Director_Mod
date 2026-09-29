package com.director.actor;

import com.director.actor.rig.ActorRigProfile;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.QuadrupedRigProfile;

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
 * @author Director Team
 * @version 1.0
 */
public final class Actor {

    private final UUID actorId;
    private String name;
    private ActorType type;
    private ActorRigProfile rigProfile;
    /**
     * 可选的自定义皮肤文件名，相对于当前世界的 director 目录。
     * 例如：{@code skins/<actorId>.png}。
     * <p>
     * 为空或 null 表示使用默认外观。
     */
    private String skinFileName;

    /**
     * 仅当 {@link #type} 为 {@link ActorType#CUSTOM} 时使用：
     * 指向 {@code CustomRigLibrary} 中一个用户自定义骨架的 rig id。
     * <p>
     * 为空表示该 CUSTOM 演员尚未绑定自定义骨架，不能使用关键帧动画。
     */
    private String customRigId;

    /**
     * 仅当 {@link #type} 为 {@link ActorType#CUSTOM} 且绑定了非人形拓扑骨架时使用：
     * 指向 {@code CustomModelLibrary} 中一个方块拼接模型的 id，用于运行时渲染。
     * <p>
     * 为空表示该 CUSTOM 演员没有可见外观（仅能驱动骨架，无法渲染）。
     */
    private String customModelId;

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
        if (type == null || type == this.type) return;
        this.type = type;
        this.rigProfile = createDefaultRigProfile(type);
    }

    public ActorRigProfile getRigProfile() {
        return rigProfile;
    }

    public void setRigProfile(ActorRigProfile rigProfile) {
        this.rigProfile = rigProfile;
    }

    public String getSkinFileName() {
        return skinFileName;
    }

    public void setSkinFileName(String skinFileName) {
        this.skinFileName = (skinFileName == null || skinFileName.isBlank()) ? null : skinFileName.trim();
    }

    public String getCustomRigId() {
        return customRigId;
    }

    public void setCustomRigId(String customRigId) {
        this.customRigId = (customRigId == null || customRigId.isBlank()) ? null : customRigId.trim();
    }

    public String getCustomModelId() {
        return customModelId;
    }

    public void setCustomModelId(String customModelId) {
        this.customModelId = (customModelId == null || customModelId.isBlank()) ? null : customModelId.trim();
    }

    /**
     * 解析此演员当前可用于关键帧动画的骨架（{@code null} 表示暂不支持关键帧动画，
     * 例如四足/村民类型，或尚未绑定骨架的 CUSTOM 类型）。
     */
    public java.util.Optional<com.director.actor.rig.skeleton.RigDefinition> resolveRigDefinition() {
        return com.director.actor.rig.ActorRigCompatibility.resolveRig(this);
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
                ", skinFileName='" + skinFileName + '\'' +
                ", customRigId='" + customRigId + '\'' +
                ", customModelId='" + customModelId + '\'' +
                '}';
    }
}

