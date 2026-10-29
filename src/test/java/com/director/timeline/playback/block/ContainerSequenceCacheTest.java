package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.sequence.*;
import com.director.core.container.Container;
import com.director.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContainerSequenceCacheTest {
    @SuppressWarnings("unchecked")
    private List<BlockSequenceEntry> sequence(TimelineClipBlockController controller, Container container,
            TimelineClip clip, float length, float duration, float delay) throws Exception {
        var method = TimelineClipBlockController.class.getDeclaredMethod("getOrGenerateSequenceEntries",
                TimelineClip.class, Container.class, ClipAnimationConfig.class, float.class, float.class, float.class);
        method.setAccessible(true);
        return (List<BlockSequenceEntry>) method.invoke(controller, clip, container, clip.getAnimationConfig(),
                length, duration, delay);
    }

    private Object ordered(TimelineClipBlockController controller, TimelineClip clip) throws Exception {
        Field field = TimelineClipBlockController.class.getDeclaredField("sequenceCacheByClip"); field.setAccessible(true);
        var cache = ((Map<?, ?>) field.get(controller)).get(clip);
        Field ordered = cache.getClass().getDeclaredField("ordered"); ordered.setAccessible(true);
        return ordered.get(cache);
    }

    @Test void metadataAndIndividualActionTimingReuseSequenceButPositionEditsInvalidate() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        container.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(1, 2, 3)));
        var clip = new TimelineClip("clip", 0, 4, 0);
        var before = sequence(controller, container, clip, 4, 1, 0);
        container.setName("renamed"); container.setColor(123); container.setOpacity(0.5f);
        assertSame(before, sequence(controller, container, clip, 4, 2, 3));
        container.removeBlock(BlockPos.ORIGIN); container.addBlocks(List.of(new BlockPos(9, 9, 9)));
        var after = sequence(controller, container, clip, 4, 2, 3);
        assertNotSame(before, after);
        assertTrue(after.stream().anyMatch(e -> e.getPos().equals(new BlockPos(9, 9, 9))));
        assertFalse(after.stream().anyMatch(e -> e.getPos().equals(BlockPos.ORIGIN)));
    }

    @Test void resizingReusesSpatialOrderAndMatchesOriginalSchedulingForEveryMapping() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        var positions = new ArrayList<BlockPos>();
        for (int x = 0; x < 8; x++) for (int y = 0; y < 4; y++) positions.add(new BlockPos(x, y, 0));
        container.addBlocks(positions);
        var clip = new TimelineClip("clip", 0, 4, 0);
        var config = clip.getAnimationConfig();
        for (var concurrency : ConcurrencyMode.values()) for (var mapping : TimeMappingStrategy.values()) {
            var params = config.getSequenceParameters();
            params.setConcurrencyMode(concurrency); params.setTimeMappingStrategy(mapping);
            params.setBatchSize(3); params.setBatchInterval(0.4f);
            sequence(controller, container, clip, 4, 1, 0);
            var spatial = ordered(controller, clip);
            var actual = sequence(controller, container, clip, 2, 1, 0);
            assertSame(spatial, ordered(controller, clip));
            var expected = BlockSequenceGenerator.generate(new ArrayList<>(container.getBlocksView()), container,
                    params, 2, 1, 0);
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).getPos(), actual.get(i).getPos());
                assertEquals(expected.get(i).getNormalizedTime(), actual.get(i).getNormalizedTime());
                assertEquals(expected.get(i).getGroupId(), actual.get(i).getGroupId());
                assertEquals(expected.get(i).getGroupNormalizedTime(), actual.get(i).getGroupNormalizedTime());
            }
        }
    }
}
