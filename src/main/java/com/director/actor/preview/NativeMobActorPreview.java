package com.director.actor.preview;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.runtime.ActorRuntime;
import org.joml.Vector3f;

/** Vanilla-mob preview that still carries the sampled rig pose for its model mixin. */
public interface NativeMobActorPreview extends ActorPosePreview {
    NativeActorPoseCarrier director$pose();

    @Override default void setActorRuntime(ActorRuntime value) { director$pose().setActorRuntime(value); }
    @Override default ActorRuntime getActorRuntime() { return director$pose().getActorRuntime(); }
    @Override default void setFinalPose(Pose value) { director$pose().setFinalPose(value); }
    @Override default Pose getFinalPose() { return director$pose().getFinalPose(); }
    @Override default void setFinalRigPose(RigPose value) { director$pose().setFinalRigPose(value); }
    @Override default RigPose getFinalRigPose() { return director$pose().getFinalRigPose(); }
    @Override default void setActorTransformScale(Vector3f value) { director$pose().setActorTransformScale(value); }
    @Override default Vector3f getActorTransformScale() { return director$pose().getActorTransformScale(); }
}
