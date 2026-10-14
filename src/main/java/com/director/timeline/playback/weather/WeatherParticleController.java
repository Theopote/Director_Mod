package com.director.timeline.playback.weather;

import com.director.mixin.accessor.WeatherParticleAccessor;
import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.timeline.playback.camera.CameraRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Weather's own bounded particle pool is reconstructed from project time on seeks. */
public final class WeatherParticleController {
    private static final WeatherParticleController INSTANCE=new WeatherParticleController();
    public static WeatherParticleController getInstance() { return INSTANCE; }
    private boolean enabled=true;
    private WeatherSample currentSample;
    private ClientWorld world;
    private final Map<Long,Owned> particles=new HashMap<>();
    private record Owned(Particle particle, double ground) {}
    private int anchorX,anchorY,anchorZ;
    public void setEnabled(boolean value) { enabled=value; if(!value)clear(); }
    public boolean isEnabled() { return enabled; }
    public void updateSample(WeatherSample sample) {
        currentSample=sample;
        if(sample==null || !sample.enabled)clear();
    }
    public void clear() {
        particles.values().forEach(p -> p.particle().markDead()); particles.clear(); world=null;
    }
    public void tick() {
        if(!enabled || currentSample==null || !currentSample.enabled) { clear(); return; }
        var client=MinecraftClient.getInstance(); var model=TimelinePlaybackSampler.resolveModel();
        if(client==null || client.world==null || client.gameRenderer==null || client.particleManager==null || model==null)return;
        var runtime=CameraRuntime.getInstance();
        var pos=runtime.isTimelineOwner() ? runtime.getCurrentSample().position() : client.gameRenderer.getCamera().getPos();
        int x=MathHelper.floor(pos.x/16)*16,y=MathHelper.floor(pos.y/16)*16,z=MathHelper.floor(pos.z/16)*16;
        if(world!=client.world || x!=anchorX || y!=anchorY || z!=anchorZ) {
            clear(); world=client.world; anchorX=x;anchorY=y;anchorZ=z;
        }
        var points=WeatherParticleField.sample(TimelineWeatherController.getInstance().effects(),TimelineWeatherController.getInstance().getSampledTime());
        var keep=new HashSet<Long>();
        for(var point:points) {
            keep.add(point.id()); var owned=particles.get(point.id());
            if(owned==null || !owned.particle().isAlive()) {
                double ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,
                        MathHelper.floor(anchorX+point.x()),MathHelper.floor(anchorZ+point.z()));
                WeatherParticleSeed.set(point.seed());
                Particle particle;
                try {
                    var type=switch(point.kind()) {
                        case SNOW -> ParticleTypes.SNOWFLAKE; case CLOUD -> ParticleTypes.CLOUD;
                        case RAIN -> ParticleTypes.RAIN; case SPLASH -> ParticleTypes.SPLASH;
                    };
                    particle=client.particleManager.addParticle(type,anchorX+point.x(),
                            point.kind()==WeatherParticleField.Kind.RAIN || point.kind()==WeatherParticleField.Kind.SPLASH
                                    ? ground+.08 : Math.max(ground+.1,anchorY+point.y()),anchorZ+point.z(),0,0,0);
                } finally { WeatherParticleSeed.clear(); }
                if(particle==null)continue;
                particle.setMaxAge(switch(point.kind()) { case SNOW -> 40; case CLOUD -> 20; default -> 5; });
                owned=new Owned(particle,ground);particles.put(point.id(),owned);
            }
            double py=point.kind()==WeatherParticleField.Kind.RAIN || point.kind()==WeatherParticleField.Kind.SPLASH
                    ? owned.ground()+.08 : Math.max(owned.ground()+.1,anchorY+point.y());
            var p=owned.particle();p.setPos(anchorX+point.x(),py,anchorZ+point.z());
            var accessor=(WeatherParticleAccessor)p;
            accessor.director$lastX(anchorX+point.x());accessor.director$lastY(py);accessor.director$lastZ(anchorZ+point.z());
            accessor.director$age(Math.max(0,(int)Math.floor(point.age()*20)));
        }
        particles.entrySet().removeIf(entry -> {
            if(keep.contains(entry.getKey()))return false;
            entry.getValue().particle().markDead();return true;
        });
    }
}
