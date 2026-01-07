package com.chronoblocks.actor.prop;

/**
 * PropVisualType v1.0
 *
 * <p>道具视觉类型：定义道具在客户端如何渲染
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 只描述视觉表现，不描述世界行为</li>
 *   <li>✅ 可扩展（未来支持 Geo / 自定义模型）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum PropVisualType {
    /**
     * 方块模型
     * <p>使用 Minecraft 方块模型渲染
     */
    BLOCK,

    /**
     * 掉落物模型
     * <p>使用 Minecraft 物品模型渲染（3D 物品）
     */
    ITEM,

    /**
     * 自定义模型（未来扩展）
     * <p>使用 GeckoLib / 自定义模型渲染
     */
    CUSTOM
}

