package com.director.actor.rig;

import com.director.actor.action.ActorActionType;
import com.director.actor.runtime.ActorRuntime;

/**
 * HumanoidRigProfile v1.0
 *
 * <p>人形 Rig Profile（村民、玩家、僵尸等）。
 *
 * <p>设计目标（导演感受）：
 * <ul>
 *   <li>RUN：明显的前倾 + 手脚摆动加快</li>
 *   <li>WALK：自然摆臂</li>
 *   <li>IDLE：轻微呼吸感</li>
 *   <li>DANCE：节奏性摆动</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class HumanoidRigProfile implements ActorRigProfile {

    private final RigProfileStyle style = new RigProfileStyle();

    @Override
    public boolean supports(ActorActionType action) {
        return switch (action) {
            case IDLE, WALK, RUN, DANCE, CARRY, THROW_PREPARE -> true;
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
                runtime.setBodyLean(0f);
                runtime.setArmSwing(0.1f * style.armExpressiveness);
                runtime.setLegSwing(0.05f * style.legExpressiveness);
                runtime.setStepFrequency(0.2f * tempo);
                runtime.setBodyBob((float) Math.sin(runtime.getTime() * 0.5f) * 0.02f); // 轻微呼吸
            }

            case WALK -> {
                runtime.setBodyLean(0.05f * style.leanBias);
                runtime.setArmSwing(0.6f * motion * style.armExpressiveness);
                runtime.setLegSwing(0.5f * motion * style.legExpressiveness);
                runtime.setStepFrequency((1.0f + intensity) * tempo);
                runtime.setBodyBob(0.05f * intensity * style.motionScale);
            }

            case RUN -> {
                runtime.setBodyLean(0.15f * style.leanBias);
                runtime.setArmSwing(1.2f * motion * style.armExpressiveness);
                runtime.setLegSwing(1.0f * motion * style.legExpressiveness);
                runtime.setStepFrequency((2.0f + intensity) * tempo);
                runtime.setBodyBob(0.1f * intensity * style.motionScale);
            }

            case DANCE -> {
                runtime.setArmSwing(1.5f);
                runtime.setLegSwing(1.0f);
                runtime.setBodyTwist((float) Math.sin(runtime.getTime() * 6f) * 0.3f);
                runtime.setStepFrequency(1.5f);
                runtime.setBodyBob((float) Math.sin(runtime.getTime() * 4f) * 0.1f);
            }

            case CARRY -> {
                runtime.lockArmsForward();
                runtime.setBodyLean(0.1f);
                runtime.setStepFrequency(0.8f);
            }

            case THROW_PREPARE -> {
                runtime.lockArmsForward();
                runtime.setBodyLean(0.05f);
                runtime.setStepFrequency(0f);
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

    @Override
    public RigPoseResult buildPose(
            ActorActionType action,
            float intensity,
            float time
    ) {
        Pose pose = new Pose();
        PoseMask mask = PoseMask.full();

        // 应用风格系数
        float motion = intensity * style.motionScale;
        float tempo = style.tempo;
        float step = time * (2.0f + intensity) * tempo;

        switch (action) {
            case IDLE -> {
                pose.bodyPitch = 0f;
                pose.leftArmPitch = 0.1f * style.armExpressiveness;
                pose.rightArmPitch = 0.1f * style.armExpressiveness;
                pose.leftLegPitch = 0.05f * style.legExpressiveness;
                pose.rightLegPitch = 0.05f * style.legExpressiveness;
            }

            case WALK -> {
                pose.bodyPitch = 0.05f * style.leanBias;
                pose.leftArmPitch = (float) Math.sin(step) * 0.6f * motion * style.armExpressiveness;
                pose.rightArmPitch = (float) Math.sin(step + Math.PI) * 0.6f * motion * style.armExpressiveness;
                pose.leftLegPitch = (float) Math.sin(step + Math.PI) * 0.5f * motion * style.legExpressiveness;
                pose.rightLegPitch = (float) Math.sin(step) * 0.5f * motion * style.legExpressiveness;
            }

            case RUN -> {
                pose.bodyPitch = 0.15f * style.leanBias;
                pose.leftArmPitch = (float) Math.sin(step * 2f) * 1.2f * motion * style.armExpressiveness;
                pose.rightArmPitch = (float) Math.sin(step * 2f + Math.PI) * 1.2f * motion * style.armExpressiveness;
                pose.leftLegPitch = (float) Math.sin(step * 2f + Math.PI) * 1.0f * motion * style.legExpressiveness;
                pose.rightLegPitch = (float) Math.sin(step * 2f) * 1.0f * motion * style.legExpressiveness;
            }

            case DANCE -> {
                pose.bodyYaw = (float) Math.sin(time * 6f * tempo) * 0.3f * motion;
                pose.leftArmPitch = (float) Math.sin(time * 6f * tempo) * 1.5f * motion * style.armExpressiveness;
                pose.rightArmPitch = (float) Math.sin(time * 6f * tempo + Math.PI) * 1.5f * motion * style.armExpressiveness;
                pose.leftLegPitch = (float) Math.sin(time * 4f * tempo) * 1.0f * motion * style.legExpressiveness;
                pose.rightLegPitch = (float) Math.sin(time * 4f * tempo + Math.PI) * 1.0f * motion * style.legExpressiveness;
            }

            case CARRY -> {
                pose.bodyPitch = 0.1f;
                pose.leftArmPitch = 0.5f; // 向前
                pose.rightArmPitch = 0.5f; // 向前
            }

            case THROW_PREPARE -> {
                pose.bodyPitch = 0.05f;
                pose.leftArmPitch = 0.8f; // 向后准备
                pose.rightArmPitch = 0.8f; // 向后准备
            }

            default -> {
                // 默认站姿
                pose.reset();
            }
        }

        return new RigPoseResult(pose, mask);
    }
}
