package com.chronoblocks.actor.rig;

/**
 * PoseBlender v1.0
 *
 * <p>姿态混合器，这是 Animation ↔ RigProfile 的唯一交汇点。
 *
 * <p>混合规则（极其清晰，不会"玄学混合"）：
 * <ul>
 *   <li>高层覆盖低层</li>
 *   <li>Animation 有控制权 → 使用 Animation 的姿态</li>
 *   <li>Animation 无控制权 → 使用 RigProfile 的姿态</li>
 * </ul>
 *
 * <p>分层模型（从高到低）：
 * <ol>
 *   <li>Layer 3（最高）：AnimationClip（明确动画）</li>
 *   <li>Layer 2：RigProfile（语义驱动）</li>
 *   <li>Layer 1（最低）：Base Pose（默认站姿）</li>
 * </ol>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class PoseBlender {

    /**
     * 混合姿态。
     *
     * <p>规则：Animation 有控制权的部位使用 Animation 的姿态，
     * Animation 无控制权的部位使用 RigProfile 的姿态。
     *
     * @param base 基础姿态（默认站姿）
     * @param rig RigProfile 姿态结果
     * @param anim Animation 姿态结果（可能为 null）
     * @return 混合后的最终姿态
     */
    public static Pose blend(
            Pose base,
            RigPoseResult rig,
            AnimationPoseResult anim
    ) {
        if (base == null) {
            base = new Pose();
        }
        if (rig == null) {
            rig = new RigPoseResult(new Pose(), PoseMask.none());
        }

        Pose result = new Pose();

        // 如果没有 Animation，直接使用 Rig
        if (anim == null) {
            result.bodyPitch = rig.pose.bodyPitch;
            result.bodyYaw = rig.pose.bodyYaw;
            result.bodyRoll = rig.pose.bodyRoll;
            result.headPitch = rig.pose.headPitch;
            result.headYaw = rig.pose.headYaw;
            result.leftArmPitch = rig.pose.leftArmPitch;
            result.rightArmPitch = rig.pose.rightArmPitch;
            result.leftLegPitch = rig.pose.leftLegPitch;
            result.rightLegPitch = rig.pose.rightLegPitch;
            return result;
        }

        // 有 Animation 时，按部位混合

        // Body
        result.bodyPitch = anim.mask.body ? anim.pose.bodyPitch : rig.pose.bodyPitch;
        result.bodyYaw = anim.mask.body ? anim.pose.bodyYaw : rig.pose.bodyYaw;
        result.bodyRoll = anim.mask.body ? anim.pose.bodyRoll : rig.pose.bodyRoll;

        // Head
        result.headPitch = anim.mask.head ? anim.pose.headPitch : rig.pose.headPitch;
        result.headYaw = anim.mask.head ? anim.pose.headYaw : rig.pose.headYaw;

        // Arms
        result.leftArmPitch = anim.mask.arms ? anim.pose.leftArmPitch : rig.pose.leftArmPitch;
        result.rightArmPitch = anim.mask.arms ? anim.pose.rightArmPitch : rig.pose.rightArmPitch;

        // Legs
        result.leftLegPitch = anim.mask.legs ? anim.pose.leftLegPitch : rig.pose.leftLegPitch;
        result.rightLegPitch = anim.mask.legs ? anim.pose.rightLegPitch : rig.pose.rightLegPitch;

        return result;
    }
}
