package com.director.core.animation;

import com.director.core.animation.easing.EasingFunction;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.container.Container;
import com.director.core.container.AnimationMode;
import com.director.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationSoundTest {
    private TimelineClip clip(boolean exit) {
        var clip = new TimelineClip("Assembly", 10, 20, 0);
        var config = clip.getAnimationConfig();
        config.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        config.setPhase(exit ? ClipAnimationConfig.AnimationPhase.EXIT : ClipAnimationConfig.AnimationPhase.ENTER);
        config.setDuration(2); config.setDelay(1);
        return clip;
    }
    private String prefix(boolean exit) { return exit ? "exit" : "enter"; }
    @Test void defaultSoundMatchesAtomicHandoffAndIgnoresPerBlockModeAndDelay() {
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit);
            clip.getAnimationConfig().setMode(AnimationMode.INSTANT);
            clip.getAnimationConfig().getSequenceParameters().setGlobalDelay(100);
            assertEquals(13, AnimationSoundCollector.wholeContainerEventTime(clip));
            var before = WholeContainerAnimation.sample(clip.getAnimationConfig(), 10, 20, 12.99f, exit);
            var after = WholeContainerAnimation.sample(clip.getAnimationConfig(), 10, 20, 13, exit);
            assertTrue(before.animating()); assertFalse(after.animating());
            assertEquals(!exit, after.worldVisible());
        }
    }
    @Test void allTimingChoicesWorkForEnterAndExit() {
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit);
            var params = clip.getAnimationConfig().getParams();
            for (String timing : List.of("START", "END", "PRE_LAND", "IMPACT")) {
                params.put(prefix(exit) + "SoundTiming", timing);
                float expected = switch (timing) {
                    case "START" -> 11;
                    case "PRE_LAND" -> 13 - 1.0f / 60;
                    default -> 13;
                };
                assertEquals(expected, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
            }
        }
    }
    @Test void impactUsesFirstArrivalOnTheEffectiveWindowOfOvershootingAnimations() {
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit); clip.setEndTime(12);
            var config = clip.getAnimationConfig();
            config.setEasing("EASE_OUT_BACK");
            config.getParams().put(prefix(exit) + "SoundTiming", "IMPACT");
            float time = AnimationSoundCollector.wholeContainerEventTime(clip);
            assertTrue(time > 11 && time < 12);
            assertTrue(EasingFunction.EASE_OUT_BACK.apply(time - 11) >= 0.9995f);
            assertTrue(EasingFunction.EASE_OUT_BACK.apply(time - 11 - 0.005f) < 0.9995f);
            var frame = WholeContainerAnimation.sample(config, 10, 12, time, exit);
            assertTrue(frame.animating());
            assertEquals(time - 11, frame.progress(), 0.0001f);
        }
    }
    @Test void durationChangesAndClipBoundsKeepSoundInSync() {
        var clip = clip(false);
        clip.getAnimationConfig().setDuration(1);
        assertEquals(12, AnimationSoundCollector.wholeContainerEventTime(clip));
        clip.getAnimationConfig().setDuration(10); clip.setEndTime(11.5f);
        assertEquals(11.5f, AnimationSoundCollector.wholeContainerEventTime(clip));
        clip.getAnimationConfig().getParams().put("enterSoundTiming", "PRE_LAND");
        assertEquals(11.5f - 1.0f / 60, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
        clip.getAnimationConfig().setDuration(0.05f); clip.setEndTime(11.01f);
        assertEquals(11, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
    }
    @Test void excessDelayCollapsesEveryTimingChoiceToTheClipBoundary() {
        var clip = clip(false); clip.setEndTime(12); clip.getAnimationConfig().setDelay(20);
        for (String timing : List.of("START", "END", "IMPACT", "PRE_LAND")) {
            clip.getAnimationConfig().getParams().put("enterSoundTiming", timing);
            assertEquals(12, AnimationSoundCollector.wholeContainerEventTime(clip));
        }
    }
    @Test void phaseSpecificOffsetsSupportEarlyAndLateSoundsWithoutNegativeGlobalTime() {
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit); clip.setEndTime(13);
            var params = clip.getAnimationConfig().getParams();
            params.put(prefix(!exit) + "SoundOffsetSeconds", 99);
            params.put(prefix(exit) + "SoundOffsetSeconds", "0.5");
            assertEquals(13.5f, AnimationSoundCollector.wholeContainerEventTime(clip));
            params.put(prefix(exit) + "SoundOffsetSeconds", -0.25f);
            assertEquals(12.75f, AnimationSoundCollector.wholeContainerEventTime(clip));
            params.put(prefix(exit) + "SoundOffsetSeconds", -100);
            assertEquals(0, AnimationSoundCollector.wholeContainerEventTime(clip));
        }
    }
    @Test void invalidOffsetsAndUnknownTimingsHaveStableDefaults() {
        var clip = clip(false); var params = clip.getAnimationConfig().getParams();
        for (Object offset : List.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, "invalid")) {
            params.put("enterSoundOffsetSeconds", offset);
            assertEquals(13, AnimationSoundCollector.wholeContainerEventTime(clip));
        }
        params.remove("enterSoundOffsetSeconds");
        params.put("enterSoundTiming", "future-setting");
        assertEquals(13, AnimationSoundCollector.wholeContainerEventTime(clip));
        params.put("enterSoundTiming", "start");
        assertEquals(11, AnimationSoundCollector.wholeContainerEventTime(clip));
    }
    @Test void assemblyGeneratesOneSoundWithoutRequiringPerBlockSequenceEntries() {
        var container = new Container("Large assembly");
        container.addBlocks(IntStream.range(0, 1000).mapToObj(x -> new BlockPos(x, 0, 0)).toList());
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit);
            clip.getAnimationConfig().getParams().put(prefix(exit) + "SoundTiming", "START");
            clip.getAnimationConfig().getParams().put(prefix(exit) + "SoundOffsetSeconds", 0.25f);
            var events = AnimationSoundCollector.getInstance().collectFromClip(clip, container, null);
            assertEquals(1, events.size());
            assertEquals(11.25f, events.getFirst().getStartTime());
            assertEquals(exit ? "minecraft:block.stone.break" : "minecraft:block.stone.place", events.getFirst().getSoundId());
        }
    }
    @Test void everyWholeEffectUsesTheSameSoundWindow() {
        var clip = clip(false);
        for (var effect : WholeContainerAnimationConfig.Effect.values()) {
            clip.getAnimationConfig().getWholeContainer().setEffect(effect);
            assertEquals(13, AnimationSoundCollector.wholeContainerEventTime(clip));
        }
    }
    @Test void perBlockScopeStillGeneratesSeparateSequencedSounds() {
        var container = new Container("Sequence");
        var first = BlockPos.ORIGIN; var second = new BlockPos(1, 0, 0);
        container.addBlocks(List.of(first, second));
        var clip = clip(false);
        clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        clip.getAnimationConfig().setMode(AnimationMode.FADE_IN);
        clip.getAnimationConfig().getParams().put("enterSoundOffsetSeconds", 0.25f);
        var entries = List.of(new BlockSequenceEntry(first, 0, 0, 0),
                new BlockSequenceEntry(second, 1, 0, 1));
        var events = AnimationSoundCollector.getInstance().collectFromClip(clip, container, entries);
        assertEquals(2, events.size());
        assertEquals(13.25f, events.getFirst().getStartTime());
        assertEquals(20.25f, events.getLast().getStartTime());
    }

    @Test void emptyAssemblyDoesNotGenerateASound() {
        assertTrue(AnimationSoundCollector.getInstance().collectFromClip(clip(false), new Container("Empty"), List.of()).isEmpty());
    }
}
