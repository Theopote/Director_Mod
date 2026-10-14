package com.director.actor.runtime;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.RigProfileStyle;
import com.director.actor.rig.skeleton.*;
import org.joml.Quaternionf;

/** Timeline-derived elbow/knee motion; compose authored channels afterwards. */
public final class ActorJointGait {
    private ActorJointGait() {}
    public static void apply(RigPose pose, ActorActionType action, float intensity, float time,
                             RigProfileStyle style, boolean arms, boolean legs) {
        if (pose == null || style == null || !Float.isFinite(time) || !Float.isFinite(intensity)
                || (action != ActorActionType.WALK && action != ActorActionType.RUN)) return;
        float strength=Math.max(0,Math.min(1,intensity*style.motionScale));
        boolean running=action==ActorActionType.RUN;
        float phase=time*(2+intensity)*style.tempo*(running?2:1);
        if(legs) {
            rotate(pose,ArticulatedPlayerRig.RIGHT_SHIN,(running?1.1f:.45f)*strength*Math.max(0,-(float)Math.sin(phase)));
            rotate(pose,ArticulatedPlayerRig.LEFT_SHIN,(running?1.1f:.45f)*strength*Math.max(0,(float)Math.sin(phase)));
        }
        if(arms) {
            float bend=-(running?.9f:.18f)*strength;
            rotate(pose,ArticulatedPlayerRig.LEFT_FOREARM,bend);
            rotate(pose,ArticulatedPlayerRig.RIGHT_FOREARM,bend);
        }
    }
    private static void rotate(RigPose pose,String id,float pitch) {
        int index=pose.rig().findBoneIndex(id);
        if(index<0 || Math.abs(pitch)<1e-6f) return;
        pose.setLocalTransform(index,pose.localTransform(index).withRotation(new Quaternionf().rotateX(pitch)),RigPose.ROTATION);
    }
}
