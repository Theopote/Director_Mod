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
    @Test void sharedDragTargetProjectsThroughDifferentRatesAndRepeats() {
        float target=ActorKeyframeDrag.target(1,50,100,2,4,8);
        assertEquals(2f,target);
        assertEquals(List.of(11f),ActorKeyframeProjection.times(target,4,AnimationLoopMode.ONCE,10,20,2,0,30));
        assertEquals(List.of(22f),ActorKeyframeProjection.times(target,4,AnimationLoopMode.ONCE,20,30,1,0,30));
        assertEquals(List.of(1f,3f,5f),ActorKeyframeProjection.times(target,4,AnimationLoopMode.LOOP,0,5,2,0,30));
        assertEquals(List.of(2f,6f,10f),ActorKeyframeProjection.times(target,4,AnimationLoopMode.PING_PONG,0,10,1,0,30));
        assertTrue(ActorKeyframeProjection.times(target,4,AnimationLoopMode.ONCE,0,.5f,1,0,30).isEmpty());
    }

    @Test void nearestSeekStaysInCurrentLoopAndHandlesReversePass() {
        assertEquals(17f,ActorKeyframeProjection.nearest(2,4,AnimationLoopMode.LOOP,10,30,2,17.2f).orElseThrow());
        assertEquals(7f,ActorKeyframeProjection.nearest(1,4,AnimationLoopMode.PING_PONG,0,20,1,6.9f).orElseThrow());
        assertEquals(1f,ActorKeyframeProjection.nearest(1,4,AnimationLoopMode.LOOP,0,10,1,3).orElseThrow());
        assertTrue(ActorKeyframeProjection.nearest(4,4,AnimationLoopMode.LOOP,0,20,1,10).isEmpty());
    }
    @Test void nearestSeekRespectsTrimAndIsNotLimitedByDenseDisplayCap() {
        assertTrue(ActorKeyframeProjection.nearest(4,4,AnimationLoopMode.ONCE,10,11,2,10).isEmpty());
        assertEquals(11f,ActorKeyframeProjection.nearest(2,4,AnimationLoopMode.ONCE,10,20,2,19).orElseThrow());
        assertEquals(99997f,ActorKeyframeProjection.nearest(1,4,AnimationLoopMode.LOOP,0,100000,1,99999).orElseThrow());
        assertTrue(ActorKeyframeProjection.nearest(1,4,AnimationLoopMode.ONCE,0,10,1,Float.NaN).isEmpty());
    }

}
