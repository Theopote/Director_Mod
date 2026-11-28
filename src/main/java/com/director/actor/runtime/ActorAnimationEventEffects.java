package com.director.actor.runtime;

import com.director.actor.prop.AnimationEventParticles;
import com.director.timeline.playback.TimelineSamplingContext;
import com.director.timeline.playback.camera.CameraSample;
import com.director.timeline.playback.entity.EntityActionEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side one-shot effects shared by editor preview and offline timeline sampling. */
public final class ActorAnimationEventEffects {
    private static final ActorAnimationEventEffects INSTANCE = new ActorAnimationEventEffects();
    private final List<Shake> shakes = new ArrayList<>();
    private record Shake(UUID ownerId, float start, float duration, float intensity, float frequency) {}

    private ActorAnimationEventEffects() {}
    public static ActorAnimationEventEffects getInstance() { return INSTANCE; }

    public void dispatch(Entity entity, EntityActionEvent event, float timelineTime) {
        dispatch(entity, event, timelineTime, null);
    }

    /** {@code ownerId} scopes camera shake replacement for the same actor; null = anonymous source. */
    public void dispatch(Entity entity, EntityActionEvent event, float timelineTime, UUID ownerId) {
        if (event == null || event.type() == null) return;
        TimelineSamplingContext context = TimelineSamplingContext.current();
        switch (event.type()) {
            case PLAY_SOUND -> {
                if (context.allowsLiveAudio()) {
                    playSound(MinecraftClient.getInstance(), event.params());
                }
            }
            case SPAWN_PARTICLE -> {
                if (context.allowsParticles() && entity != null) {
                    spawnParticles(MinecraftClient.getInstance(), entity, event.params());
                }
            }
            // Visibility is sticky in EntityClipSampler → sample.visibility; do not setInvisible here.
            case CAMERA_SHAKE -> {
                // Export/headless sampling must not advance the shared preview shake state.
                if (context.isPreview()) {
                    UUID id = ownerId != null ? ownerId : UUID.randomUUID();
                    shakes.removeIf(s -> id.equals(s.ownerId()));
                    float duration = Math.max(0.01f, number(event.params(), "duration", .35f));
                    shakes.add(new Shake(id, timelineTime, duration,
                            number(event.params(), "intensity", .25f),
                            number(event.params(), "frequency", 14f)));
                }
            }
            case SET_VISIBILITY -> { }
            default -> { }
        }
    }

    /** Sole camera-shake apply point; TimelineCameraController must call this when sampling. */
    public CameraSample decorate(CameraSample sample, float timelineTime) {
        if (sample == null || !TimelineSamplingContext.current().isPreview()) return sample;
        shakes.removeIf(s -> timelineTime < s.start() || timelineTime > s.start() + s.duration());
        if (shakes.isEmpty()) return sample;

        double norm = 1.0 / Math.sqrt(shakes.size());
        float yaw = 0, pitch = 0, roll = 0;
        for (var shake : shakes) {
            float elapsed = timelineTime - shake.start();
            float fade = 1 - elapsed / shake.duration();
            double phase = elapsed * shake.frequency() * Math.PI * 2;
            float amount = shake.intensity() * fade * fade;
            yaw += (float) Math.sin(phase) * amount;
            pitch += (float) Math.sin(phase * 1.31 + .7) * amount * .65f;
            roll += (float) Math.cos(phase * .83 + .2) * amount * .4f;
        }
        yaw *= (float) norm;
        pitch *= (float) norm;
        roll *= (float) norm;
        return new CameraSample(sample.position(), sample.yaw() + yaw, sample.pitch() + pitch,
                sample.roll() + roll, sample.fov(), sample.nearPlane(), sample.farPlane(),
                sample.depthOfFieldEnabled(), sample.focusDistance(), sample.aperture());
    }

    public void reset() { shakes.clear(); }

    private static void playSound(MinecraftClient client, Map<String, Object> params) {
        if (client == null || client.getSoundManager() == null) return;
        String raw = params.get("soundId") instanceof String text ? text : "";
        var id = Identifier.tryParse(raw);
        var event = id == null ? null : Registries.SOUND_EVENT.get(id);
        if (event != null) client.getSoundManager().play(PositionedSoundInstance.master(event,
                number(params, "pitch", 1), number(params, "volume", .8f)));
    }

    private static void spawnParticles(MinecraftClient client, Entity entity, Map<String, Object> params) {
        if (client == null || client.world == null || client.particleManager == null) return;
        String kind = params.get("particle") instanceof String text ? text : "POOF";
        var effect = AnimationEventParticles.get(kind).orElse(ParticleTypes.POOF);
        int count = params.get("count") instanceof Number n ? Math.max(1, Math.min(64, n.intValue())) : 8;
        float spread = number(params, "spread", .25f);
        float speed = number(params, "speed", .02f);
        var random = client.world.getRandom();
        for (int i = 0; i < count; i++) {
            client.particleManager.addParticle(effect,
                    entity.getX() + (random.nextDouble() * 2 - 1) * spread,
                    entity.getY() + entity.getHeight() * .65 + (random.nextDouble() * 2 - 1) * spread,
                    entity.getZ() + (random.nextDouble() * 2 - 1) * spread,
                    (random.nextDouble() * 2 - 1) * speed,
                    (random.nextDouble() * 2 - 1) * speed,
                    (random.nextDouble() * 2 - 1) * speed);
        }
    }

    private static float number(Map<String, Object> params, String key, float fallback) {
        return params.get(key) instanceof Number n && Float.isFinite(n.floatValue()) ? n.floatValue() : fallback;
    }
}
