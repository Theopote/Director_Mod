package com.chronoblocks.animation.timeline;

import com.chronoblocks.animation.core.AnimationClip;
import com.chronoblocks.animation.core.EvaluatedPose;
import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.PlayableGraph;
import com.chronoblocks.animation.core.Track;

import java.util.Objects;
import java.util.UUID;

/**
 * TimelineController - 管理动画片段与求值。
 */
public class TimelineController {

    private final PlayableGraph playableGraph = new PlayableGraph();
    private double currentTimeSec = 0.0;

    public AnimationClip createClipFor(UUID containerId, String clipName) {
        Objects.requireNonNull(containerId, "containerId");
        String finalName = clipName != null ? clipName : "Clip";
        AnimationClip clip = new AnimationClip(finalName, currentTimeSec, currentTimeSec + 5.0, containerId);
        playableGraph.addClip(clip);
        return clip;
    }

    public <T> void addKeyframe(AnimationClip clip, Track<T> track, double timeSec, T value) {
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(track, "track");
        track.addKeyframe(new Keyframe<>(timeSec, value));
        if (!clip.getTracks().contains(track)) {
            clip.addTrack(track);
        }
    }

    public void setCurrentTime(double timeSec) {
        this.currentTimeSec = Math.max(0.0, timeSec);
    }

    public double getCurrentTime() {
        return currentTimeSec;
    }

    public EvaluatedPose evaluate(UUID containerId) {
        return playableGraph.evaluate(currentTimeSec, containerId);
    }

    public PlayableGraph getPlayableGraph() {
        return playableGraph;
    }
}


