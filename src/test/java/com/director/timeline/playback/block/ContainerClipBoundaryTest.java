package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ContainerClipBoundaryTest {
    @Test void movingTimesWithinOneSequenceBoundaryReuseBothSets() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        container.addBlocks(Set.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6), new BlockPos(7, 8, 9)));
        var clip = new TimelineClip("building", 2, 4, 0);
        clip.getAnimationConfig().setMode(com.director.core.container.AnimationMode.FADE_IN);
        clip.getAnimationConfig().setDuration(0.4f);
        clip.getAnimationConfig().getSequenceParameters().setConcurrencyMode(
                com.director.core.animation.sequence.ConcurrencyMode.ONE_BY_ONE);
        var render = blocks(controller, container, clip, 2.01f, true, "renderBlocks");
        var world = blocks(controller, container, clip, 2.01f, true, "worldBlocks");
        assertSame(render, blocks(controller, container, clip, 2.02f, true, "renderBlocks"));
        assertSame(world, blocks(controller, container, clip, 2.02f, true, "worldBlocks"));
        assertEquals(1, render.size());
        assertTrue(world.isEmpty());
    }

    @Test void enterAndDemolitionViewsMatchIndependentPerBlockWindowsAcrossSeeks() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        container.addBlocks(Set.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6), new BlockPos(7, 8, 9)));
        var clip = new TimelineClip("building", 2, 4, 0);
        var config = clip.getAnimationConfig();
        config.setMode(com.director.core.container.AnimationMode.FADE_IN); config.setDuration(0.4f);
        config.getSequenceParameters().setConcurrencyMode(com.director.core.animation.sequence.ConcurrencyMode.ONE_BY_ONE);
        var sample = TimelineClipBlockController.class.getDeclaredMethod("calculateVisibleBlocksInternal",
                TimelineClip.class, float.class, boolean.class, Container.class, ClipAnimationConfig.class, boolean.class);
        sample.setAccessible(true);
        var result = sample.invoke(controller, clip, 2.01f, true, container, config, true);
        var accessor = result.getClass().getDeclaredMethod("sequenceEntries"); accessor.setAccessible(true);
        @SuppressWarnings("unchecked")
        var sequence = (List<com.director.core.animation.sequence.BlockSequenceEntry>) accessor.invoke(result);
        var timing = com.director.core.animation.PerBlockAnimationTiming.layout(config, 2, 4);
        for (boolean exit : new boolean[] {false, true}) {
            if (exit) container.setType(ContainerType.DEMOLITION);
            for (float time : new float[] {1, 2, 2.1f, 2.4f, 2.9f, 3.8f, 4, 4.4f, 5, 2.3f}) {
                var render = new HashSet<BlockPos>(); var world = new HashSet<BlockPos>();
                for (var entry : sequence) {
                    var window = timing.window(entry.getNormalizedTime());
                    if (exit ? time < window.end() : time >= window.start()) render.add(entry.getPos());
                    if (exit ? time < window.start() : time >= window.end()) world.add(entry.getPos());
                }
                assertEquals(render, blocks(controller, container, clip, time, true, "renderBlocks"), "render time=" + time + " exit=" + exit);
                assertEquals(world, blocks(controller, container, clip, time, true, "worldBlocks"), "world time=" + time + " exit=" + exit);
            }
        }
    }
    private Set<?> blocks(TimelineClipBlockController controller, Container container, TimelineClip clip,
            float time, boolean enabled, String accessor) throws Exception {
        Method sample = TimelineClipBlockController.class.getDeclaredMethod("calculateVisibleBlocksInternal",
                TimelineClip.class, float.class, boolean.class, Container.class, ClipAnimationConfig.class, boolean.class);
        sample.setAccessible(true);
        Object result = sample.invoke(controller, clip, time, enabled, container, clip.getAnimationConfig(), true);
        Method field = result.getClass().getDeclaredMethod(accessor); field.setAccessible(true);
        return (Set<?>) field.invoke(result);
    }

    @Test void completedContainerKeepsAllBlocksAndReusesItsStableSetAcrossRightSideTimes() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        container.addBlocks(Set.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)));
        var clip = new TimelineClip("building", 2, 4, 0);
        var right = blocks(controller, container, clip, 100, true, "worldBlocks");
        assertEquals(container.getBlocksView(), right);
        assertSame(right, blocks(controller, container, clip, 101, true, "worldBlocks"));
        assertTrue(blocks(controller, container, clip, 1, true, "renderBlocks").isEmpty());
        assertTrue(blocks(controller, container, clip, 100, false, "renderBlocks").isEmpty());
        container.setType(ContainerType.DEMOLITION);
        assertTrue(blocks(controller, container, clip, 100, true, "worldBlocks").isEmpty());
    }
}
