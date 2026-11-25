package com.director.actor.prop;

import com.director.actor.Actor;
import com.director.timeline.model.entity.EntityActionClip.ActionType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validation and capability vocabulary for events authored on actor animations. */
public final class AnimationEventParameters {
    /** Canonical particle ids; runtime maps these to ParticleEffect in {@link AnimationEventParticles}. */
    public static final List<String> PARTICLE_IDS = List.of(
            "CRIT", "POOF", "ENCHANT", "CLOUD", "ELECTRIC_SPARK", "END_ROD");
    public static final Set<String> PARTICLES = Set.copyOf(PARTICLE_IDS);

    private static final List<ActionType> SUPPORTED = List.of(
            ActionType.PLAY_SOUND, ActionType.SPAWN_PARTICLE, ActionType.CAMERA_SHAKE, ActionType.SET_VISIBILITY,
            ActionType.ATTACH_PROP, ActionType.DETACH_PROP, ActionType.SWITCH_PROP, ActionType.THROW_PROP);

    private AnimationEventParameters() {}

    public static List<ActionType> supported() { return SUPPORTED; }
    public static boolean supports(ActionType type) { return SUPPORTED.contains(type); }

    public static void validateActor(Actor actor, ActionType type, Map<String, Object> params) {
        if (!supports(type)) throw new IllegalArgumentException("此事件尚未接入演员动画");
        PropEventParameters.copy(params, false);
        if (PropEventParameters.supports(type)) {
            PropEventParameters.validateActor(actor, type, params);
            return;
        }
        switch (type) {
            case PLAY_SOUND -> {
                identifier(params, "soundId");
                range(params, "volume", 0, 4, .8f);
                range(params, "pitch", .1f, 4, 1f);
            }
            case SPAWN_PARTICLE -> {
                String kind = text(params, "particle", "POOF");
                if (!PARTICLES.contains(kind)) throw new IllegalArgumentException("不支持的粒子类型：" + kind);
                integer(params, "count", 1, 64, 8);
                range(params, "spread", 0, 4, .25f);
                range(params, "speed", 0, 8, .02f);
            }
            case CAMERA_SHAKE -> {
                range(params, "intensity", .001f, 2, .25f);
                range(params, "duration", .01f, 10, .35f);
                range(params, "frequency", .1f, 30, 14f);
            }
            case SET_VISIBILITY -> {
                if (!(params.get("visible") instanceof Boolean))
                    throw new IllegalArgumentException("显隐事件需要布尔 visible 参数");
            }
            default -> throw new IllegalArgumentException("不支持的动画事件：" + type);
        }
    }

    private static String text(Map<String, Object> params, String key, String fallback) {
        Object value = params.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String string) || string.isBlank()) throw new IllegalArgumentException("参数不能为空：" + key);
        return string;
    }

    private static void identifier(Map<String, Object> params, String key) {
        String value = text(params, key, "");
        if (net.minecraft.util.Identifier.tryParse(value) == null)
            throw new IllegalArgumentException("声音 ID 格式无效：" + value);
    }

    private static float range(Map<String, Object> params, String key, float min, float max, float fallback) {
        Object raw = params.get(key);
        if (raw == null) return fallback;
        float value = (float) PropEventParameters.number(params, key);
        if (value < min || value > max) throw new IllegalArgumentException(key + " 范围必须为 " + min + "–" + max);
        return value;
    }

    private static int integer(Map<String, Object> params, String key, int min, int max, int fallback) {
        Object raw = params.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) throw new IllegalArgumentException("参数必须为整数：" + key);
        double value = number.doubleValue();
        int result = number.intValue();
        if (!Double.isFinite(value) || value != result || result < min || result > max)
            throw new IllegalArgumentException(key + " 范围必须为 " + min + "–" + max);
        return result;
    }
}
