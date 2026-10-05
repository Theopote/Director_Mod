package com.director.ui.timeline.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorKeyframeDragTest {
    @Test void convertsPixelsThroughClipSpeed() {
        assertEquals(2f, ActorKeyframeDrag.target(1, 50, 100, 2, 10, 10));
        assertEquals(0.5f, ActorKeyframeDrag.target(1, -100, 100, 0.5f, 10, 10));
    }
    @Test void boundsByBothAssetAndTrimmedClip() {
        assertEquals(0, ActorKeyframeDrag.target(1, -1000, 100, 1, 10, 10));
        assertEquals(3, ActorKeyframeDrag.target(1, 1000, 100, 1, 3, 10));
        assertEquals(4, ActorKeyframeDrag.target(1, 1000, 100, 2, 10, 2));
    }
    @Test void invalidViewportDoesNotMoveKey() {
        assertEquals(1, ActorKeyframeDrag.target(1, 20, 0, 1, 10, 10));
        assertEquals(1, ActorKeyframeDrag.target(1, Float.NaN, 100, 1, 10, 10));
    }
    @Test void groupClampsOneDeltaAndPreservesSpacing() {
        var times=java.util.List.of(1f,3f,4f);
        assertEquals(1f,ActorKeyframeDrag.groupDelta(times,1000,100,2,5,10));
        assertEquals(-1f,ActorKeyframeDrag.groupDelta(times,-1000,100,2,5,10));
        assertEquals(.5f,ActorKeyframeDrag.groupDelta(times,25,100,2,5,10));
    }
    @Test void groupHonorsTrimmedReferenceAndRejectsInvalidSelection() {
        assertEquals(.5f,ActorKeyframeDrag.groupDelta(java.util.List.of(1f,3.5f),1000,100,2,10,2));
        assertEquals(0f,ActorKeyframeDrag.groupDelta(java.util.List.of(1f,5f),100,100,2,10,2));
        assertEquals(0f,ActorKeyframeDrag.groupDelta(java.util.List.of(Float.NaN),100,100,1,10,10));
        assertEquals(0f,ActorKeyframeDrag.groupDelta(java.util.List.of(),100,100,1,10,10));
    }

}
