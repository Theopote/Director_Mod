package com.chronoblocks.animation.timeline;

import com.chronoblocks.animation.core.EvaluatedPose;
import com.chronoblocks.animation.core.PlayableGraph;

import java.util.UUID;

/**
 * TimelineController - 管理动画片段与求值。
 */
public class TimelineController {

    private final PlayableGraph playableGraph = new PlayableGraph();
    private double currentTimeSec = 0.0;

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


