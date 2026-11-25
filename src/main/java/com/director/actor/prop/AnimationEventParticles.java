package com.director.actor.prop;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single runtime mapping from {@link AnimationEventParameters#PARTICLE_IDS} to ParticleEffect.
 * String id coverage is testable without bootstrapping particle registries; effect resolution is lazy.
 */
public final class AnimationEventParticles {
    /** Must stay in lockstep with the switch in {@link #resolve(String)}. */
    private static final List<String> MAPPED_IDS = List.of(
            "CRIT", "POOF", "ENCHANT", "CLOUD", "ELECTRIC_SPARK", "END_ROD");

    private static volatile Map<String, ParticleEffect> byId;

    private AnimationEventParticles() {}

    /** Ids the runtime switch knows about; used by unit tests without loading ParticleTypes. */
    public static Set<String> mappedIds() {
        return Set.copyOf(MAPPED_IDS);
    }

    public static Optional<ParticleEffect> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(map().get(id));
    }

    private static Map<String, ParticleEffect> map() {
        Map<String, ParticleEffect> local = byId;
        if (local != null) return local;
        synchronized (AnimationEventParticles.class) {
            if (byId == null) byId = build();
            return byId;
        }
    }

    private static Map<String, ParticleEffect> build() {
        var map = new LinkedHashMap<String, ParticleEffect>();
        for (String id : AnimationEventParameters.PARTICLE_IDS) {
            map.put(id, resolve(id));
        }
        return Map.copyOf(map);
    }

    private static ParticleEffect resolve(String id) {
        return switch (id) {
            case "CRIT" -> ParticleTypes.CRIT;
            case "POOF" -> ParticleTypes.POOF;
            case "ENCHANT" -> ParticleTypes.ENCHANT;
            case "CLOUD" -> ParticleTypes.CLOUD;
            case "ELECTRIC_SPARK" -> ParticleTypes.ELECTRIC_SPARK;
            case "END_ROD" -> ParticleTypes.END_ROD;
            default -> throw new IllegalStateException("PARTICLE_IDS missing runtime mapping: " + id);
        };
    }
}
