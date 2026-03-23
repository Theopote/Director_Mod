package com.director.actor.prop;

/**
 * ActorPropSlot v1.0
 *
 * <p>演员道具槽位：定义道具可以"附着"在演员的哪个部位
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 语义清晰（手、头、背等）</li>
 *   <li>✅ 可扩展（未来支持更多槽位）</li>
 * </ul>
 *
 * <p>使用示例：
 * <ul>
 *   <li>MAIN_HAND - 主手（右手）</li>
 *   <li>OFF_HAND - 副手（左手）</li>
 *   <li>BOTH_HANDS - 双手（抱方块）</li>
 *   <li>HEAD - 头部（戴帽子）</li>
 *   <li>BACK - 背部（背背包）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public enum ActorPropSlot {
    /**
     * 主手（右手）
     * <p>道具出现在演员的右手
     */
    MAIN_HAND,

    /**
     * 副手（左手）
     * <p>道具出现在演员的左手
     */
    OFF_HAND,

    /**
     * 双手
     * <p>道具被双手抱住（如抱方块）
     */
    BOTH_HANDS,

    /**
     * 头部
     * <p>道具出现在演员的头部（如戴帽子）
     */
    HEAD,

    /**
     * 背部
     * <p>道具出现在演员的背部（如背背包）
     */
    BACK
}

