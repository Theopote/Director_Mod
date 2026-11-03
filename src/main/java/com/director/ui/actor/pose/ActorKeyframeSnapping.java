package com.director.ui.actor.pose;

import java.util.List;

/** Snap one group delta while retaining the spacing between every selected key. */
final class ActorKeyframeSnapping {
    private ActorKeyframeSnapping() {}
    static float delta(float pixels,float pixelsPerSecond,float frameStep,List<Float> keys,List<Float> targets) {
        if(!Float.isFinite(pixels) || !Float.isFinite(pixelsPerSecond) || pixelsPerSecond<=0)return 0;
        float raw=pixels/pixelsPerSecond;
        float snapped=frameStep>0?ActorAnimationDopeSheet.snapDragDelta(pixels,pixelsPerSecond,frameStep):raw;
        float distance=8f/pixelsPerSecond;
        for(float key:keys) for(float target:targets) {
            float candidate=target-key,error=Math.abs(candidate-raw);
            if(Float.isFinite(candidate) && error<distance) { snapped=candidate;distance=error; }
        }
        return snapped;
    }
}
