package com.director.ui.timeline.render;

import com.director.actor.rig.skeleton.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBlendEnvelopeTest {
    private PoseBlendSettings settings(float in,float out) {
        return new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.8f,7,true,Set.of(),in,out);
    }
    @Test void curveMatchesPlaybackAtEveryProjectedPointAndMovesWithClip() {
        var settings=settings(2,1);
        var points=ActorBlendEnvelope.points(settings,100,700,0,1000,6);
        assertEquals(100,points.getFirst().x());assertEquals(700,points.getLast().x());
        for(var point:points) assertEquals(settings.effectiveWeight(settings.envelopeAt((point.x()-100)/100,6)),point.weight(),1e-6f);
        var shifted=ActorBlendEnvelope.points(settings,300,900,0,1000,6);
        assertEquals(points.size(),shifted.size());
        for(int i=0;i<points.size();i++) {
            assertEquals(points.get(i).x()+200,shifted.get(i).x(),1e-5f);
            assertEquals(points.get(i).weight(),shifted.get(i).weight(),1e-6f);
        }
        assertEquals(2,points.stream().filter(ActorBlendEnvelope.Point::transition).count());
    }
    @Test void viewportCroppingDoesNotRestartEnvelopeAtVisibleEdge() {
        var settings=settings(2,1);
        var points=ActorBlendEnvelope.points(settings,100,700,200,650,6);
        assertEquals(200,points.getFirst().x());assertEquals(.4f,points.getFirst().weight(),1e-6f);
        assertEquals(650,points.getLast().x());assertEquals(.4f,points.getLast().weight(),1e-6f);
        assertTrue(points.stream().allMatch(p->p.x()>=200 && p.x()<=650));
    }
    @Test void shortFadesAndCompressedFadesRetainExactBoundariesEvenWhenZoomedOut() {
        var shortFade=settings(.1f,.1f);
        var points=ActorBlendEnvelope.points(shortFade,0,10,0,10,100);
        assertTrue(points.stream().anyMatch(p->Math.abs(p.x()-.01f)<1e-6f && p.transition()));
        assertTrue(points.stream().anyMatch(p->Math.abs(p.x()-9.99f)<1e-5f && p.transition()));
        points=ActorBlendEnvelope.points(settings(8,4),0,30,0,30,3);
        assertTrue(points.stream().anyMatch(p->p.x()==20 && p.weight()==.8f && p.transition()));
    }
    @Test void emptyMasksAndZeroWeightStayAtBaseline() {
        for(var settings:List.of(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,0,true,Set.of()),
                new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,Set.of()),
                new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,0,7,true,Set.of())))
            assertTrue(ActorBlendEnvelope.points(settings,0,100,0,100,2).stream().allMatch(p->p.weight()==0));
    }
    @Test void offscreenInvalidAndDenseInputsAreBounded() {
        var settings=settings(1,1);
        assertTrue(ActorBlendEnvelope.points(settings,100,200,0,50,2).isEmpty());
        assertTrue(ActorBlendEnvelope.points(settings,0,100,0,100,0).isEmpty());
        assertTrue(ActorBlendEnvelope.points(settings,Float.NaN,100,0,100,2).isEmpty());
        assertTrue(ActorBlendEnvelope.points(settings,0,100,100,0,2).isEmpty());
        assertTrue(ActorBlendEnvelope.points(settings,0,100000,0,100000,2).size()<=131);
    }
}
