package com.director.actor.runtime;

import com.director.timeline.playback.camera.CameraRuntime;
import com.director.timeline.playback.camera.CameraSample;
import com.director.timeline.playback.entity.EntityActionEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Client-side one-shot effects shared by editor preview and offline timeline sampling. */
public final class ActorAnimationEventEffects {
    private static final ActorAnimationEventEffects INSTANCE = new ActorAnimationEventEffects();
    private Shake shake;
    private record Shake(float start, float duration, float intensity, float frequency) {}

    private ActorAnimationEventEffects() {}
    public static ActorAnimationEventEffects getInstance() { return INSTANCE; }

    public void dispatch(Entity entity, EntityActionEvent event, float timelineTime) {
        if (entity == null || event == null || event.type() == null) return;
        var client = MinecraftClient.getInstance();
        switch (event.type()) {
            case PLAY_SOUND -> playSound(client, event.params());
            case SPAWN_PARTICLE -> spawnParticles(client, entity, event.params());
            case CAMERA_SHAKE -> {
                shake = new Shake(timelineTime, number(event.params(), "duration", .35f),
                        number(event.params(), "intensity", .25f), number(event.params(), "frequency", 14f));
                applyCurrent(timelineTime);
            }
            case SET_VISIBILITY -> { }
            default -> { }
        }
    }

    public CameraSample decorate(CameraSample sample, float timelineTime) {
        if (sample == null) return null;
        if (shake == null) return sample;
        float elapsed = timelineTime - shake.start();
        if (elapsed < 0 || elapsed > shake.duration()) {
            if (elapsed > shake.duration() || elapsed < 0) shake = null;
            return sample;
        }
        float fade = 1 - elapsed / shake.duration();
        double phase = elapsed * shake.frequency() * Math.PI * 2;
        float amount = shake.intensity() * fade * fade;
        float yaw = (float) Math.sin(phase) * amount;
        float pitch = (float) Math.sin(phase * 1.31 + .7) * amount * .65f;
        float roll = (float) Math.cos(phase * .83 + .2) * amount * .4f;
        return new CameraSample(sample.position(), sample.yaw() + yaw, sample.pitch() + pitch,
                sample.roll() + roll, sample.fov(), sample.nearPlane(), sample.farPlane(),
                sample.depthOfFieldEnabled(), sample.focusDistance(), sample.aperture());
    }

    private void applyCurrent(float timelineTime) {
        var runtime = CameraRuntime.getInstance();
        var current = runtime.getCurrentSample();
        if (current == null) return;
        var decorated = decorate(current, timelineTime);
        if (decorated != current) runtime.applyTimelineSample(decorated);
    }

    public void reset() { shake = null; }

    private static void playSound(MinecraftClient client, Map<String, Object> params) {
        String raw = params.get("soundId") instanceof String text ? text : "";
        var id = Identifier.tryParse(raw);
        var event = id == null ? null : Registries.SOUND_EVENT.get(id);
        if (event != null) client.getSoundManager().play(PositionedSoundInstance.master(event,
                number(params, "pitch", 1), number(params, "volume", .8f)));
    }

    private static void spawnParticles(MinecraftClient client, Entity entity, Map<String, Object> params) {
        if (client.world == null || client.particleManager == null) return;
        String kind = params.get("particle") instanceof String text ? text : "POOF";
        ParticleEffect effect = switch (kind) {
            case "CRIT" -> ParticleTypes.CRIT;
            case "ENCHANT" -> ParticleTypes.ENCHANT;
            case "CLOUD" -> ParticleTypes.CLOUD;
            case "ELECTRIC_SPARK" -> ParticleTypes.ELECTRIC_SPARK;
            case "END_ROD" -> ParticleTypes.END_ROD;
            default -> ParticleTypes.POOF;
        };
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
