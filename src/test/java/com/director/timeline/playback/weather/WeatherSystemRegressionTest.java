package com.director.timeline.playback.weather;
import com.director.timeline.model.*;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.timeline.playback.weather.*;
import com.director.client.world.WorldRenderOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
class WeatherSystemRegressionTest {
    @org.junit.jupiter.api.BeforeAll static void services() { com.director.core.DirectorServices.initialize(); }
    private WeatherKeyframe rain(float time) {
        return new WeatherKeyframe("rain",time,WeatherKeyframe.WeatherType.HEAVY_RAIN,TimelineInterpolation.LINEAR);
    }
    private TimelineTrack track(WeatherKeyframe... keys) {
        var track=new TimelineTrack("weather",TrackType.WEATHER);
        for(var key:keys)track.addAutomationKeyframe(key);
        return track;
    }
    @AfterEach void cleanup() {
        com.director.core.DirectorServices.get().containerTimeline().setTimelineModel(null);
        TimelineWeatherController.getInstance().clear(); WorldRenderOverride.clearAll();
    }
    @Test void stepHoldsUntilNextKey() {
        var a=new WeatherKeyframe("clear",0);a.setInterpolation(TimelineInterpolation.STEP);
        var b=rain(10); var sampler=new WeatherTrackSampler(track(a,b));
        assertEquals(0,sampler.sample(4.9f).rainIntensity);
        assertEquals(0,sampler.sample(5).rainIntensity);
        assertEquals(.9f,sampler.sample(10).rainIntensity);
    }
    @Test void switchingToEmptyTrackClearsWeather() {
        var controller=TimelineWeatherController.getInstance();
        controller.bindActiveTrack(track(rain(0)));controller.setEnabled(true);controller.previewAt(0);
        var before=controller.getCurrentSample(); controller.bindActiveTrack(track());
        assertNotSame(before,controller.getCurrentSample()); assertFalse(controller.hasOverride());
        assertNull(WorldRenderOverride.get().weatherSample());
    }
    @Test void deletingLastKeyClearsSampleImmediately() {
        var key=rain(0);var track=track(key);
        var controller=TimelineWeatherController.getInstance(); controller.bindActiveTrack(track);
        controller.setEnabled(true);controller.previewAt(0);
        track.removeAutomationKeyframe(key.getId());controller.rebuildFromActiveTrack();
        assertFalse(controller.getCurrentSample().enabled);assertFalse(controller.hasOverride());
    }
    @Test void emptySampleDoesNotAdvertiseOverride() {
        var controller=TimelineWeatherController.getInstance();controller.bindActiveTrack(track());
        controller.setEnabled(true);controller.previewAt(0);
        assertFalse(controller.getCurrentSample().enabled);
        assertFalse(controller.hasOverride());assertFalse(WorldRenderOverride.get().hasWeatherOverride());
    }
    @Test void clearingOverrideClearsParticleEmitterSample() throws Exception {
        var sample=new WeatherSample();sample.enabled=true;sample.snowIntensity=1;
        var particles=WeatherParticleController.getInstance();particles.updateSample(sample);
        var controller=TimelineWeatherController.getInstance();controller.bindActiveTrack(null);
        controller.updateForPlayback(0);
        var field=WeatherParticleController.class.getDeclaredField("currentSample");field.setAccessible(true);
        assertNull(field.get(particles));
    }
    @Test void nanIntensityIsNeutralized() {
        var key=rain(0);key.setRainStrength(Float.NaN);
        assertEquals(0,new WeatherTrackSampler(track(key)).sample(0).rainIntensity);
    }
    @Test void windAngleHandlesHugeAndNonFiniteValues() {
        float angle=1e20f;
        assertTrue(Float.isFinite(angle));assertEquals(angle,angle-360f);
        var key=rain(0);key.setWindDirectionDegrees(Float.NaN);
        assertEquals(0,key.getWindDirectionDegrees());
        key.setWindDirectionDegrees(angle);
        assertTrue(key.getWindDirectionDegrees()>=0 && key.getWindDirectionDegrees()<360);
        key.setWindDirectionDegrees(Float.POSITIVE_INFINITY);
        assertEquals(0,key.getWindDirectionDegrees());
    }
    @Test void normalDeleteUndoRedoRefreshWeatherCache() {
        var rain=rain(0);var clear=new WeatherKeyframe("clear",10);
        var track=track(rain,clear);
        // Keep real model notifications while disabling only test persistence writes.
        var model=new TimelineModel(new TimelineViewportState()) {
            @Override public void markDirty() { super.markDirty(); notifyModelChanged("model_changed"); }
        };
        model.setSuppressPersistenceWrites(true);
        var controller=TimelineWeatherController.getInstance();
        com.director.core.DirectorServices.get().containerTimeline().setTimelineModel(model);
        model.addModelChangeListener(change -> controller.checkAndAutoActivate());
        model.addTrack(track);controller.previewAt(0);
        var command=new com.director.ui.timeline.commands.RemoveAutomationKeyframeCommand(model,track,rain);
        command.execute();
        assertEquals(1,track.getAutomationKeyframes().size());controller.previewAt(0);
        assertEquals(0,controller.getCurrentSample().rainIntensity);
        command.undo();controller.previewAt(0);assertEquals(.9f,controller.getCurrentSample().rainIntensity);
        command.execute();controller.previewAt(0);assertEquals(0,controller.getCurrentSample().rainIntensity);
    }

    @Test void partialClearRemovesFlash() throws Exception {
        var controller=TimelineWeatherController.getInstance();controller.bindActiveTrack(track(rain(0)));
        controller.setEnabled(true);controller.previewAt(0);
        var event=WeatherEventController.getInstance();
        var flash=WeatherEventController.class.getDeclaredField("currentFlashBrightness");flash.setAccessible(true);flash.set(event,.8f);
        controller.bindActiveTrack(null);controller.updateForPlayback(0);controller.tickEvents(100);
        assertFalse(controller.hasOverride());assertEquals(0,WorldRenderOverride.get().weatherFlashBrightness());
    }

    @Test void cloudPhaseIsContinuousAtSpeedChanges() {
        var a=rain(0);a.setCloudSpeed(1);a.setWindStrength(0);a.setInterpolation(TimelineInterpolation.STEP);
        var b=rain(10);b.setCloudSpeed(2);b.setWindStrength(0);
        var sampler=new WeatherTrackSampler(track(a,b));var effects=sampler.effects();
        assertEquals(200,effects.cloudPhase(10),.001f);
        assertEquals(240,effects.cloudPhase(11),.001f);
        assertEquals(200,effects.cloudPhase(9.9999f),.01f);
    }
    @Test void linearCloudSpeedUsesIntegratedDistance() {
        var a=rain(0);a.setCloudSpeed(0);a.setWindStrength(0);
        var b=rain(10);b.setCloudSpeed(2);b.setWindStrength(0);
        var effects=new WeatherTrackSampler(track(a,b)).effects();
        assertEquals(50,effects.cloudPhase(5),.01f);assertEquals(200,effects.cloudPhase(10),.01f);
    }
    @Test void lightningFlashIsStableAcrossSeekAndRepeatedFrames() {
        var key=rain(0);key.setThunderStrength(1);key.setLightningFrequency(20);
        var effects=new WeatherTrackSampler(track(key)).effects();
        float expected=effects.flash(1.03f);
        assertTrue(expected>0);effects.flash(10);effects.flash(.1f);
        assertEquals(expected,effects.flash(1.03f));assertEquals(expected,effects.flash(1.03f));
        assertEquals(0,effects.flash(Float.NaN));
        assertTrue(Float.isFinite(effects.flash(Float.MAX_VALUE)));
    }
    @Test void particlesAreBoundedAndReconstructibleOnSeek() {
        var key=rain(0);key.setRainType(WeatherKeyframe.RainType.SLEET);key.setRainStrength(1);key.setSnowStrength(1);
        var effects=new WeatherTrackSampler(track(key)).effects();
        var atTwo=WeatherParticleField.sample(effects,2);
        assertFalse(atTwo.isEmpty());assertTrue(atTwo.size()<=WeatherParticleField.LIMIT);
        WeatherParticleField.sample(effects,10);WeatherParticleField.sample(effects,1);
        assertEquals(atTwo,WeatherParticleField.sample(effects,2));
        assertEquals(atTwo,WeatherParticleField.sample(effects,2));
        assertEquals(atTwo.size(),atTwo.stream().map(WeatherParticleField.Point::id).distinct().count());
    }
    @Test void effectsRespectDisabledEmptyTrack() {
        var effects=new WeatherTrackSampler(track()).effects();
        assertEquals(0,effects.flash(5));assertTrue(WeatherParticleField.sample(effects,5).isEmpty());
    }
    @Test void retainedParticlesExpireWhenRainStops() {
        var a=rain(0);a.setRainStrength(1);a.setInterpolation(TimelineInterpolation.STEP);
        var b=new WeatherKeyframe("clear",1);
        var effects=new WeatherTrackSampler(track(a,b)).effects();
        assertFalse(WeatherParticleField.sample(effects,.9f).isEmpty());
        assertTrue(WeatherParticleField.sample(effects,1.3f).isEmpty());
    }
    @Test void numericParametersRemainFinite() {
        var key=rain(0);key.setCloudSpeed(Float.NaN);key.setCloudBrightness(Float.POSITIVE_INFINITY);
        key.setLightningFrequency(Float.NaN);key.setFogColor(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY);
        key.setTime(Float.NaN);key.setTempBias(Float.NaN);key.setHumidityBias(Float.NaN);
        assertTrue(Float.isFinite(key.getCloudSpeed()));assertTrue(Float.isFinite(key.getCloudBrightness()));
        assertEquals(0,key.getLightningFrequency());assertEquals(0,key.getFogColor().x);
        assertEquals(0,key.getTime());assertEquals(0,key.getTempBias());assertEquals(0,key.getHumidityBias());
    }

    @Test void propertyPreviewUsesItsOwnTimeWithoutMovingPlaybackHead() {
        var model=new TimelineModel(new TimelineViewportState());model.setSuppressPersistenceWrites(true);
        var a=rain(0);var b=new WeatherKeyframe("clear",10);var track=track(a,b);model.addTrack(track);
        com.director.core.DirectorServices.get().containerTimeline().setTimelineModel(model);
        var controller=TimelineWeatherController.getInstance();controller.bindActiveTrack(track);controller.setEnabled(true);
        controller.previewAt(10);
        assertEquals(0,model.getCurrentTime());assertEquals(10,controller.getSampledTime());
        assertEquals(0,controller.getCurrentSample().rainIntensity);
        controller.previewAt(Float.NaN);
        assertEquals(0,controller.getSampledTime());assertEquals(.9f,controller.getCurrentSample().rainIntensity);
    }
    @Test void clearingSamplerAlsoClearsEffectTimeline() {
        var key=rain(0);key.setThunderStrength(1);key.setLightningFrequency(20);
        var sampler=new WeatherTrackSampler(track(key));assertTrue(sampler.effects().flash(1)>0);
        sampler.clear();
        assertEquals(0,sampler.effects().flash(1));assertEquals(0,sampler.effects().cloudPhase(1));
        assertTrue(WeatherParticleField.sample(sampler.effects(),1).isEmpty());
    }
}
