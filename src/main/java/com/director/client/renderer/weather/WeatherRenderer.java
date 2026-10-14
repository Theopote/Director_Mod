package com.director.client.renderer.weather;
import com.director.timeline.playback.weather.WeatherSample;
/** Compatibility facade; WeatherParticleController now owns all weather particles. */
public final class WeatherRenderer {
    private static final WeatherRenderer INSTANCE=new WeatherRenderer();
    private WeatherSample currentSample=WeatherSample.disabled();
    private WeatherRenderer() {}
    public static WeatherRenderer get() { return INSTANCE; }
    public void onWeatherSampleUpdated(WeatherSample sample) { currentSample=sample==null ? WeatherSample.disabled() : sample; }
    public WeatherSample getCurrentSample() { return currentSample; }
    public void tick() {}
}
