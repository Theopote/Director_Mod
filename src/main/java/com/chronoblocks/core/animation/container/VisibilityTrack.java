package com.chronoblocks.core.animation.container;

/**
 * VisibilityTrack - 控制可见性的轨道（0.0 - 1.0）。
 */
public class VisibilityTrack extends Track<Double> {

    public VisibilityTrack() {
        super("visibility.alpha");
    }

    @Override
    public Double evaluate(double timeSec) {
        if (keyframes.isEmpty()) {
            return 1.0;
        }
        if (keyframes.size() == 1) {
            return keyframes.getFirst().value();
        }

        Keyframe<Double> previous = keyframes.getFirst();
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe<Double> next = keyframes.get(i);
            if (timeSec <= next.time()) {
                double duration = next.time() - previous.time();
                double alpha = duration <= 0 ? 1.0 : (timeSec - previous.time()) / duration;
                alpha = Math.max(0.0, Math.min(1.0, alpha));
                return previous.value() + (next.value() - previous.value()) * alpha;
            }
            previous = next;
        }
        return keyframes.getLast().value();
    }

    public EvaluatedPose evaluatePose(double timeSec) {
        return new EvaluatedPose(null, null, null, evaluate(timeSec));
    }
}

