package com.director.actor.preview;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.runtime.ActorRuntime;
import org.joml.Vector3f;

/** Pose storage shared by villager, zombie, skeleton, cow, and sheep previews. */
public final class NativeActorPoseCarrier {
    private ActorRuntime runtime;
    private Pose pose;
    private RigPose rigPose;
    private final Vector3f scale = new Vector3f(1);

    public void setActorRuntime(ActorRuntime value) { runtime = value; }
    public ActorRuntime getActorRuntime() { return runtime; }
    public Pose getFinalPose() { return pose; }
    public RigPose getFinalRigPose() { return rigPose == null ? null : rigPose.copy(); }
    public Vector3f getActorTransformScale() { return new Vector3f(scale); }

    public void setActorTransformScale(Vector3f value) {
        if (value != null && value.isFinite()) scale.set(value);
    }

    public void setFinalPose(Pose value) {
        pose = value;
        setFinalRigPose(value == null ? null : LegacyHumanoidPoseAdapter.convert(value));
    }

    public void setFinalRigPose(RigPose value) {
        rigPose = value == null ? null : value.copy();
    }
}
