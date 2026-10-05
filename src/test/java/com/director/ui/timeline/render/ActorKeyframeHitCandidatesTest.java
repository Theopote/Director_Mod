package com.director.ui.timeline.render;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorKeyframeHitCandidatesTest {
    private ActorKeyframeHitCandidates.Point<String> p(String key,float x,float y) {
        return new ActorKeyframeHitCandidates.Point<>(key,x,y);
    }
    @Test void overlappingKeysRemainSelectableAndNearestComesFirst() {
        assertEquals(List.of("near","samePixel","far"),ActorKeyframeHitCandidates.near(
                List.of(p("far",13,10),p("near",10,10),p("samePixel",10.2f,10)),10,10,6));
    }
    @Test void identicalCandidatesDeduplicateButDistinctReferencesDoNot() {
        assertEquals(List.of("clipA/key1","clipB/key1","clipA/key2"),ActorKeyframeHitCandidates.near(
                List.of(p("clipA/key1",10,10),p("clipA/key1",10,10),p("clipB/key1",10,10),p("clipA/key2",10,10)),10,10,6));
    }
    @Test void excludesOtherChannelsOutsideRadiusAndInvalidCoordinates() {
        assertEquals(List.of("key"),ActorKeyframeHitCandidates.near(
                List.of(p("key",0,0),p("channel",0,7),p("edge",6,0),p("invalid",Float.NaN,0)),0,0,6));
        assertTrue(ActorKeyframeHitCandidates.near(List.of(p("key",0,0)),Float.NaN,0,6).isEmpty());
        assertTrue(ActorKeyframeHitCandidates.near(List.of(p("key",0,0)),0,0,-1).isEmpty());
    }
}
