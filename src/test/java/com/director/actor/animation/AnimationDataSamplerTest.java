package com.director.actor.animation;

import com.director.actor.rig.Pose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnimationDataSamplerTest {
    private static final String ID = "test:absolute-sampler";
    @AfterEach void cleanup() { AnimationLibrary.getInstance().remove(ID); }
    private void register(float end) {
        Pose last = new Pose(); last.headYaw = end;
        AnimationLibrary.getInstance().register(new AnimationData(ID, ID, 1f,
                List.of(new AnimationKeyframe(0f, new Pose()), new AnimationKeyframe(1f, last)),
                AnimationData.AnimationSource.CUSTOM));
    }
    private float yaw(float time, boolean loop, float speed) {
        return AnimationDataSampler.sample(ID, time, loop, speed).pose.headYaw;
    }
    @Test void sameIdUsesCurrentLoopAndSpeedOnEverySeek() {
        register(1f);
        assertEquals(.25f, yaw(1.25f, true, 1f), 1e-5f);
        assertEquals(1f, yaw(1.25f, false, 1f), 1e-5f);
        assertEquals(.5f, yaw(.25f, false, 2f), 1e-5f);
        assertEquals(.25f, yaw(1.25f, true, 1f), 1e-5f);
    }
    @Test void missingOrRemovedIdNeverRetainsPreviousPose() {
        register(1f); assertNotNull(AnimationDataSampler.sample(ID, .5f, false, 1f));
        assertNull(AnimationDataSampler.sample("test:missing", .5f, false, 1f));
        AnimationLibrary.getInstance().remove(ID);
        assertNull(AnimationDataSampler.sample(ID, .5f, false, 1f));
        assertNull(AnimationDataSampler.sample(null, .5f, false, 1f));
    }
    @Test void replacingAssetWithSameIdTakesEffectImmediately() {
        register(1f); assertEquals(.5f, yaw(.5f, false, 1f), 1e-5f);
        register(2f); assertEquals(1f, yaw(.5f, false, 1f), 1e-5f);
    }
    @Test void runtimeEditsCannotMutateBoundaryKeyframes() {
        register(1f);
        AnimationDataSampler.sample(ID, 1f, false, 1f).pose.headYaw = 99f;
        assertEquals(1f, yaw(1f, false, 1f));
        AnimationDataSampler.sample(ID, 0f, false, 1f).pose.headYaw = 99f;
        assertEquals(0f, yaw(0f, false, 1f));
    }
    @Test void invalidInputsAndHugeTimeRemainFinite() {
        register(1f);
        assertEquals(0f, yaw(Float.NaN, true, 1f));
        assertEquals(.5f, yaw(.5f, false, Float.NaN));
        assertEquals(0f, yaw(-1f, false, 1f));
        assertTrue(Float.isFinite(yaw(Float.MAX_VALUE, true, Float.MAX_VALUE)));
    }
}
