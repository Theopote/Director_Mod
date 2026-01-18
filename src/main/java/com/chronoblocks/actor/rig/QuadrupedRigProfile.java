package com.chronoblocks.actor.rig;

import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.actor.runtime.ActorRuntime;

/**
 * QuadrupedRigProfile v1.0
 *
 * <p>四足动物 Rig Profile（牛、羊、马等）。
 *
 * <p>设计目标：
 * <ul>
 *   <li>RUN：步频大幅提高 + 身体前倾</li>
 *   <li>WALK：稳定节奏</li>
 *   <li>IDLE：轻微头部摆动</li>
 *   <li>GRAZE：头部向下</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class QuadrupedRigProfile implements ActorRigProfile {

    private final RigProfileStyle style = new RigProfileStyle();

    @Override
    public boolean supports(ActorActionType action) {
        return switch (action) {
            case IDLE, WALK, RUN, GRAZE -> true;
            default -> false;
        };
    }

    @Override
    public void applyAction(
            ActorRuntime runtime,
            ActorActionType action,
            float intensity,
            float deltaTime
    ) {
        // 更新时间（用于周期性动画）
        runtime.setTime(runtime.getTime() + deltaTime);

        // 应用风格系数
        float motion = intensity * style.motionScale;
        float tempo = style.tempo;

        switch (action) {
            case IDLE -> {
                runtime.setBodyBob((float) Math.sin(runtime.getTime() * 0.8f) * 0.05f * style.motionScale);
                runtime.setStepFrequency(0f);
                runtime.setBodyLean(0f);
            }

            case WALK -> {
                runtime.setStepFrequency(1.2f * tempo);
                runtime.setLegSwing(0.4f * motion * style.legExpressiveness);
                runtime.setBodyBob(0.1f * intensity * style.motionScale);
                runtime.setBodyLean(0.05f * style.leanBias);
            }

            case RUN -> {
                runtime.setStepFrequency((2.5f + intensity) * tempo);
                runtime.setLegSwing(0.8f * style.legExpressiveness);
                runtime.setBodyLean(0.2f * style.leanBias);
                runtime.setBodyBob(0.25f * style.motionScale);
            }

            case GRAZE -> {
                runtime.setHeadDown(true);
                runtime.setStepFrequency(0f);
                runtime.setBodyLean(0.1f);
            }

            default -> {
                runtime.resetPose();
            }
        }
    }

    @Override
    public RigProfileStyle getStyle() {
        return style;
    }
}
