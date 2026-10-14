package com.director.timeline.playback.season;

import com.director.timeline.model.*;
import com.director.timeline.model.season.SeasonKeyframe;
import com.director.season.*;
import com.director.client.world.WorldRenderOverride;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeasonSystemRegressionTest {
    @BeforeAll static void setup() { com.director.core.DirectorServices.initialize(); }
    @AfterEach void cleanup() { TimelineSeasonController.getInstance().clear(); WorldRenderOverride.clearAll(); }
    private Object field(Object o,String name) throws Exception {
        var f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private SeasonKeyframe key(float t,float amount) {
        var k=new SeasonKeyframe("season",t); k.setSnowAmount(amount); k.setSnowThickness(3); k.setFreezeStrength(amount); return k;
    }
    private TimelineModel bind(SeasonKeyframe... keys) {
        var m=new TimelineModel(new TimelineViewportState()); m.setSuppressPersistenceWrites(true);
        var tr=new TimelineTrack("season",TrackType.SEASON); for(var k:keys)tr.addAutomationKeyframe(k); m.addTrack(tr);
        var c=TimelineSeasonController.getInstance(); c.setEnabled(true); c.init(m); return m;
    }
    private void assertManagersDisabled() throws Exception {
        assertFalse(((SeasonSample)field(SeasonSnowManager.get(),"currentSample")).enabled);
        assertNull(field(WaterFreezeManager.get(),"lastSample"));
        assertFalse(SeasonSnowManager.get().hasPendingWork()); assertFalse(WaterFreezeManager.get().hasPendingWork());
    }
    @Test void disableStopsBothManagers() throws Exception {
        bind(key(0,.9f)); var c=TimelineSeasonController.getInstance(); c.setEnabled(false); c.onTimelineTick(0);
        assertFalse(c.hasOverride()); assertManagersDisabled();
    }
    @Test void detachStopsBothManagers() throws Exception { bind(key(0,.9f)); TimelineSeasonController.getInstance().init(null); assertManagersDisabled(); }
    @Test void clearAndRebindSameModelWorks() {
        var m=bind(key(0,.9f)); var c=TimelineSeasonController.getInstance(); c.clear(); c.init(m); c.onTimelineTick(0);
        assertTrue(c.hasOverride()); assertEquals(.9f,c.getCurrentSample().snowAmount);
    }
    @Test void keyframePreviewSurvivesPropertyDirtyNotification() {
        var m=bind(key(0,0),key(10,.9f)); var c=TimelineSeasonController.getInstance(); c.previewAtKeyframeTime(10);
        m.notifyModelChanged("model_changed"); assertEquals(.9f,c.getCurrentSample().snowAmount);
        c.onTimelineTick(0); assertEquals(0,c.getCurrentSample().snowAmount);
    }
    @Test void disabledControllerDoesNotReapplyDuringRebuild() throws Exception {
        var m=bind(key(0,.9f)); var c=TimelineSeasonController.getInstance(); c.setEnabled(false); m.notifyModelChanged("model_changed"); assertManagersDisabled();
    }
    @Test void deletingLastTrackClearsEffects() throws Exception {
        var m=bind(key(0,.9f)); m.removeTrack(m.getTracks().getFirst()); m.notifyModelChanged("model_changed"); assertManagersDisabled();
    }
    @Test void deletingLastKeyAndRestoringRefreshesSampler() {
        var k=key(0,.9f); var m=bind(k); var tr=m.getTracks().getFirst(); var c=TimelineSeasonController.getInstance();
        tr.removeAutomationKeyframe(k.getId()); m.notifyModelChanged("model_changed"); assertFalse(c.hasOverride());
        tr.addAutomationKeyframe(k); m.notifyModelChanged("model_changed"); assertTrue(c.hasOverride());
    }
    @Test void switchingModelsToEmptyClearsEffects() throws Exception {
        bind(key(0,.9f)); var m=new TimelineModel(new TimelineViewportState());m.setSuppressPersistenceWrites(true);
        TimelineSeasonController.getInstance().init(m); assertManagersDisabled();
    }
    @Test void reenableImmediatelyResamples() {
        bind(key(0,.9f));var c=TimelineSeasonController.getInstance();c.setEnabled(false);c.setEnabled(true);assertTrue(c.hasOverride());
    }
    @Test void catmullRomUsesNeighbourValues() {
        var a=key(0,0); var b=key(10,.2f); b.setInterpolation(SeasonKeyframe.Interpolation.CATMULL_ROM);
        var c=key(20,.8f); var d=key(30,0); var s=new SeasonTrackSampler(List.of(a,b,c,d)); float before=s.sampleAt(12.5f).snowAmount;
        a.setSnowAmount(1);d.setSnowAmount(1);assertNotEquals(before,s.sampleAt(12.5f).snowAmount);
        assertEquals(.2f,s.sampleAt(10).snowAmount);assertEquals(.8f,s.sampleAt(20).snowAmount);
    }
    @Test void linearAndSmoothAreDifferent() {
        var a=key(0,0);var b=key(10,1);var s=new SeasonTrackSampler(List.of(a,b));a.setInterpolation(SeasonKeyframe.Interpolation.LINEAR);
        assertEquals(.25f,s.sampleAt(2.5f).snowAmount);a.setInterpolation(SeasonKeyframe.Interpolation.SMOOTH);assertEquals(.15625f,s.sampleAt(2.5f).snowAmount);
    }
    @Test void nonFiniteValuesAreNeutralized() {
        var k=key(0,.9f);k.setSnowAmount(Float.NaN);k.setFreezeStrength(Float.POSITIVE_INFINITY);k.setHumidity(Float.NaN);k.setTime(Float.NaN);
        k.getGrassTint().x=Float.NaN;var s=new SeasonTrackSampler(List.of(k)).sampleAt(Float.NaN);
        assertEquals(0,s.snowAmount);assertEquals(0,s.freezeStrength);assertEquals(0,s.humidity);assertEquals(0,s.grassTint.x);assertEquals(0,k.getTime());
    }
    @Test void shutdownCanBeReusedWithoutAnExecutor() {
        var ice=WaterFreezeManager.get();ice.shutdown();var sample=new SeasonSample();sample.enabled=true;sample.freezeStrength=.9f;
        ice.applySeasonSample(sample);assertTrue(ice.hasPendingWork());ice.clearAll();assertFalse(ice.hasPendingWork());
    }
    @Test void sectionTintSnapshotSurvivesClear() {
        var sample=new SeasonSample();sample.enabled=true;sample.grassTint.x=.5f;WorldRenderOverride.publishSeason(true,sample);
        try(var scope=SeasonTintContext.openScope()) {
            WorldRenderOverride.clearAll();assertEquals(.5f,SeasonTintContext.sample().grassTint.x);
            try(var nested=SeasonTintContext.openScope()) { assertEquals(.5f,SeasonTintContext.sample().grassTint.x); }
        }
        assertNull(SeasonTintContext.sample());
    }
}
