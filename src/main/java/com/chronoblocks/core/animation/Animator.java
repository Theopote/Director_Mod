package com.chronoblocks.core.animation;

import com.chronoblocks.core.timeline.Timeline;

/**
 * Animator - Evaluates a Timeline and applies results to containers.
 */
public class Animator {
    private final Timeline timeline;

    public Animator(Timeline timeline) {
        this.timeline = timeline;
    }

    public void evaluate(double timeSeconds) {
        // TODO: Sample all tracks/curves at timeSeconds and dispatch to containers.
    }
}
