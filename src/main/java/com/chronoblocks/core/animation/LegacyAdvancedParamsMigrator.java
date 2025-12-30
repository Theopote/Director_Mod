package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;

import java.util.List;
import java.util.Map;

/**
 * 旧版“高级修饰参数”兼容迁移：
 *
 * <p>历史上 {@link ClipAnimationConfig#getParams()} 中存在一些 UI 开关（例如 flashWhite），
 * 但随着特效系统引入，这类参数应当迁移到 {@link ClipAnimationConfig#getEffects()}。
 *
 * <p>本迁移器会在运行时/编辑器中执行一次，把旧字段转换成等价的 EffectInstance，
 * 以保证旧存档/旧片段的行为不丢失。
 */
public final class LegacyAdvancedParamsMigrator {

    private static final String KEY_DONE = "_legacyAdvancedParamsMigratedV1";

    private LegacyAdvancedParamsMigrator() {
    }

    public static void migrateIfNeeded(ClipAnimationConfig config) {
        if (config == null) return;
        Map<String, Object> params = config.getParams();
        if (Boolean.TRUE.equals(params.get(KEY_DONE))) return;

        boolean migrated = false;

        // ========== 1) flashWhite -> EffectType.FLASH_WHITE ==========
        boolean flashWhite = getBoolean(params.get("flashWhite"));
        if (flashWhite) {
            // 确保存在一个 FLASH_WHITE 特效
            EffectInstance inst = findFirstEffect(config.getEffects());
            if (inst == null) {
                inst = new EffectInstance(EffectType.FLASH_WHITE);
                inst.setEnabled(true);
                inst.setPhase(EffectInstance.EffectPhase.ENTER);
                inst.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
                inst.setStartOffset(0.0f);
                inst.setDuration(0.30f); // 单块动画前 30%
                config.addEffect(inst);
            }

            float intensity = getFloat(params.get("flashWhiteIntensity"));
            inst.getParams().putIfAbsent("intensity", intensity);

            // 清理旧字段（避免 UI/用户困惑）
            params.remove("flashWhite");
            params.remove("flashWhiteIntensity");
            migrated = true;
        }

        if (migrated) {
            params.put(KEY_DONE, true);
        }
    }

    private static EffectInstance findFirstEffect(List<EffectInstance> effects) {
        if (effects == null || effects.isEmpty()) return null;
        for (EffectInstance e : effects) {
            if (e != null && e.getType() == EffectType.FLASH_WHITE) {
                return e;
            }
        }
        return null;
    }

    private static boolean getBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private static float getFloat(Object v) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return (float) 1.0;
    }
}


