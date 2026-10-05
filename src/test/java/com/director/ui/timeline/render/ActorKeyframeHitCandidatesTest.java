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
    @Test void rectangleWorksInBothDirectionsAndIncludesEdges() {
        var points=List.of(p("topLeft",0,0),p("bottomRight",10,10),p("middle",5,5),p("outside",11,5));
        assertEquals(List.of("topLeft","bottomRight","middle"),ActorKeyframeHitCandidates.rectangle(points,10,10,0,0));
        assertEquals(ActorKeyframeHitCandidates.rectangle(points,0,0,10,10),ActorKeyframeHitCandidates.rectangle(points,10,10,0,0));
    }
    @Test void rectangleRetainsDenseKeysAndDeduplicatesRepeatedProjections() {
        var points=List.of(p("key1",5,5),p("key2",5,5),p("key1",6,5),p("invalid",Float.NaN,5));
        assertEquals(List.of("key1","key2"),ActorKeyframeHitCandidates.rectangle(points,0,0,10,10));
        assertTrue(ActorKeyframeHitCandidates.rectangle(points,Float.NaN,0,10,10).isEmpty());
        assertTrue(ActorKeyframeHitCandidates.rectangle(points,20,20,30,30).isEmpty());
    }

}
