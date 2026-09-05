package com.director.ui.timeline.render;

import com.director.actor.animation.asset.AnimationLoopMode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorKeyframeProjectionTest {
    @Test void onceUsesClipStartSpeedAndVisibleBounds() {
        assertEquals(List.of(11f), ActorKeyframeProjection.times(2, 4, AnimationLoopMode.ONCE, 10, 20, 2, 0, 30));
        assertEquals(List.of(), ActorKeyframeProjection.times(2, 4, AnimationLoopMode.ONCE, 10, 20, 2, 12, 30));
        assertEquals(List.of(), ActorKeyframeProjection.times(4, 4, AnimationLoopMode.ONCE, 10, 11, 2, 0, 30));
    }
    @Test void loopRepeatsOnlyWithinVisibleClipAndWrapsEndpoint() {
        assertEquals(List.of(13f, 15f, 17f), ActorKeyframeProjection.times(2, 4, AnimationLoopMode.LOOP, 10, 17, 2, 12, 30));
        assertEquals(List.of(), ActorKeyframeProjection.times(4, 4, AnimationLoopMode.LOOP, 10, 20, 1, 0, 30));
    }
    @Test void pingPongIncludesReversePassAndDoesNotDuplicateTurns() {
        assertEquals(List.of(1f, 7f, 9f, 15f), ActorKeyframeProjection.times(1, 4, AnimationLoopMode.PING_PONG, 0, 16, 1, 0, 16));
        assertEquals(List.of(4f, 12f), ActorKeyframeProjection.times(4, 4, AnimationLoopMode.PING_PONG, 0, 16, 1, 0, 16));
        assertEquals(List.of(0f, 8f, 16f), ActorKeyframeProjection.times(0, 4, AnimationLoopMode.PING_PONG, 0, 16, 1, 0, 16));
    }
    @Test void degenerateAndDenseInputRemainBounded() {
        assertEquals(List.of(3f), ActorKeyframeProjection.times(0, 0, AnimationLoopMode.LOOP, 3, 10, 1, 0, 10));
        assertTrue(ActorKeyframeProjection.times(0, 0.001f, AnimationLoopMode.LOOP, 0, 100000, 1, 0, 100000).size() <= 1024);
        assertTrue(ActorKeyframeProjection.times(Float.NaN, 4, AnimationLoopMode.ONCE, 0, 10, 1, 0, 10).isEmpty());
    }
}
