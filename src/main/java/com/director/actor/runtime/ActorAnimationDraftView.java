package com.director.actor.runtime;

import com.director.actor.rig.skeleton.PoseBlendSettings;
import com.director.actor.rig.skeleton.RigDefinition;
import com.director.actor.rig.skeleton.RigPose;

/** Runtime-facing view of an editor animation/bone draft without UI types. */
public interface ActorAnimationDraftView {
    RigDefinition rig();
    RigPose authoredPose();
    PoseBlendSettings settings();
    float envelope();
    default String animationId() { return null; }
}
