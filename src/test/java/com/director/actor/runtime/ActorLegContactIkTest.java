package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorLegContactIkTest {
    @Test void reachableTargetsImproveWithoutMovingHipOrChangingScale() {
        for(var mode:LimbBendMode.values()) {
            var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);pose.setLimbBendMode(mode);
            var hip=pose.localTransform(HumanoidRig.LEFT_LEG);var knee=pose.localTransform(ArticulatedPlayerRig.LEFT_SHIN);
            var target=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(0,.09f,.12f);
            float before=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).distance(target);
            ActorLegContactIk.solve(pose,HumanoidRig.LEFT_LEG,target);
            assertTrue(ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).distance(target)<before*.5f, () -> mode+" before="+before+" after="+ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).distance(target));
            assertEquals(hip.translation(),pose.localTransform(HumanoidRig.LEFT_LEG).translation());
            assertEquals(knee.translation(),pose.localTransform(ArticulatedPlayerRig.LEFT_SHIN).translation());
            assertEquals(hip.scale(),pose.localTransform(HumanoidRig.LEFT_LEG).scale());
            assertFalse(pose.isAuthored(pose.rig().requireBoneIndex(HumanoidRig.RIGHT_LEG)));
        }
    }
    @Test void unreachableAndInvalidTargetsStayFiniteAndDoNotStretchTheLeg() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var hip=pose.localTransform(HumanoidRig.RIGHT_LEG);
        ActorLegContactIk.solve(pose,HumanoidRig.RIGHT_LEG,new Vector3f(100,-100,100));
        assertTrue(ActorRouteFootContact.solePosition(pose,HumanoidRig.RIGHT_LEG).isFinite());
        assertEquals(hip.translation(),pose.localTransform(HumanoidRig.RIGHT_LEG).translation());
        assertEquals(hip.scale(),pose.localTransform(HumanoidRig.RIGHT_LEG).scale());
        var before=pose.localTransform(HumanoidRig.RIGHT_LEG).toMatrix();
        ActorLegContactIk.solve(pose,HumanoidRig.RIGHT_LEG,new Vector3f(Float.NaN));
        assertTrue(before.equals(pose.localTransform(HumanoidRig.RIGHT_LEG).toMatrix(),1e-6f));
    }
    @Test void noCorrectionDoesNotAuthorChannels() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        ActorLegContactIk.solve(pose,HumanoidRig.LEFT_LEG,ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG));
        assertTrue(pose.authoredBones().isEmpty());
    }
    @Test void negativeKneesRemainOnTheirAuthoredSideAtPartialWeights() {
        for(var mode:LimbBendMode.values()) for(float angle:new float[]{-.2f,-1.2f,-2.5f}) {
            var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);pose.setLimbBendMode(mode);
            String knee=ArticulatedPlayerRig.LEFT_SHIN;
            pose.setLocalTransform(knee,pose.localTransform(knee).withRotation(LimbBendTransform.encodedRotation(angle,.4f)));
            var target=ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).add(.05f,-.04f,.1f);
            assertDoesNotThrow(()->ActorLegContactIk.solve(pose,HumanoidRig.LEFT_LEG,target,.6f));
            assertTrue(LimbBendTransform.angles(pose.localTransform(knee).rotation()).x<=1e-5f);
            assertTrue(ActorRouteFootContact.solePosition(pose,HumanoidRig.LEFT_LEG).isFinite());
        }
    }
}
