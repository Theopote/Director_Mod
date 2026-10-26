package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.PoseScope;
import com.director.timeline.playback.entity.EntitySample;

/** Route timing risk, not a claim that visible sliding must occur. */
public enum ActorPresetRouteFeedback {
    NONE, DISTANCE_CLOCK, LOCAL_CLOCK;
    public static ActorPresetRouteFeedback resolve(ActorPresetFeedback feedback,EntitySample sample,boolean muted) {
        if(muted || sample==null || !sample.enabled || !sample.active || sample.visibility<=.01f || sample.routeClip==null
                || !feedback.available() || feedback.weight()<=0 || feedback.rotationBones().stream().noneMatch(PoseScope::isLeg)) return NONE;
        return feedback.distanceClock()?DISTANCE_CLOCK:LOCAL_CLOCK;
    }
}
