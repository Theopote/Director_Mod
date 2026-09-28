package com.director.actor.rig;

import com.director.actor.action.ActorActionType;
import com.director.actor.runtime.ActorRuntime;

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
 * @author Director Team
 * @version 1.0
 */
public interface ActorRigProfile {

    /** Deterministic timeline entry point; clears state left by a previously sampled action. */
    default RigPoseResult sampleAt(ActorRuntime runtime, ActorActionType action, float intensity, float time) {
        runtime.resetPose();
        runtime.setTime(Math.max(0f, time));
        ActorActionType supportedAction = supports(action) ? action : ActorActionType.IDLE;
        applyAction(runtime, supportedAction, intensity, 0f);
        return buildPose(supportedAction, intensity, runtime.getTime());
    }

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

    /**
     * 构建姿态（用于 Animation ↔ RigProfile 混合）。
     *
     * <p>RigProfile 不再直接写 ActorRuntime 的字段，
     * 而是生成一个"语义 Pose" + Mask（通常是全身）。
     *
     * <p>注意：此方法用于混合系统，原有的 applyAction 方法仍然保留用于向后兼容。
     *
     * @param action 动作类型
     * @param intensity 动作强度 [0.0-1.0]
     * @param time 当前时间（秒，用于周期性动画）
     * @return RigPoseResult，包含姿态和控制掩码
     */
    RigPoseResult buildPose(
            ActorActionType action,
            float intensity,
            float time
    );
}
