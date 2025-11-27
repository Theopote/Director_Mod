package com.chronoblocks.core.animation.sequence;

import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 SequenceParameters 转换为人类可读的「序列行为总结」。
 */
public final class SequenceSummaryBuilder {

    private SequenceSummaryBuilder() {
    }

    public static String buildSequenceSummary(SequenceParameters params) {
        if (params == null) {
            return "";
        }

        List<String> segments = new ArrayList<>();

        FlowType flow = params.getFlowType();
        if (flow != null) {
            String text = "流向：" + flow.getDisplayName();
            if (flow == FlowType.PLANAR) {
                text += String.format(" (Yaw %.0f°, Pitch %.0f°)",
                    params.getPlaneYaw(), params.getPlanePitch());
            }
            segments.add(text);
        }

        OriginType origin = params.getOriginType();
        if (origin != null) {
            if (origin == OriginType.CUSTOM) {
                segments.add(String.format("起点：自定义 (%d,%d,%d)",
                    params.getCustomX(), params.getCustomY(), params.getCustomZ()));
            } else {
                segments.add("起点：" + origin.getDisplayName());
            }
        }

        FillType fill = params.getFillType();
        if (fill != null) {
            segments.add("填充：" + fill.getDisplayName());
        }

        segments.add(String.format("层级分离度：%.0f%%", params.getPriorityWeight()));

        if (params.isReverse()) {
            segments.add("方向反转");
        }
        if (params.isAlternating()) {
            segments.add("层间往复");
        }

        return String.join(" · ", segments);
    }
}

