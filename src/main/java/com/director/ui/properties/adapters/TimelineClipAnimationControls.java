package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.sequence.ConcurrencyMode;
import com.director.core.animation.sequence.SequenceParameters;
import com.director.core.animation.sequence.TimeMappingStrategy;
import java.util.HashMap;
import java.util.Map;

/** Property controls follow the same precedence as PerBlockSchedule. */
final class TimelineClipAnimationControls {
    private TimelineClipAnimationControls() {}

    static boolean usesTimeMapping(SequenceParameters params) {
        return params.getConcurrencyMode() == ConcurrencyMode.ONE_BY_ONE;
    }
    static boolean usesBatch(SequenceParameters params) {
        return params.getConcurrencyMode() == ConcurrencyMode.IN_BATCHES
                || usesTimeMapping(params) && params.getTimeMappingStrategy() == TimeMappingStrategy.BATCH_MAPPING;
    }
    static boolean usesGroupGap(SequenceParameters params) {
        return usesTimeMapping(params) && (params.getTimeMappingStrategy() == TimeMappingStrategy.GROUP_FIRST
                || params.getTimeMappingStrategy() == TimeMappingStrategy.STAGGERED);
    }

    /** Sound timing belongs to the clip, so resetting an action must not reset its audio. */
    static void applyActionDefaults(ClipAnimationConfig config, Map<String, Object> defaults, boolean resetModifiers) {
        var params = config.getParams();
        var retained = new HashMap<String, Object>();
        for (String key : new String[]{"enterSoundTiming", "exitSoundTiming", "enterSoundOffsetSeconds",
                "exitSoundOffsetSeconds", "_legacyAdvancedParamsMigratedV1"})
            if (params.containsKey(key)) retained.put(key, params.get(key));
        boolean fade = Boolean.TRUE.equals(params.get("opacityFade"))
                || params.get("opacityFade") instanceof String value && Boolean.parseBoolean(value);
        Object intensity = params.getOrDefault("opacityFadeIntensity", 1.0f);
        params.clear();
        params.putAll(defaults);
        params.putAll(retained);
        params.put("opacityFade", !resetModifiers && fade);
        params.put("opacityFadeIntensity", resetModifiers ? 1.0f : intensity);
    }
}
