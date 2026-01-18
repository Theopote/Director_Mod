package com.chronoblocks.actor.rig;

/**
 * RigProfileStyle v1.0
 *
 * <p>RigProfile 的风格参数，用于调节演员的"表演性格"。
 *
 * <p>这是 Actor 级的"表演风格"，不是 Clip 级的"时间变化"。
 * <ul>
 *   <li>✅ 描述"这个演员整体是怎么表演的"</li>
 *   <li>❌ 不随时间插值</li>
 *   <li>❌ 不写进 Timeline</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>风格只是"调味"，不改变动作语义</li>
 *   <li>所有参数都有合理的默认值（1.0）</li>
 *   <li>参数范围经过精心设计，保证视觉合理性</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class RigProfileStyle {

    /** 整体动作幅度（0.5 保守 → 1.5 夸张） */
    public float motionScale = 1.0f;

    /** 动作节奏（0.7 慢 → 1.3 快） */
    public float tempo = 1.0f;

    /** 身体前倾偏好（0.5 不倾向 → 1.5 更前倾） */
    public float leanBias = 1.0f;

    /** 手臂表现欲（0.3 内敛 → 1.5 夸张） */
    public float armExpressiveness = 1.0f;

    /** 腿部表现欲（0.3 内敛 → 1.5 夸张） */
    public float legExpressiveness = 1.0f;
}
