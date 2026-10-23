package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorGazeConstraintTest {
    private RigPose pose() {
        var p=new RigPose(ArticulatedPlayerRig.DEFINITION);
        p.setLocalTransform(HumanoidRig.BODY,p.localTransform(HumanoidRig.BODY).withRotation(new Quaternionf().rotateXYZ(.3f,.5f,-.25f)));
        p.setLocalTransform(HumanoidRig.HEAD,p.localTransform(HumanoidRig.HEAD).withRotation(new Quaternionf().rotateXYZ(.2f,-.4f,.1f)));
        return p;
    }
    private RigWorldPose world(RigPose p) {return PlayerRenderWorldPose.resolve(p,new Vector3f(2,3,4),37,new Vector3f(1.2f,.8f,1.5f));}
    @Test void zeroWeightPreservesAuthoredHeadAndHalfWeightSlerpsFromIt() {
        var original=pose();var target=new Vector3f(5,6,9);var initial=original.localTransform(HumanoidRig.HEAD);
        assertFalse(ActorGazeConstraint.apply(original,world(original),target,true,0));
        assertTrue(initial.toMatrix().equals(original.localTransform(HumanoidRig.HEAD).toMatrix(),1e-6f));
        var full=original.copy();assertTrue(ActorGazeConstraint.apply(full,world(full),target,true,1));
        var half=original.copy();assertTrue(ActorGazeConstraint.apply(half,world(half),target,true,.5f));
        assertTrue(initial.rotation().slerp(full.localTransform(HumanoidRig.HEAD).rotation(),.5f).equals(half.localTransform(HumanoidRig.HEAD).rotation(),1e-6f));
        assertEquals(initial.translation(),half.localTransform(HumanoidRig.HEAD).translation());
        assertEquals(initial.scale(),half.localTransform(HumanoidRig.HEAD).scale());
    }
    @Test void rotatedScaledPlayerParentsStillAimAtWorldTarget() {
        var p=pose();var target=new Vector3f(-3,5,8);var oldWorld=world(p);var head=p.localTransform(HumanoidRig.HEAD);
        var basis=oldWorld.transform(p.rig().requireBoneIndex(HumanoidRig.HEAD)).mul(new Matrix4f().rotation(head.rotation()).scale(head.scale()).invert());
        var eye=basis.transformPosition(new Vector3f(0,-.25f,0));
        var body=p.localTransform(HumanoidRig.BODY).toMatrix();
        assertTrue(ActorGazeConstraint.apply(p,oldWorld,target,true,1));
        var forward=world(p).transform(p.rig().requireBoneIndex(HumanoidRig.HEAD)).transformDirection(new Vector3f(0,0,-1)).normalize();
        assertTrue(forward.dot(new Vector3f(target).sub(eye).normalize())>.9999f);
        assertTrue(body.equals(p.localTransform(HumanoidRig.BODY).toMatrix(),1e-6f));
    }
    @Test void genericRigUsesItsOwnParentSpaceAndInvalidInputsAreNoOps() {
        var p=new RigPose(HumanoidRig.DEFINITION);var actor=new Matrix4f().rotateY(.4f).scale(1,.8f,1.3f);
        p.setLocalTransform(HumanoidRig.BODY,p.localTransform(HumanoidRig.BODY).withRotation(new Quaternionf().rotateXYZ(.2f,.3f,-.4f)));
        var w=RigWorldPose.resolve(p,actor);var target=new Vector3f(2,3,4);var eye=w.transform(2).transformPosition(new Vector3f(0,.125f,0));
        assertTrue(ActorGazeConstraint.apply(p,w,target,false,1));
        var forward=RigWorldPose.resolve(p,actor).transform(2).transformDirection(new Vector3f(0,0,1)).normalize();
        assertTrue(forward.dot(target.sub(eye).normalize())>.9999f);
        var before=p.localTransform(2).toMatrix();
        assertFalse(ActorGazeConstraint.apply(p,w,new Vector3f(Float.NaN),false,1));
        assertFalse(ActorGazeConstraint.apply(p,w,new Vector3f(),false,Float.NaN));
        assertTrue(before.equals(p.localTransform(2).toMatrix(),1e-6f));
    }
}
