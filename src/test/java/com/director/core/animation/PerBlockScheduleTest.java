package com.director.core.animation;

import com.director.core.animation.sequence.*;
import com.director.core.container.AnimationMode;
import com.director.core.container.Container;
import com.director.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PerBlockScheduleTest {
    private List<BlockSequenceEntry> ordered(int... groupSizes) {
        List<BlockSequenceEntry> entries = new ArrayList<>();
        for (int g = 0; g < groupSizes.length; g++)
            for (int i = 0; i < groupSizes[g]; i++)
                entries.add(new BlockSequenceEntry(new BlockPos(entries.size(), g, 0), 0, g, 0));
        return entries;
    }
    private ClipAnimationConfig config(SequenceParameters params) {
        var config = ClipAnimationConfig.defaultConfig();
        config.setMode(AnimationMode.FADE_IN); config.setDuration(2); config.setDelay(0);
        config.setSequenceParameters(params);
        return config;
    }
    private PerBlockAnimationTiming.Window window(ClipAnimationConfig config, BlockSequenceEntry entry, float end) {
        return PerBlockAnimationTiming.layout(config, 0, end, entry).window(entry.getNormalizedTime());
    }
    @Test void serialLengthIsSumAndResizingSpeedsUpEveryCompleteAction() {
        var params = new SequenceParameters(); var config = config(params);
        var schedule = PerBlockSchedule.create(ordered(3), params, 2, false);
        assertEquals(6, schedule.duration());
        for (int i = 0; i < 3; i++) {
            var entry = schedule.entries().get(i);
            var normal = window(config, entry, 6); var fast = window(config, entry, 3);
            assertEquals(i * 2, normal.start(), 0.00001); assertEquals(i * 2 + 2, normal.end(), 0.00001);
            assertEquals(i, fast.start(), 0.00001); assertEquals(i + 1, fast.end(), 0.00001);
            var sample = BlockAnimationEngine.sample(entry.getPos(), null, config, entry, 0, 3, i + 0.5f, 42);
            assertEquals(0.5f, sample.getRawProgress(), 0.00001);
        }
    }
    @Test void batchesWaitForThePreviousBatchToFinishThenApplyGap() {
        var params = new SequenceParameters(); params.setConcurrencyMode(ConcurrencyMode.IN_BATCHES);
        params.setBatchSize(2); params.setBatchInterval(0.5f);
        var config = config(params); var schedule = PerBlockSchedule.create(ordered(5), params, 2, false);
        assertEquals(7, schedule.duration());
        for (int i = 0; i < 5; i++)
            assertEquals((i / 2) * 2.5f, window(config, schedule.entries().get(i), 7).start(), 0.00001);
        assertEquals(7, window(config, schedule.entries().getLast(), 7).end());
    }
    @Test void parallelLayersAccumulateIndependentlyUsingTheLongestLayer() {
        var params = new SequenceParameters(); params.setConcurrencyMode(ConcurrencyMode.PARALLEL_LAYERS);
        // Explicit layer concurrency takes priority over an old saved batch mapping.
        params.setTimeMappingStrategy(TimeMappingStrategy.BATCH_MAPPING);
        var schedule = PerBlockSchedule.create(ordered(1, 3), params, 2, false);
        assertEquals(6, schedule.duration());
        var config = config(params);
        assertEquals(2, schedule.entries().stream().filter(e -> window(config, e, 6).start() == 0).count());
        assertEquals(2, window(config, schedule.entries().stream().filter(e -> e.getGroupId() == 0).findFirst().orElseThrow(), 6).end());
    }
    @Test void unequalGroupsFinishBeforeNextGroupStartsAndStaggerReversesOnlyLocalOrder() {
        for (var mapping : List.of(TimeMappingStrategy.GROUP_FIRST, TimeMappingStrategy.STAGGERED)) {
            var params = new SequenceParameters(); params.setTimeMappingStrategy(mapping); params.setBatchInterval(0.5f);
            var schedule = PerBlockSchedule.create(ordered(1, 3), params, 2, false);
            assertEquals(8.5f, schedule.duration());
            var config = config(params);
            var next = schedule.entries().get(1);
            assertEquals(2.5f, window(config, next, 8.5f).start(), 0.00001);
            assertEquals(mapping == TimeMappingStrategy.STAGGERED ? 3 : 1, next.getPos().getX());
        }
    }
    @Test void simultaneousBlocksAreStillSeparateActionsSharingAWindow() {
        var params = new SequenceParameters(); params.setConcurrencyMode(ConcurrencyMode.ALL_AT_ONCE);
        var schedule = PerBlockSchedule.create(ordered(3), params, 2, false);
        assertEquals(2, schedule.duration());
        for (var entry : schedule.entries()) {
            assertEquals(0, window(config(params), entry, 2).start());
            assertEquals(2, window(config(params), entry, 2).end());
        }
    }
    @Test void instantRetainsSequenceAndDoesNotUseTransformDuration() {
        var params = new SequenceParameters(); var config = config(params); config.setMode(AnimationMode.INSTANT);
        var schedule = PerBlockSchedule.create(ordered(3), params, 2, true);
        assertEquals(4, schedule.duration());
        for (int i = 0; i < 3; i++) {
            var w = window(config, schedule.entries().get(i), 4);
            assertEquals(i * 2, w.start()); assertEquals(w.start(), w.end());
        }
    }
    @Test void delayIsAppliedOnceAndBothPhasesShareTheSameCompleteWindows() {
        var params = new SequenceParameters(); params.setGlobalDelay(0.5f);
        var config = config(params); config.setDelay(0.5f);
        var schedule = PerBlockSchedule.create(ordered(3), params, 2, false);
        for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
            config.setPhase(phase);
            for (int i = 0; i < 3; i++) {
                var entry = schedule.entries().get(i); var w = window(config, entry, 4);
                assertEquals(i + 1, w.start(), 0.00001); assertEquals(i + 2, w.end(), 0.00001);
                assertEquals(phase == ClipAnimationConfig.AnimationPhase.EXIT,
                        BlockAnimationEngine.sample(entry.getPos(), null, config, entry, 0, 4, w.start() - 0.1f, 1).isVisible());
                assertEquals(phase != ClipAnimationConfig.AnimationPhase.EXIT,
                        BlockAnimationEngine.sample(entry.getPos(), null, config, entry, 0, 4, w.end(), 1).isVisible());
            }
        }
    }
    @Test void generatorAudioAndSamplingAgreeIncludingShortClipsAndInstantMode() {
        var container = new Container("blocks");
        container.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
        for (var mode : List.of(AnimationMode.FADE_IN, AnimationMode.INSTANT))
            for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
                var clip = new TimelineClip("blocks", 10, 10.3f, 0); var config = clip.getAnimationConfig();
                config.setDuration(2); config.setDelay(0); config.setMode(mode); config.setPhase(phase);
                config.getParams().put(phase == ClipAnimationConfig.AnimationPhase.EXIT ? "exitSoundTiming" : "enterSoundTiming", "END");
                var entries = BlockSequenceGenerator.generate(null, container, config.getSequenceParameters(), clip.getLength(), config.getDuration(), 0, mode == AnimationMode.INSTANT);
                var sounds = AnimationSoundCollector.getInstance().collectFromClip(clip, container, entries);
                for (int i = 0; i < entries.size(); i++) {
                    var e = entries.get(i); var timing = PerBlockAnimationTiming.layout(config, clip.getStartTime(), clip.getEndTime(), e);
                    var w = timing.window(e.getNormalizedTime());
                    assertEquals(w.end(), sounds.get(i).getStartTime(), 0.00001);
                    assertEquals(mode == AnimationMode.INSTANT ? 0 : clip.getLength()/3, w.end() - w.start(), 0.00001);
                }
                assertEquals(clip.getEndTime(), sounds.getLast().getStartTime(), 0.00001);
            }
    }
    @Test void persistedDurationOverrideAndEmptySelectionsAreHandled() {
        var params = new SequenceParameters(); params.setPerBlockDuration(0.25f);
        assertEquals(0.75f, PerBlockSchedule.create(ordered(3), params, 2, false).duration());
        assertTrue(PerBlockSchedule.create(List.of(), params, 2, false).entries().isEmpty());
    }
    @Test void reverseReversesLayerOrderAndLowWeightAllowsInterleaving() {
        var container = new Container("layers");
        var lowerFar = new BlockPos(10, 0, 0); var upperNear = new BlockPos(0, 1, 0);
        container.addBlocks(List.of(lowerFar, upperNear));
        var params = new SequenceParameters(); params.setPriorityWeight(0);
        assertEquals(upperNear, BlockSequenceGenerator.generate(null, container, params).getFirst().getPos());
        params.setPriorityWeight(20);
        assertEquals(lowerFar, BlockSequenceGenerator.generate(null, container, params).getFirst().getPos());
        params.setReverse(true);
        assertEquals(upperNear, BlockSequenceGenerator.generate(null, container, params).getFirst().getPos());
        params.setTimeMappingStrategy(TimeMappingStrategy.GROUP_FIRST);
        var schedule = PerBlockSchedule.create(BlockSequenceGenerator.generate(null, container, params), params, 1, false);
        assertEquals(upperNear, schedule.entries().getFirst().getPos());
    }
    @Test void tinyActionDurationSurvivesCopyAndGsonRoundTrip() {
        var config = config(new SequenceParameters()); config.setDuration(0.001f);
        assertEquals(0.001f, config.copy().getDuration());
        var gson = new com.google.gson.Gson();
        var restored = gson.fromJson(gson.toJson(config), ClipAnimationConfig.class);
        assertEquals(0.001f, restored.getDuration());
        assertEquals(0.003f, PerBlockSchedule.create(ordered(3), restored.getSequenceParameters(), restored.getDuration(), false).duration(), 0.000001);
    }
    @Test void allConcurrencyAndMappingCombinationsProduceSortedBoundedFullWindows() {
        for (var concurrency : ConcurrencyMode.values()) for (var mapping : TimeMappingStrategy.values()) {
            var params = new SequenceParameters(); params.setConcurrencyMode(concurrency); params.setTimeMappingStrategy(mapping);
            params.setBatchSize(2); params.setBatchInterval(0.2f);
            var schedule = PerBlockSchedule.create(ordered(2, 1, 4), params, 0.3f, false);
            var config = config(params); float lastStart = -1, maxEnd = 0;
            for (var entry : schedule.entries()) {
                var w = window(config, entry, schedule.duration());
                assertTrue(w.start() >= lastStart); assertTrue(w.end() <= schedule.duration() + 0.00001);
                assertEquals(0.3f, w.end() - w.start(), 0.00001);
                lastStart = w.start(); maxEnd = Math.max(maxEnd, w.end());
            }
            assertEquals(schedule.duration(), maxEnd, 0.00001);
        }
    }
}
