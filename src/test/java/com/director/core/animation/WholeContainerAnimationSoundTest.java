package com.director.core.animation;

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
    @Test void oneSoundPrecedesAtomicWorldHandoffForEveryEffectAndDirection() {
        for (boolean exit : new boolean[] {false, true}) {
            var clip = clip(exit);
            for (var effect : WholeContainerAnimationConfig.Effect.values()) {
                var config = clip.getAnimationConfig();
                config.getWholeContainer().setEffect(effect);
                config.getSequenceParameters().setGlobalDelay(100);
                float soundTime = AnimationSoundCollector.wholeContainerEventTime(clip);
                assertEquals(13 - 1.0f / 60, soundTime, 0.0001f);
                assertTrue(WholeContainerAnimation.sample(config, 10, 20, soundTime, exit).animating());
                var after = WholeContainerAnimation.sample(config, 10, 20, 13, exit);
                assertFalse(after.animating()); assertEquals(!exit, after.worldVisible());
            }
        }
    }
    @Test void oldPerBlockSoundChoicesOffsetsAndOvershootDoNotChangeWholeSoundTiming() {
        var clip = clip(false); var config = clip.getAnimationConfig();
        config.setEasing("EASE_OUT_BACK");
        for (String timing : List.of("START", "END", "PRE_LAND", "IMPACT")) {
            config.getParams().put("enterSoundTiming", timing);
            config.getParams().put("enterSoundOffsetSeconds", 100);
            assertEquals(13 - 1.0f / 60, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
        }
        assertEquals(100, config.getParams().get("enterSoundOffsetSeconds"));
    }
    @Test void durationEditsAndShortClipsKeepSoundBeforeHandoffWithoutPrecedingAnimationStart() {
        var clip = clip(false);
        clip.getAnimationConfig().setDuration(1);
        assertEquals(12 - 1.0f / 60, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
        clip.getAnimationConfig().setDuration(10); clip.setEndTime(11.5f);
        assertEquals(11.5f - 1.0f / 60, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
        clip.getAnimationConfig().setDuration(0.05f); clip.setEndTime(11.01f);
        assertEquals(11, AnimationSoundCollector.wholeContainerEventTime(clip), 0.0001f);
    }
    @Test void excessDelayProducesSoundAtInstantaneousHandoff() {
        var clip = clip(false); clip.setEndTime(12); clip.getAnimationConfig().setDelay(20);
        assertEquals(12, AnimationSoundCollector.wholeContainerEventTime(clip));
    }
    @Test void largeAssemblyGeneratesOnlyOneSoundWithoutSequenceEntries() {
        var container = new Container("Large assembly");
        container.addBlocks(IntStream.range(0, 1000).mapToObj(x -> new BlockPos(x, 0, 0)).toList());
        for (boolean exit : new boolean[] {false, true}) {
            var events = AnimationSoundCollector.getInstance().collectFromClip(clip(exit), container, null);
            assertEquals(1, events.size());
            assertEquals(13 - 1.0f / 60, events.getFirst().getStartTime(), 0.0001f);
            assertEquals(exit ? "minecraft:block.stone.break" : "minecraft:block.stone.place", events.getFirst().getSoundId());
        }
    }
    @Test void perBlockScopeStillUsesIndividualTimingAndOffsetSettings() {
        var container = new Container("Sequence");
        var first = BlockPos.ORIGIN; var second = new BlockPos(1, 0, 0);
        container.addBlocks(List.of(first, second));
        var clip = clip(false); var config = clip.getAnimationConfig();
        config.setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK); config.setMode(AnimationMode.FADE_IN);
        config.getParams().put("enterSoundOffsetSeconds", 0.25f);
        var entries = List.of(new BlockSequenceEntry(first, 0, 0, 0), new BlockSequenceEntry(second, 1, 0, 1));
        var events = AnimationSoundCollector.getInstance().collectFromClip(clip, container, entries);
        assertEquals(2, events.size());
        assertEquals(13.25f, events.getFirst().getStartTime()); assertEquals(20.25f, events.getLast().getStartTime());
    }
    @Test void emptyAssemblyDoesNotGenerateASound() {
        assertTrue(AnimationSoundCollector.getInstance().collectFromClip(clip(false), new Container("Empty"), List.of()).isEmpty());
    }
}
