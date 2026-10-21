package com.director.timeline.playback.entity;

import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorMotionSamplingTest {
    private ActorTrack movingTrack() {
        var track = new ActorTrack("actor");
        var transform = new EntityTransformClip("move", 2f, 12f);
        transform.setFromPos(0f, 0f, 0f);
        transform.setToPos(10f, 0f, 0f);
        transform.setInterpolation(TimelineInterpolation.LINEAR);
        track.addClip(transform);
        return track;
    }

    @Test void motionDoesNotDependOnSamplingHistory() {
        var sampler = new EntityClipSampler(movingTrack());
        var direct = sampler.sampleAt(5f);
        assertEquals(1f, direct.speed, 1e-5f);
        assertEquals(3f, direct.motionLocalTime, 1e-5f);
        for (float t : new float[]{5f, 11f, 3f, 5f}) {
            sampler.sampleAt(t);
            var repeated = sampler.sampleAt(5f);
            assertEquals(direct.speed, repeated.speed);
            assertEquals(direct.action, repeated.action);
            assertEquals(direct.motionLocalTime, repeated.motionLocalTime);
        }
        sampler.clearAllCaches();
        assertEquals(direct.speed, sampler.sampleAt(5f).speed);
    }

    @Test void explicitIdleWinsUntilMutedOrOutsideItsInterval() {
        var track = movingTrack();
        var idle = new ActorActionClip("hold pose", 4f, 6f);
        idle.setActionType(ActorActionType.IDLE);
        track.addClip(idle);
        var sampler = new EntityClipSampler(track);
        var sample = sampler.sampleAt(5f);
        assertTrue(sample.actionExplicit);
        assertEquals(ActorActionType.IDLE, sample.action);
        assertEquals(1f, sample.actionLocalTime);
        assertTrue(sample.copy().actionExplicit);
        assertEquals(1f, sample.copy().actionLocalTime);
        assertFalse(sampler.sampleAt(7f).actionExplicit);
        // 10 blocks / 10s = 1.0 units/s → WALK (RUN_THRESHOLD is 4.0)
        assertEquals(ActorActionType.WALK, sampler.sampleAt(7f).action);
        idle.setMuted(true);
        assertEquals(ActorActionType.WALK, sampler.sampleAt(5f).action);
    }

    @Test void smoothSpeedUsesCurveDerivativeAndStepDoesNotInventLocomotion() {
        var track = movingTrack();
        var transform = (EntityTransformClip) track.getClips().getFirst();
        transform.setInterpolation(TimelineInterpolation.SMOOTH);
        var sampler = new EntityClipSampler(track);
        assertEquals(0f, sampler.sampleAt(2f).speed);
        assertEquals(1.5f, sampler.sampleAt(7f).speed, 1e-5f);
        assertEquals(0f, sampler.sampleAt(12f).speed);
        transform.setInterpolation(TimelineInterpolation.STEP);
        assertEquals(0f, sampler.sampleAt(7f).speed);
        assertEquals(0f, sampler.sampleAt(7f).position.x);
        assertEquals(10f, sampler.sampleAt(12f).position.x);
    }

    @Test void keyframeSegmentsOverrideClipEndpointsAndHoldAtStepBoundaries() {
        var track = movingTrack();
        var transform = (EntityTransformClip) track.getClips().getFirst();
        var first = new EntityClipKeyframe(3f, TimelineInterpolation.LINEAR);
        var second = new EntityClipKeyframe(5f, TimelineInterpolation.LINEAR);
        second.setPosition(4f, 0f, 0f);
        var third = new EntityClipKeyframe(7f, TimelineInterpolation.STEP);
        third.setPosition(100f, 0f, 0f);
        transform.addKeyframe(first);
        transform.addKeyframe(second);
        transform.addKeyframe(third);
        var sampler = new EntityClipSampler(track);
        assertEquals(0f, sampler.sampleAt(2f).speed);
        assertEquals(2f, sampler.sampleAt(4f).speed);
        assertEquals(4f, sampler.sampleAt(6f).position.x);
        assertEquals(0f, sampler.sampleAt(6f).speed);
        assertEquals(100f, sampler.sampleAt(7f).position.x);
        assertEquals(0f, sampler.sampleAt(8f).speed);
    }

    @Test void transformOverridesRouteSpeedAndMutedTransformRestoresRoute() {
        var track = movingTrack();
        var transform = (EntityTransformClip) track.getClips().getFirst();
        var route = new ActorRouteClip("route", 0f, 20f);
        route.addWaypoint(0f, 0f, 0f);
        route.addWaypoint(0f, 0f, 100f);
        route.setSpeed(5f);
        track.addClip(route);
        var sampler = new EntityClipSampler(track);
        assertEquals(1f, sampler.sampleAt(5f).speed);
        transform.setMuted(true);
        assertEquals(5f, sampler.sampleAt(5f).speed);
    }

    @Test void loopingRouteHasNoTeleportSpeedAndPingPongFacesTravelDirection() {
        var track = new ActorTrack("route");
        var route = new ActorRouteClip("route", 0f, 20f);
        route.addWaypoint(0f, 0f, 0f);
        route.addWaypoint(0f, 0f, 4f);
        route.setSpeed(2f);
        route.setLoopMode(ActorRouteClip.LoopMode.LOOP);
        track.addClip(route);
        var sampler = new EntityClipSampler(track);
        sampler.sampleAt(1.99f);
        assertEquals(2f, sampler.sampleAt(2f).speed);
        route.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);
        assertEquals(180f, Math.abs(sampler.sampleAt(3f).rotation.x), 1e-5f);
        // ONCE fits the path into the full clip length (4 / 20 = 0.2), not constant route speed.
        route.setLoopMode(ActorRouteClip.LoopMode.ONCE);
        assertEquals(0.2f, sampler.sampleAt(3f).speed, 1e-5f);
        assertEquals(0f, sampler.sampleAt(20f).speed);
    }
}
