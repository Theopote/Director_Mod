package com.director.actor.runtime;

import com.director.actor.prop.*;
import com.director.actor.rig.skeleton.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorWorldPoseEvaluatorTest {
    private RigPose pose() {
        var p=new RigPose(ArticulatedPlayerRig.DEFINITION);
        p.setLocalTransform(HumanoidRig.BODY,p.localTransform(HumanoidRig.BODY).withRotation(new Quaternionf().rotateXYZ(.2f,.4f,-.1f)));
        p.setLocalTransform(ArticulatedPlayerRig.RIGHT_FOREARM,p.localTransform(ArticulatedPlayerRig.RIGHT_FOREARM).withRotation(LimbBendTransform.encodedRotation(-.8f,.3f)));
        return p;
    }
    @Test void gazeFeedbackDistinguishesSuccessFromMissingTargetZeroWeightAndDegenerateScale() {
        var p=pose();var position=new Vector3f();var scale=new Vector3f(1);var target=new Vector3f(3,4,5);
        assertTrue(ActorWorldPoseEvaluator.evaluate(p,position,0,scale,true,target,.5f).gazeApplied());
        assertFalse(ActorWorldPoseEvaluator.evaluate(p,position,0,scale,true,null,1).gazeApplied());
        assertFalse(ActorWorldPoseEvaluator.evaluate(p,position,0,scale,true,target,0).gazeApplied());
        assertFalse(ActorWorldPoseEvaluator.evaluate(p,position,0,new Vector3f(0),true,target,1).gazeApplied());
        var world=ActorWorldPoseEvaluator.resolve(p,position,0,scale,true);
        var eye=ActorWorldPoseEvaluator.eyePosition(p,world,true);
        assertFalse(ActorWorldPoseEvaluator.evaluate(p,position,0,scale,true,eye,1).gazeApplied());
    }

    @Test void playerSocketsUseRenderedPivotsAndCorrectModelYAxis() {
        var p=pose();var position=new Vector3f(3,4,5);var scale=new Vector3f(1.2f,.8f,1.5f);
        var world=ActorWorldPoseEvaluator.resolve(p,position,37,scale,true);
        var reference=PlayerRenderWorldPose.resolve(p,position,37,scale);
        for(int i=0;i<p.rig().boneCount();i++) assertTrue(world.transform(i).equals(reference.transform(i),1e-6f));
        var hand=ActorPropSocketResolver.resolve(world,true,ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        var expected=reference.transform(p.rig().requireBoneIndex(ArticulatedPlayerRig.RIGHT_FOREARM)).translate(0,.375f,0).scale(1,-1,-1);
        assertTrue(expected.equals(hand,1e-6f));
        var head=ActorPropSocketResolver.resolve(world,true,ActorPropSlot.HEAD,new Vector3f(),new Vector3f()).orElseThrow();
        assertTrue(reference.transform(p.rig().requireBoneIndex(HumanoidRig.HEAD)).translate(0,-.25f,0).scale(1,-1,-1).equals(head,1e-6f));
    }
    @Test void liveAndHistoricalWorldInputsProduceIdenticalGazedSocketsWithoutMutatingPose() {
        var p=pose();var before=p.localTransform(HumanoidRig.HEAD).toMatrix();
        var position=new Vector3f(2,3,4);var scale=new Vector3f(1.1f,.7f,1.4f);var target=new Vector3f(-3,5,9);
        var live=ActorWorldPoseEvaluator.evaluate(p,position,25,scale,true,target,.65f);
        ActorWorldPoseEvaluator.evaluate(p,new Vector3f(),80,new Vector3f(1),true,new Vector3f(8),1);
        var historical=ActorWorldPoseEvaluator.evaluate(p.copy(),new Vector3f(position),25,new Vector3f(scale),true,new Vector3f(target),.65f);
        assertTrue(before.equals(p.localTransform(HumanoidRig.HEAD).toMatrix(),1e-6f));
        for(var slot:ActorPropSlot.values()) {
            var offset=new Vector3f(.1f,.2f,-.1f);var rotation=new Vector3f(10,20,30);
            var a=ActorPropSocketResolver.resolve(live.world(),true,slot,offset,rotation).orElseThrow();
            var b=ActorPropSocketResolver.resolve(historical.world(),true,slot,offset,rotation).orElseThrow();
            assertTrue(a.equals(b,1e-6f));
            var prop=new RuntimeProp(PropRegistry.getInstance().get("stone").orElseThrow(),slot);
            prop.setLocalOffset(offset.x,offset.y,offset.z);prop.setLocalRotation(rotation.x,rotation.y,rotation.z);
            prop.updateSocketTransform(live.world(),true);assertTrue(a.equals(prop.getWorldTransform(),1e-6f));
        }
    }
    @Test void genericMappingAndPropOffsetsKeepExistingContract() {
        var p=new RigPose(HumanoidRig.DEFINITION);var position=new Vector3f(2,3,4);var scale=new Vector3f(2,1,3);
        var matrix=new Matrix4f().translate(position).rotateY((float)java.lang.Math.toRadians(-30)).scale(scale);
        var world=ActorWorldPoseEvaluator.resolve(p,position,30,scale,false);
        for(var slot:ActorPropSlot.values()) {
            var old=ActorPropSocketResolver.resolve(p,matrix,slot,new Vector3f(.2f),new Vector3f(20)).orElseThrow();
            var shared=ActorPropSocketResolver.resolve(world,false,slot,new Vector3f(.2f),new Vector3f(20)).orElseThrow();
            assertTrue(old.equals(shared,1e-6f));
        }
    }
    @Test void missingHistoricalTargetPreservesAuthoredHeadInsteadOfUsingCurrentTarget() {
        var p=pose();p.setLocalTransform(HumanoidRig.HEAD,p.localTransform(HumanoidRig.HEAD).withRotation(new Quaternionf().rotateY(.6f)));
        var result=ActorWorldPoseEvaluator.evaluate(p,new Vector3f(),0,new Vector3f(1),true,null,1);
        assertTrue(p.localTransform(HumanoidRig.HEAD).toMatrix().equals(result.pose().localTransform(HumanoidRig.HEAD).toMatrix(),1e-6f));
    }
}
