package com.chronoblocks.animation.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PlayableGraph - 简易动画求值器。
 * 根据时间 t 和容器 ID 计算聚合后的 EvaluatedPose。
 */
public class PlayableGraph {

    private final List<AnimationClip> clips = new ArrayList<>();

    public List<AnimationClip> getClips() {
        return clips;
    }

    public EvaluatedPose evaluate(double timeSec, UUID containerId) {
        EvaluatedPose result = EvaluatedPose.empty();
        for (AnimationClip clip : clips) {
            if (!Objects.equals(clip.getBoundContainerId(), containerId)) {
                continue;
            }
            if (timeSec < clip.getStart() || timeSec > clip.getEnd()) {
                continue;
            }
            for (Track<?> track : clip.getTracks()) {
                if (track instanceof TransformTrack transformTrack) {
                    result = result.merge(transformTrack.evaluatePose(timeSec));
                } else if (track instanceof RotationTrack rotationTrack) {
                    result = result.merge(rotationTrack.evaluatePose(timeSec));
                } else if (track instanceof VisibilityTrack visibilityTrack) {
                    result = result.merge(visibilityTrack.evaluatePose(timeSec));
                } else if (track instanceof com.chronoblocks.animation.timeline.ContainerVisibilityTrack containerVisibilityTrack) {
                    result = result.merge(containerVisibilityTrack.evaluatePose(timeSec));
                }
            }
        }
        return result;
    }
}


