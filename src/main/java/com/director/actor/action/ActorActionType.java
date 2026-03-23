package com.director.actor.action;

/**
 * ActorActionType v1.0
 *
 * <p>动作语义枚举 - 导演词汇表
 *
 * <p>这是整个"动作语义系统"的核心枚举，定义了导演可以使用的动作语义。
 * 例如：RUN（奔跑感觉）、WALK（行走感觉）、DANCE（跳舞）等。
 *
 * <p>设计原则：
 * <ul>
 *   <li>这是"导演意图"，不是"实现细节"</li>
 *   <li>不绑定 Minecraft 模型</li>
 *   <li>不要求骨骼一致</li>
 *   <li>不锁死动画实现</li>
 * </ul>
 *
 * <p>⚠️ 注意：不要一开始就追求"完整"，这是导演可理解的动作集合。
 *
 * @author Director Team
 * @version 1.0
 */
public enum ActorActionType {

    // ========== 基础动作 ==========

    /**
     * 站立/待机
     */
    IDLE,

    /**
     * 行走
     */
    WALK,

    /**
     * 奔跑
     */
    RUN,

    // ========== 表演动作 ==========

    /**
     * 跳舞
     */
    DANCE,

    /**
     * 庆祝
     */
    CELEBRATE,

    /**
     * 坐下
     */
    SIT,

    /**
     * 冥想
     */
    MEDITATE,

    // ========== 操作动作 ==========

    /**
     * 搬运
     */
    CARRY,

    /**
     * 投掷准备
     */
    THROW_PREPARE,

    /**
     * 攻击
     */
    ATTACK,

    // ========== 动物向动作 ==========

    /**
     * 吃草
     */
    GRAZE,

    /**
     * 咆哮
     */
    ROAR,

    // ========== 扩展 ==========

    /**
     * 自定义动作（预留）
     */
    CUSTOM
}
