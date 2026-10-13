package com.director.lighting;
import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.ui.lighting.*;
import com.director.ui.timeline.commands.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightTimelineTest {
    TimelineModel model() {var m=new TimelineModel(new TimelineViewportState());m.setSuppressPersistenceWrites(true);return m;}
    PointLight point() {return PointLight.create(0,0,0);}
    LightTimelineClip schedule(TimelineModel m,PointLight l,float start,float end) {
        m.replacePointLight(l.id(),l);var t=new TimelineTrack("Light",TrackType.LIGHT);
        var c=new LightTimelineClip(l.name(),start,end,l.id());assertTrue(t.addClip(c));m.addTrack(t);return c;
    }
    PointLight line() {return PointLight.createLine(List.of(new LightPoint(0,0,0),new LightPoint(10,0,0),new LightPoint(10,10,0)));}
    @Test void lineEmitsAcrossEntirePolylineAndFallsOffBeyondEndpoints() {
        var l=line();assertEquals(15,PointLightSampler.level(List.of(l),5,0,0));
        assertEquals(15,PointLightSampler.level(List.of(l),10,5,0));
        assertEquals(7.5,PointLightSampler.level(List.of(l),5,0,4));
        assertEquals(7.5,PointLightSampler.level(List.of(l),-4,0,0));
        assertEquals(0,PointLightSampler.level(List.of(l),-8,0,0));
    }
    @Test void coincidentLineAnchorsRemainFinite() {
        var l=PointLight.createLine(List.of(new LightPoint(2,3,4),new LightPoint(2,3,4)));
        assertEquals(15,PointLightSampler.level(List.of(l),2,3,4));assertEquals(0,LightGeometry.distanceSquared(l,2,3,4));
    }
    @Test void anchorEditOnlyMovesItsSelectedVertexAndWholeTranslationMovesAll() {
        var l=line();var edited=l.withAnchor(1,new LightPoint(8,0,3));
        assertEquals(new LightPoint(8,0,3),LightGeometry.vertices(edited).get(1));
        assertEquals(LightGeometry.vertices(l).getFirst(),LightGeometry.vertices(edited).getFirst());
        assertEquals(new LightPoint(12,3,4),LightGeometry.vertices(l.at(2,3,4)).get(1));
    }
    @Test void areaHasFullIntensityOverItsSurfaceAndUsesDistanceToItsEdges() {
        var l=PointLight.createArea(0,0,0,0,0).area(10,6,0,0);
        assertEquals(15,PointLightSampler.level(List.of(l),4,2,0));
        assertEquals(7.5,PointLightSampler.level(List.of(l),4,2,4));
        assertEquals(7.5,PointLightSampler.level(List.of(l),9,0,0));
        assertEquals(7.5,PointLightSampler.level(List.of(l),0,0,-4));
    }
    @Test void rotatedAreaDistanceMatchesTransformedCorners() {
        var l=PointLight.createArea(20,30,40,53,-37).area(10,6,53,-37);
        for(var v:LightGeometry.vertices(l)) assertEquals(0,LightGeometry.distanceSquared(l,v.x(),v.y(),v.z()),1e-10);
        var horizontal=PointLight.createArea(0,0,0,0,-90).area(10,6,0,-90);
        assertEquals(0,LightGeometry.distanceSquared(horizontal,4,0,2),1e-10);
        assertEquals(16,LightGeometry.distanceSquared(horizontal,0,4,0),1e-10);
    }
    @Test void influenceBoundsIncludeGeometryExtentsAndFalloff() {
        var b=PointLightChanges.affected(List.of(),List.of(line())).getFirst();
        assertTrue(b.maxX()>=19 && b.maxY()>=19 && b.minX()<=-9);
        var a=PointLight.createArea(0,0,0,0,0).area(20,4,0,0);
        assertTrue(PointLightChanges.affected(List.of(),List.of(a)).getFirst().maxX()>=19);
    }
    @Test void invalidGeometryAndMissingLineAnchorsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PointLight.createLine(List.of(new LightPoint(0,0,0))));
        assertThrows(IllegalArgumentException.class,()->PointLight.createArea(0,0,0,Float.NaN,0));
        assertThrows(IllegalArgumentException.class,()->PointLight.createArea(0,0,0,0,0).area(65,2,0,0));
    }
    @Test void clipIntervalIsStartInclusiveEndExclusiveWithNoLightInGaps() {
        var m=model();var l=point();schedule(m,l,2,5);
        assertTrue(LightTimelineSampler.sample(m,1.999f).isEmpty());assertEquals(List.of(l),LightTimelineSampler.sample(m,2));
        assertEquals(List.of(l),LightTimelineSampler.sample(m,4.999f));assertTrue(LightTimelineSampler.sample(m,5).isEmpty());
        schedule(m,l,8,10);assertTrue(LightTimelineSampler.sample(m,6).isEmpty());assertEquals(1,LightTimelineSampler.sample(m,8).size());
    }
    @Test void unscheduledAssetsAndDeletedClipsDoNotEmit() {
        var m=model();var l=point();m.replacePointLight(l.id(),l);assertTrue(LightTimelineSampler.sample(m,0).isEmpty());
        var c=schedule(m,l,0,5);m.findTrackForClip(c).removeClip(c);assertTrue(LightTimelineSampler.sample(m,1).isEmpty());
    }
    @Test void visibleMutedSoloAndMasterEnableControlLights() {
        var m=model();var a=point();var b=point().at(20,0,0);var ca=schedule(m,a,0,5);var cb=schedule(m,b,0,5);
        var ta=m.findTrackForClip(ca);var tb=m.findTrackForClip(cb);assertEquals(2,LightTimelineSampler.sample(m,1).size());
        ta.setMuted(true);assertEquals(List.of(b),LightTimelineSampler.sample(m,1));ta.setMuted(false);
        ta.setVisible(false);assertEquals(List.of(b),LightTimelineSampler.sample(m,1));ta.setVisible(true);
        ta.setSolo(true);assertEquals(List.of(a),LightTimelineSampler.sample(m,1));
        tb.setSolo(true);assertEquals(2,LightTimelineSampler.sample(m,1).size());
        m.replacePointLight(a.id(),a.settings(15,8,1,false));assertEquals(List.of(b),LightTimelineSampler.sample(m,1));
    }
    @Test void mostRecentlyStartedScheduleWinsForTheSameLight() {
        var m=model();var l=point();var earlier=schedule(m,l,0,10);var later=schedule(m,l,2,5);
        earlier.addKeyframe(new LightKeyframe(0,l.settings(4,8,1,true)));later.addKeyframe(new LightKeyframe(2,l.settings(12,8,1,true)));
        assertEquals(4,LightTimelineSampler.sample(m,1).getFirst().brightness());
        assertEquals(12,LightTimelineSampler.sample(m,3).getFirst().brightness());
        assertEquals(4,LightTimelineSampler.sample(m,6).getFirst().brightness());
    }
    @Test void linearKeysAnimatePositionBrightnessRangeAndFalloff() {
        var m=model();var l=point();var c=schedule(m,l,0,10);
        c.addKeyframe(new LightKeyframe(0,l.settings(0,4,1,true)));c.addKeyframe(new LightKeyframe(10,l.at(10,20,30).settings(10,12,3,true)));
        var v=c.sample(l,5);assertEquals(5,v.brightness());assertEquals(8,v.range());assertEquals(2,v.falloff());
        assertEquals(5,v.x());assertEquals(10,v.y());assertEquals(15,v.z());
    }
    @Test void smoothAndStepModesFollowTheLeftKeyAndHoldOutsideKeys() {
        var l=point();var c=new LightTimelineClip("L",0,10,l.id());var a=new LightKeyframe(2,l.settings(0,8,1,true));
        var b=new LightKeyframe(6,l.settings(12,8,1,true));c.addKeyframe(a);c.addKeyframe(b);
        a.setInterpolation(TimelineInterpolation.SMOOTH);assertEquals(1.875,c.sample(l,3).brightness());
        a.setInterpolation(TimelineInterpolation.STEP);assertEquals(0,c.sample(l,5.9f).brightness());assertEquals(12,c.sample(l,6).brightness());
        assertEquals(0,c.sample(l,0).brightness());assertEquals(12,c.sample(l,9).brightness());
    }
    @Test void zeroAndDisabledAnimatedValuesEmitNothing() {
        var m=model();var l=point();var c=schedule(m,l,0,5);var k=new LightKeyframe(0,l.settings(0,8,1,true));c.addKeyframe(k);
        assertTrue(LightTimelineSampler.sample(m,1).isEmpty());k.setValue(l.settings(15,8,1,false));assertTrue(LightTimelineSampler.sample(m,1).isEmpty());
    }
    @Test void areaKeysAnimateDimensionsAndUseShortestYawPath() {
        var a=PointLight.createArea(0,0,0,170,0);var b=a.area(10,6,-170,80);var v=LightTimelineClip.interpolate(a,b,.5);
        assertEquals(6,v.width());assertEquals(4,v.height());assertEquals(180,v.yaw());assertEquals(40,v.pitch());
    }
    @Test void lineKeysInterpolateAnchorsAndKeepImmutableSourceGeometry() {
        var a=line();var b=a.withAnchor(1,new LightPoint(20,0,0));var v=LightTimelineClip.interpolate(a,b,.5);
        assertEquals(15,LightGeometry.vertices(v).get(1).x());assertEquals(10,LightGeometry.vertices(a).get(1).x());
    }
    @Test void wholeClipMovementMovesKeysAndUndoRestoresTheirTimes() {
        var m=model();var l=point();var c=schedule(m,l,0,10);var k=new LightKeyframe(3,l);c.addKeyframe(k);var t=m.findTrackForClip(c);
        var h=new TimelineCommandHistory();h.executeCommand(new MoveClipCommand(m,c,t,t,0,10,20,30));
        assertEquals(23,k.getTime());assertEquals(20,c.getStartTime());h.undo();assertEquals(3,k.getTime());h.redo();assertEquals(23,k.getTime());
    }
    @Test void clipCopyKeepsLightKindAndKeyValuesWithIndependentKeyObjects() {
        var l=line();var c=new LightTimelineClip("L",0,10,l.id());var k=new LightKeyframe(2,l);c.addKeyframe(k);
        var copy=(LightTimelineClip)TimelineClipCopies.copy(c);assertEquals(l.id(),copy.lightId());assertNotSame(k,copy.getClipKeyframes().getFirst());
        copy.offsetTime(10);assertEquals(2,k.getTime());assertEquals(12,copy.getClipKeyframes().getFirst().getTime());
    }
    @Test void trimmingCapturesBoundaryValuesAndSnapshotUndoRestoresOriginalKeys() {
        var l=point();var c=new LightTimelineClip("L",0,10,l.id());var a=new LightKeyframe(0,l.settings(0,8,1,true));var b=new LightKeyframe(10,l.settings(10,8,1,true));c.addKeyframe(a);c.addKeyframe(b);
        var before=c.captureState();c.setTimeRange(2,8);assertEquals(2,c.sample(l,2).brightness());assertEquals(8,c.sample(l,8).brightness());
        assertEquals(5,c.sample(l,5).brightness());c.restoreState(before);assertEquals(List.of(a,b),c.getClipKeyframes());assertEquals(0,c.getStartTime());assertEquals(10,c.getEndTime());
    }
    @Test void tracksRejectIncompatibleClipTypesAndLightKeysRequireMatchingAssets() {
        var l=point();var c=new LightTimelineClip("L",0,10,l.id());
        assertFalse(new TimelineTrack("Other",TrackType.CONTAINER).addClip(c));
        assertFalse(new TimelineTrack("Light",TrackType.LIGHT).addClip(new TimelineClip("C",0,10,0)));
        assertThrows(IllegalArgumentException.class,()->c.addKeyframe(new LightKeyframe(1,point())));
        assertThrows(IllegalArgumentException.class,()->c.addKeyframe(1f));
    }
    @Test void creationUndoRedoIncludesLightTrackClipAndAsset() {
        var m=model();m.setCurrentTime(3);var l=line();var h=new TimelineCommandHistory();var cmd=new CreateLightCommand(m,l);
        int tracks=m.getTracks().size();h.executeCommand(cmd);assertEquals(tracks+1,m.getTracks().size());assertEquals(l,m.getPointLight(l.id()));
        assertEquals(3,cmd.clip().getStartTime());assertEquals(List.of(l),LightTimelineSampler.sample(m,3));
        h.undo();assertNull(m.getPointLight(l.id()));assertEquals(tracks,m.getTracks().size());h.redo();assertEquals(List.of(l),LightTimelineSampler.sample(m,3));
    }
    @Test void keyframeGesturePreviewsAnimationAndGroupsUndoWithoutEditingSceneDefaults() {
        var m=model();var l=point();var c=schedule(m,l,0,10);var k=new LightKeyframe(0,l);c.addKeyframe(k);
        var edit=new PointLightParameterEdit(m,c,k);edit.preview(l.settings(7,8,1,true));edit.preview(l.settings(3,8,1,true));
        assertEquals(3,LightTimelineSampler.sample(m,1).getFirst().brightness());assertEquals(l,m.getPointLight(l.id()));
        var h=new TimelineCommandHistory();assertTrue(edit.commit((a,b)->h.executeCommand(new StateChangeCommand(m,"Key",()->k.setValue(a),()->k.setValue(b)))));
        assertEquals(1,h.getUndoSize());h.undo();assertEquals(l,k.value());h.redo();assertEquals(3,k.value().brightness());
    }
    @Test void deletedClipInvalidatesPendingKeyframeGesture() {
        var m=model();var l=point();var c=schedule(m,l,0,10);var k=new LightKeyframe(0,l);c.addKeyframe(k);var edit=new PointLightParameterEdit(m,c,k);
        edit.preview(l.settings(7,8,1,true));m.findTrackForClip(c).removeClip(c);assertFalse(edit.commit((a,b)->fail("Deleted key must not be committed")));
    }
    @Test void allKindsAndAnimatedValuesSurviveJsonRoundTrip() {
        var m=model();var a=line();var b=PointLight.createArea(20,30,40,57,-32);var c=schedule(m,a,1,9);schedule(m,b,2,8);
        var key=new LightKeyframe(3,a.withAnchor(1,new LightPoint(8,0,3)).settings(6,12,2,true));key.setInterpolation(TimelineInterpolation.SMOOTH);c.addKeyframe(key);
        var g=new Gson();var dto=g.fromJson(g.toJson(TimelineModelPersistence.toDTO(m,null)),TimelineModelPersistence.TimelineDTO.class);
        var restored=model();TimelineModelPersistence.fromDTO(dto,restored,null);assertEquals(m.getPointLights(),restored.getPointLights());
        var copied=restored.getTracks().stream().flatMap(t->t.getClips().stream()).filter(x->x instanceof LightTimelineClip l && l.lightId().equals(a.id())).map(x->(LightTimelineClip)x).findFirst().orElseThrow();
        assertEquals(1,copied.getClipKeyframes().size());assertEquals(key.value(),((LightKeyframe)copied.getClipKeyframes().getFirst()).value());
        assertEquals(TimelineInterpolation.SMOOTH,copied.getClipKeyframes().getFirst().getInterpolation());assertEquals(LightTimelineSampler.sample(m,4),LightTimelineSampler.sample(restored,4));
    }
    @Test void legacyPointLightFormatMigratesOnceAndDoesNotResurrectDeletedSchedules() {
        var l=point();var g=new Gson();String lightJson=g.toJson(l);var obj=com.google.gson.JsonParser.parseString(lightJson).getAsJsonObject();
        for(String k:List.of("type","anchors","width","height","yaw","pitch")) obj.remove(k);
        var dto=g.fromJson("{\"duration\":12,\"tracks\":[],\"pointLights\":["+obj+"]}",TimelineModelPersistence.TimelineDTO.class);
        var m=model();TimelineModelPersistence.fromDTO(dto,m,null);assertEquals(LightType.POINT,m.getPointLights().getFirst().type());assertEquals(List.of(l),LightTimelineSampler.sample(m,1));
        var saved=TimelineModelPersistence.toDTO(m,null);var restored=model();TimelineModelPersistence.fromDTO(saved,restored,null);assertEquals(1,restored.getTracks().size());
        restored.getTracks().getFirst().removeClip(restored.getTracks().getFirst().getClips().getFirst());
        var again=model();TimelineModelPersistence.fromDTO(TimelineModelPersistence.toDTO(restored,null),again,null);assertTrue(LightTimelineSampler.sample(again,1).isEmpty());
    }
}
