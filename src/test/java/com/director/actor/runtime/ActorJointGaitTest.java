package com.director.actor.runtime;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.RigProfileStyle;
import com.director.actor.rig.skeleton.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorJointGaitTest {
    @Test void deterministicSwingAndManualKeysHavePriority() {
        var a=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var b=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var style=new RigProfileStyle();
        ActorJointGait.apply(a,ActorActionType.WALK,1,.5f,style,true,true);
        ActorJointGait.apply(b,ActorActionType.WALK,1,.5f,style,true,true);
        int shin=a.rig().requireBoneIndex(ArticulatedPlayerRig.LEFT_SHIN);
        assertTrue(a.localTransform(shin).rotation().equals(b.localTransform(shin).rotation(),1e-6f));
        assertTrue(a.isAuthored(shin));
        assertFalse(a.isAuthored(a.rig().requireBoneIndex(ArticulatedPlayerRig.RIGHT_SHIN)));
        var keys=new RigPose(a.rig());
        var manual=new Quaternionf().rotateX(.8f);
        keys.setLocalTransform(shin,keys.localTransform(shin).withRotation(manual),RigPose.ROTATION);
        var result=ActorRigPoseComposition.compose(a,keys,p->{});
        assertTrue(result.localTransform(shin).rotation().equals(manual,1e-6f));
    }
    @Test void idleAndRegionOverridesLeaveJointsUncontrolled() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        ActorJointGait.apply(pose,ActorActionType.IDLE,1,1,new RigProfileStyle(),true,true);
        assertTrue(pose.authoredBones().isEmpty());
        ActorJointGait.apply(pose,ActorActionType.RUN,1,1,new RigProfileStyle(),false,false);
        assertTrue(pose.authoredBones().isEmpty());
    }
}
