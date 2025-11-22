package com.chronoblocks.animation.timeline;

import com.chronoblocks.animation.core.Track;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间线专用的可见性轨道
 * 
 * @author ChronoBlocks Team
 * @version 5.3
 */
public class ContainerVisibilityTrack extends Track<Boolean> {

    private final List<VisibilityKeyframe> visibilityKeyframes = new ArrayList<>();

    public ContainerVisibilityTrack() {
        super("visibility");
    }

    /**
     * 取得当前时间是否应该可见
     */
    public boolean evaluate(float t) {
        if (visibilityKeyframes.isEmpty()) {
            return true;
        }

        VisibilityKeyframe last = visibilityKeyframes.getFirst();

        for (VisibilityKeyframe k : visibilityKeyframes) {
            if (k.time > t) {
                break;
            }
            last = k;
        }
        return last.visible;
    }

    @Override
    public Boolean evaluate(double timeSec) {
        return evaluate((float) timeSec);
    }

    /**
     * 转换为 EvaluatedPose 格式（用于 PlayableGraph）
     */
    public com.chronoblocks.animation.core.EvaluatedPose evaluatePose(double timeSec) {
        boolean visible = evaluate((float) timeSec);
        // EvaluatedPose 使用 double visibility，1.0 表示可见，0.0 表示不可见
        return new com.chronoblocks.animation.core.EvaluatedPose(
            null, null, null, visible ? 1.0 : 0.0
        );
    }
}

