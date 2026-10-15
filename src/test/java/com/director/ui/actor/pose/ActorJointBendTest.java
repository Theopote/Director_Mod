package com.director.ui.actor.pose;
import com.director.actor.rig.skeleton.ArticulatedPlayerRig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorJointBendTest {
    @Test void positiveElbowAndKneeBendUseOppositePitchAndPreserveTwist() {
        float[] rotation={0,25,35};
        ActorJointBend.set(ArticulatedPlayerRig.RIGHT_FOREARM,90,rotation);
        assertArrayEquals(new float[]{-90,25,35},rotation);
        assertEquals(90,ActorJointBend.degrees(ArticulatedPlayerRig.RIGHT_FOREARM,rotation[0]));
        ActorJointBend.set(ArticulatedPlayerRig.LEFT_SHIN,90,rotation);
        assertArrayEquals(new float[]{90,25,35},rotation);
    }
    @Test void straighteningAndBoundsDoNotAffectOtherAxes() {
        float[] rotation={-80,12,30};
        ActorJointBend.set(ArticulatedPlayerRig.LEFT_FOREARM,0,rotation);
        assertArrayEquals(new float[]{0,12,30},rotation);
        ActorJointBend.set(ArticulatedPlayerRig.LEFT_SHIN,200,rotation);
        assertEquals(160,rotation[0]);
        assertThrows(IllegalArgumentException.class,()->ActorJointBend.set(ArticulatedPlayerRig.LEFT_SHIN,Float.NaN,rotation));
        assertFalse(ActorJointBend.isJoint("head"));
    }
}
