package com.director.actor.rig;

import com.director.actor.action.ActorActionType;
import com.director.actor.runtime.ActorRuntime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralPoseSamplingTest {
    @Test void pauseAndRewindDoNotAdvancePhaseOrRetainCarryState() {
        for (ActorRigProfile profile : new ActorRigProfile[]{new HumanoidRigProfile(), new QuadrupedRigProfile()}) {
            var runtime = new ActorRuntime();
            var expected = profile.sampleAt(runtime, ActorActionType.WALK, 0.7f, 3f).pose;
            profile.sampleAt(runtime, ActorActionType.CARRY, 1f, 10f);
            for (float ignored : new float[]{1f, 2f, 3f}) {
                var actual = profile.sampleAt(runtime, ActorActionType.WALK, 0.7f, 3f).pose;
                assertEquals(expected.leftLegPitch, actual.leftLegPitch);
                assertEquals(expected.headPitch, actual.headPitch);
                assertEquals(3f, runtime.getTime());
                assertFalse(runtime.isArmsLockedForward());
            }
        }
    }
}
