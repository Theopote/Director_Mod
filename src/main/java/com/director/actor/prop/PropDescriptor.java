package com.director.actor.prop;

import java.util.Objects;

/**
 * PropDescriptor v1.0
 *
 * <p>道具描述符：定义道具的视觉表现和元数据
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 不依赖 Minecraft 类（Block / Entity）</li>
 *   <li>✅ 只描述"视觉表现"，不描述"世界行为"</li>
 *   <li>✅ 可序列化，可持久化</li>
 * </ul>
 *
 * <p>⚠️ 注意：
 * <ul>
 *   <li>Prop 是"演员表演时临时携带的道具"</li>
 *   <li>不是世界中的真实方块/实体</li>
 *   <li>Prop = Actor 的一个"表演附属物（Attachment）"</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class PropDescriptor {

    /** 道具 ID（唯一标识符） */
    private final String id;

    /** 显示名称 */
    private final String displayName;

    /** 视觉类型 */
    private final PropVisualType visualType;

    /** 方块状态 ID（如果 visualType == BLOCK） */
    private final String blockStateId;

    /**
     * 构造函数
     *
     * @param id 道具 ID（如 "oak_planks"）
     * @param displayName 显示名称（如 "Oak Planks"）
     * @param visualType 视觉类型
     * @param blockStateId 方块状态 ID（如 "minecraft:oak_planks"），如果 visualType != BLOCK 可以为 null
     */
    public PropDescriptor(
            String id,
            String displayName,
            PropVisualType visualType,
            String blockStateId
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.displayName = displayName != null && !displayName.isBlank() 
                ? displayName 
                : id;
        this.visualType = visualType != null ? visualType : PropVisualType.BLOCK;
        this.blockStateId = blockStateId;
    }

    // ========== Getters ==========

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PropVisualType getVisualType() {
        return visualType;
    }

    public String getBlockStateId() {
        return blockStateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropDescriptor that = (PropDescriptor) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("PropDescriptor{id='%s', displayName='%s', visualType=%s}",
                id, displayName, visualType);
    }
}

