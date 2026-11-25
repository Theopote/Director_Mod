package com.director.actor.prop;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single runtime mapping from {@link AnimationEventParameters#PARTICLE_IDS} to ParticleEffect.
 * Kept separate so validation unit tests do not bootstrap particle registries.
 */
public final class AnimationEventParticles {
    private static final Map<String, ParticleEffect> BY_ID = build();

    private AnimationEventParticles() {}

    public static Optional<ParticleEffect> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static Map<String, ParticleEffect> build() {
        var map = new LinkedHashMap<String, ParticleEffect>();
        for (String id : AnimationEventParameters.PARTICLE_IDS) {
            ParticleEffect effect = switch (id) {
                case "CRIT" -> ParticleTypes.CRIT;
                case "POOF" -> ParticleTypes.POOF;
                case "ENCHANT" -> ParticleTypes.ENCHANT;
                case "CLOUD" -> ParticleTypes.CLOUD;
                case "ELECTRIC_SPARK" -> ParticleTypes.ELECTRIC_SPARK;
                case "END_ROD" -> ParticleTypes.END_ROD;
                default -> throw new IllegalStateException("PARTICLE_IDS missing runtime mapping: " + id);
            };
            map.put(id, effect);
        }
        return Map.copyOf(map);
    }
}
