package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.core.container.AnimationMode;

/**
 * 把“旧的 AnimationMode（本质是排序策略）”迁移到新版 SequenceParameters。
 *
 * <p>目标：
 * - 存档/预设依旧可以读出旧 mode
 * - 运行时和 UI 以 SequenceParameters 为准（排序策略），AnimationMode 只负责“单块变换”
 */
public final class LegacyAnimationModeMigrator {

    private static final String KEY_DONE = "_legacyModeMigratedToSequenceV1";
    private static final String KEY_LEGACY = "_legacyOrderingMode";

    private LegacyAnimationModeMigrator() {
    }

    public static void migrateIfNeeded(ClipAnimationConfig config) {
        if (config == null) return;
        if (Boolean.TRUE.equals(config.getParams().get(KEY_DONE))) {
            return;
        }

        AnimationMode mode = config.getMode();
        if (mode == null) {
            return;
        }

        SequenceParameters seq = config.getSequenceParameters();
        if (seq == null) {
            seq = new SequenceParameters();
            config.setSequenceParameters(seq);
        }

        boolean migrated = false;

        switch (mode) {
            // ====== 纯排序策略（旧模式）======
            case RANDOM -> {
                seq.setFlowType(FlowType.RANDOM);
                migrated = true;
            }
            case SPIRAL -> {
                seq.setFlowType(FlowType.PLANAR);
                seq.setOriginType(OriginType.CENTER);
                seq.setFillType(FillType.SPIRAL);
                migrated = true;
            }
            case WAVE -> {
                seq.setFlowType(FlowType.PLANAR);
                seq.setOriginType(OriginType.BOTTOM_CENTER);
                seq.setFillType(FillType.LINEAR_SCAN);
                seq.setAlternating(true);
                migrated = true;
            }
            case SLICE -> {
                seq.setFlowType(FlowType.PLANAR);
                seq.setOriginType(OriginType.BOTTOM_CENTER);
                seq.setFillType(FillType.LINEAR_SCAN);
                migrated = true;
            }
            case GROW_DOWN -> {
                // “向下生长”在旧系统里主要表达“从上到下的层级顺序”
                // 新系统没有 TOP_* origin，因此用 reverse 达到同等效果（以 BOTTOM_CENTER 为基准反转）
                seq.setFlowType(FlowType.PLANAR);
                seq.setOriginType(OriginType.BOTTOM_CENTER);
                seq.setFillType(FillType.LINEAR_SCAN);
                seq.setReverse(true);
                migrated = true;
            }
            case EXPAND -> {
                // “扩散”优先表达“从中心向外”
                seq.setFlowType(FlowType.SPHERICAL);
                seq.setOriginType(OriginType.CENTER);
                seq.setFillType(FillType.RADIAL);
                migrated = true;
            }
            default -> {
                // 其余 mode：认为是“单块变换”（不迁移）
            }
        }

        if (migrated) {
            // 迁移后：把 mode 降级为 INSTANT（不再把“排序策略”放在 mode 里）
            // 注意：这是对 UI/运行时更清晰的“新语义”，旧存档仍可读（并会被迁移一次）。
            config.setParam(KEY_LEGACY, mode.name());
            config.setMode(AnimationMode.INSTANT);
            config.setParam(KEY_DONE, true);
        }
    }
}


