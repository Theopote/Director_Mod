package com.director.actor.preview;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.runtime.ActorRuntime;
import org.joml.Vector3f;

/** Pose bridge shared by client-only player and custom armor-stand previews. */
public interface ActorPosePreview {
    double getX();
    double getY();
    double getZ();
    float getYaw();
    float getPitch();
    void refreshPositionAndAngles(double x,double y,double z,float yaw,float pitch);
    void setActorRuntime(ActorRuntime runtime);
    ActorRuntime getActorRuntime();
    void setFinalPose(Pose pose);
    Pose getFinalPose();
    void setFinalRigPose(RigPose pose);
    RigPose getFinalRigPose();
    void setActorTransformScale(Vector3f scale);
    Vector3f getActorTransformScale();
}
