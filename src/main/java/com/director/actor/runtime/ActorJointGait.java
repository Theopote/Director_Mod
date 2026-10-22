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
        applyPhase(pose,action,intensity,time*com.director.actor.rig.ActorRigProfile.locomotionFrequency(2,action,intensity,style),style,arms,legs);
    }
    public static void apply(RigPose pose,ActorActionType action,float intensity,float time,
                             com.director.actor.rig.ActorRigProfile profile,boolean arms,boolean legs) {
        apply(pose,action,intensity,time,profile,arms,legs,1);
    }
    public static void apply(RigPose pose,ActorActionType action,float intensity,float time,
            com.director.actor.rig.ActorRigProfile profile,boolean arms,boolean legs,float motionWeight) {
        applyPhase(pose,action,intensity*motionWeight,time*profile.locomotionFrequency(action,intensity),profile.getStyle(),arms,legs);
    }
    private static void applyPhase(RigPose pose,ActorActionType action,float intensity,float phase,
                                   RigProfileStyle style,boolean arms,boolean legs) {
        if (pose == null || style == null || !Float.isFinite(phase) || !Float.isFinite(intensity)
                || (action != ActorActionType.WALK && action != ActorActionType.RUN)) return;
        float strength=Math.max(0,Math.min(1,intensity*style.motionScale));
        boolean running=action==ActorActionType.RUN;
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
