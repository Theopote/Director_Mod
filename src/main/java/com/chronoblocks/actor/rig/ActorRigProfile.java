package com.chronoblocks.actor.rig;

import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.actor.runtime.ActorRuntime;

/**
 * ActorRigProfile v1.0
 *
 * <p>描述一个 Actor "能做什么动作 + 如何表现这些动作"的策略集合。
 *
 * <p>它不是：
 * <ul>
 *   <li>❌ 骨骼定义</li>
 *   <li>❌ 动画资源</li>
 *   <li>❌ 实体类型</li>
 * </ul>
 *
 * <p>它是一个行为解释器（Behavior Interpreter）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>RigProfile 不关心 Timeline</li>
 *   <li>RigProfile 不关心 Clip</li>
 *   <li>它只解释："现在这个动作，身体该怎么动"</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface ActorRigProfile {

    /**
     * 该 Rig 支持哪些动作语义。
     *
     * @param action 动作类型
     * @return 如果支持则返回 true
     */
    boolean supports(ActorActionType action);

    /**
     * 应用动作语义到 Runtime 表现。
     *
     * @param runtime 身体控制参数
     * @param action 动作类型
     * @param intensity 动作强度 [0.0-1.0]
     * @param deltaTime 时间增量（秒）
     */
    void applyAction(
            ActorRuntime runtime,
            ActorActionType action,
            float intensity,
            float deltaTime
    );

    /**
     * 获取风格参数（用于 UI 编辑）。
     *
     * @return RigProfileStyle，如果此 RigProfile 不支持风格调节则返回 null
     */
    RigProfileStyle getStyle();
}
