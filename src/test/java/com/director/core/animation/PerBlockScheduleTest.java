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
    @Test void startsUseClipLengthDividedByCountAndActionsOverlap() {
        var params = new SequenceParameters(); var config = config(params);
        var schedule = PerBlockSchedule.create(ordered(10), params, 2);
        assertEquals(1.8f, schedule.lastStartOffset(), 0.00001);
        for (int i = 0; i < 10; i++) {
            var w = window(config, schedule.entries().get(i), 2);
            assertEquals(i * 0.2f, w.start(), 0.00001);
            assertEquals(2, w.end()-w.start(), 0.00001);
        }
        for (int i = 0; i < 3; i++) {
            var e = schedule.entries().get(i);
            assertTrue(BlockAnimationEngine.sample(e.getPos(), null, config, e, 0, 2, 0.5f, 1).isVisible());
            assertFalse(BlockAnimationEngine.sample(e.getPos(), null, config, e, 0, 2, 0.5f, 1).isAnimationFinished());
        }
    }
    @Test void changingActionDurationDoesNotMoveAnyStartsOrResizeTheClip() {
        var container = new Container("blocks"); container.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(1,0,0)));
        var clip = new TimelineClip("blocks", 0, 2, 0); var config = clip.getAnimationConfig();
        config.setMode(AnimationMode.FADE_IN); config.setDuration(1);
        var first = BlockSequenceGenerator.generate(null, container, config.getSequenceParameters(), 2, 1, 0);
        config.setDuration(5);
        var second = BlockSequenceGenerator.generate(null, container, config.getSequenceParameters(), 2, 5, 0);
        assertEquals(2, clip.getLength());
        for (int i=0; i<2; i++) {
            assertEquals(first.get(i).getNormalizedTime(), second.get(i).getNormalizedTime());
            assertEquals(5, window(config, second.get(i), 2).end()-window(config, second.get(i), 2).start());
        }
    }
    @Test void batchAndLayerStartsDoNotWaitForPreviousActions() {
        var params = new SequenceParameters(); params.setConcurrencyMode(ConcurrencyMode.IN_BATCHES);
        params.setBatchSize(2); params.setBatchInterval(0.25f);
        var schedule = PerBlockSchedule.create(ordered(5), params, 2);
        var config = config(params);
        assertEquals(0.25f, window(config, schedule.entries().get(2), 2).start());
        assertEquals(2, window(config, schedule.entries().getFirst(), 2).end());
        params.setConcurrencyMode(ConcurrencyMode.PARALLEL_LAYERS);
        params.setTimeMappingStrategy(TimeMappingStrategy.BATCH_MAPPING);
        schedule = PerBlockSchedule.create(ordered(1,3), params, 3);
        assertEquals(2, schedule.entries().stream().filter(e -> e.getNormalizedTime()==0).count());
        assertEquals(2, schedule.lastStartOffset());
    }
    @Test void soundsRemainAtCompletionAfterTheClipEnd() {
        var container = new Container("blocks"); container.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(1,0,0), new BlockPos(2,0,0)));
        for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
            var clip = new TimelineClip("blocks", 10, 10.3f, 0); var config = clip.getAnimationConfig();
            config.setMode(AnimationMode.FADE_IN); config.setDuration(2); config.setPhase(phase);
            config.getParams().put(phase == ClipAnimationConfig.AnimationPhase.EXIT ? "exitSoundTiming" : "enterSoundTiming", "END");
            var entries = BlockSequenceGenerator.generate(null, container, config.getSequenceParameters(), clip.getLength(), 2, 0);
            var sounds = AnimationSoundCollector.getInstance().collectFromClip(clip, container, entries);
            assertEquals(12, sounds.getFirst().getStartTime(), 0.00001);
            assertEquals(12.2f, sounds.getLast().getStartTime(), 0.00001);
            config.setMode(AnimationMode.INSTANT);
            sounds = AnimationSoundCollector.getInstance().collectFromClip(clip, container, entries);
            assertEquals(10.2f, sounds.getLast().getStartTime(), 0.00001);
        }
    }
    @Test void allMappingsKeepIndependentDurationsAndStartsInsideTheTriggerSpan() {
        for (var concurrency : ConcurrencyMode.values()) for (var mapping : TimeMappingStrategy.values()) {
            var params = new SequenceParameters(); params.setConcurrencyMode(concurrency); params.setTimeMappingStrategy(mapping);
            params.setBatchSize(2); params.setBatchInterval(0.2f);
            var schedule = PerBlockSchedule.create(ordered(2,1,4), params, 1);
            var config = config(params); float previous=-1;
            for (var entry : schedule.entries()) {
                var w = window(config, entry, 1);
                assertTrue(w.start()>=previous); assertTrue(w.start()<1);
                assertEquals(2, w.end()-w.start(), 0.00001); previous=w.start();
            }
        }
    }
    @Test void timelineDurationIncludesIndependentTailWithoutChangingClipLength() {
        var model = new com.director.timeline.model.TimelineModel(new com.director.timeline.model.TimelineViewportState());
        var track = new com.director.timeline.model.TimelineTrack("blocks", com.director.timeline.model.TrackType.CONTAINER);
        model.addTrack(track); var clip = new TimelineClip("blocks", 10, 12, 0);
        clip.getAnimationConfig().setMode(AnimationMode.FADE_IN);
        clip.getAnimationConfig().setDuration(2); track.addClip(clip);
        clip.bindToContainer(java.util.UUID.randomUUID(), "blocks"); model.setDuration(0.1f);
        assertTrue(model.getDuration()>=14); assertEquals(2, clip.getLength());
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
        var schedule = PerBlockSchedule.create(BlockSequenceGenerator.generate(null, container, params), params, 1);
        assertEquals(upperNear, schedule.entries().getFirst().getPos());
    }
    @Test void tinyActionDurationSurvivesCopyAndGsonRoundTrip() {
        var config = config(new SequenceParameters()); config.setDuration(0.001f);
        assertEquals(0.001f, config.copy().getDuration());
        var gson = new com.google.gson.Gson();
        var restored = gson.fromJson(gson.toJson(config), ClipAnimationConfig.class);
        assertEquals(0.001f, restored.getDuration());
        assertEquals(0.001f, PerBlockAnimationTiming.layout(restored, 0, 1).duration(), 0.000001);
    }
}
