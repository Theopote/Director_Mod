package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.sequence.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TimelineClipAnimationControlsTest {
    @Test void actionSwitchPreservesClipAudioAndModifiersButReplacesActionParameters() {
        var config = ClipAnimationConfig.defaultConfig(); var params = config.getParams();
        params.put("enterSoundTiming", "PRE_LAND"); params.put("exitSoundTiming", "START");
        params.put("enterSoundOffsetSeconds", -0.25f); params.put("exitSoundOffsetSeconds", 0.5f);
        params.put("opacityFade", "true"); params.put("opacityFadeIntensity", 0.75f);
        params.put("_legacyAdvancedParamsMigratedV1", true); params.put("oldActionDistance", 9);
        TimelineClipAnimationControls.applyActionDefaults(config, Map.of("initialScale", 0.2f), false);
        assertEquals("PRE_LAND", params.get("enterSoundTiming")); assertEquals("START", params.get("exitSoundTiming"));
        assertEquals(-0.25f, params.get("enterSoundOffsetSeconds")); assertEquals(0.5f, params.get("exitSoundOffsetSeconds"));
        assertEquals(true, params.get("opacityFade")); assertEquals(0.75f, params.get("opacityFadeIntensity"));
        assertEquals(true, params.get("_legacyAdvancedParamsMigratedV1"));
        assertFalse(params.containsKey("oldActionDistance")); assertEquals(0.2f, params.get("initialScale"));
    }
    @Test void resetActionPreservesAudioAndSequenceAndResetsOnlyActionModifiers() {
        var config = ClipAnimationConfig.defaultConfig(); var params = config.getParams();
        params.put("enterSoundTiming", "START"); params.put("exitSoundOffsetSeconds", 1.25f);
        params.put("opacityFade", true); params.put("opacityFadeIntensity", 0.5f);
        config.getSequenceParameters().setGlobalDelay(3); config.setDelay(2); config.setDuration(5);
        TimelineClipAnimationControls.applyActionDefaults(config, Map.of("height", 10f), true);
        assertEquals("START", params.get("enterSoundTiming")); assertEquals(1.25f, params.get("exitSoundOffsetSeconds"));
        assertEquals(false, params.get("opacityFade")); assertEquals(1f, params.get("opacityFadeIntensity"));
        assertEquals(3, config.getSequenceParameters().getGlobalDelay()); assertEquals(2, config.getDelay()); assertEquals(5, config.getDuration());
    }
    @Test void controlsRespectExplicitConcurrencyBeforeTimeMapping() {
        var params = new SequenceParameters();
        for (var concurrency : ConcurrencyMode.values()) for (var mapping : TimeMappingStrategy.values()) {
            params.setConcurrencyMode(concurrency); params.setTimeMappingStrategy(mapping);
            assertEquals(concurrency == ConcurrencyMode.ONE_BY_ONE, TimelineClipAnimationControls.usesTimeMapping(params));
            assertEquals(concurrency == ConcurrencyMode.IN_BATCHES || concurrency == ConcurrencyMode.ONE_BY_ONE
                    && mapping == TimeMappingStrategy.BATCH_MAPPING, TimelineClipAnimationControls.usesBatch(params));
            assertEquals(concurrency == ConcurrencyMode.ONE_BY_ONE && (mapping == TimeMappingStrategy.GROUP_FIRST
                    || mapping == TimeMappingStrategy.STAGGERED), TimelineClipAnimationControls.usesGroupGap(params));
        }
    }
}
