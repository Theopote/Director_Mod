package com.director.timeline.playback.block;

import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BlockOcclusionResolverTest {
    @Test void pairCacheHasAHardBoundAndRecomputesEvictedResults() throws Exception {
        var resolver = new BlockOcclusionResolver();
        var target = new Container("target"); target.addBlocks(List.of(BlockPos.ORIGIN));
        var candidate = new CountingContainer(); candidate.addBlocks(List.of(BlockPos.ORIGIN));
        resolver.resolveOccludingContainers(target, List.of(candidate));
        for (int i = 0; i < 1100; i++)
            resolver.resolveOccludingContainers(target, List.of(new Container("unrelated")));
        var field = BlockOcclusionResolver.class.getDeclaredField("cache"); field.setAccessible(true);
        assertEquals(1024, ((Map<?, ?>) field.get(resolver)).size());
        assertEquals(Set.of(candidate.getId()), resolver.resolveOccludingContainers(target, List.of(candidate)));
        assertEquals(2, candidate.visited);
    }

    private static final class CountingContainer extends Container {
        int visited;
        CountingContainer() { super("candidate"); }
        @Override public Set<BlockPos> getBlocksView() {
            Set<BlockPos> source = super.getBlocksView();
            return new AbstractSet<>() {
                @Override public int size() { return source.size(); }
                @Override public Iterator<BlockPos> iterator() {
                    var iterator = source.iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return iterator.hasNext(); }
                        @Override public BlockPos next() { visited++; return iterator.next(); }
                    };
                }
            };
        }
    }

    @Test void largeOverlapStopsAfterFirstHitAndMetadataDoesNotRescan() {
        var target = new Container("target");
        target.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(100_000, 0, 0)));
        var candidate = new CountingContainer();
        var positions = new ArrayList<BlockPos>();
        for (int i = 0; i < 100_000; i++) positions.add(new BlockPos(i, 0, 0));
        candidate.addBlocks(positions);
        var resolver = new BlockOcclusionResolver();
        assertEquals(Set.of(candidate.getId()), resolver.resolveOccludingContainers(target, List.of(candidate)));
        assertEquals(1, candidate.visited);
        candidate.setName("renamed"); candidate.setColor(42); target.setOpacity(0.5f);
        assertEquals(Set.of(candidate.getId()), resolver.resolveOccludingContainers(target, List.of(candidate)));
        assertEquals(1, candidate.visited);
    }

    @Test void boundsOverlapWithoutContainedPositionsDoesNotFadeAndBothSidesInvalidate() {
        var target = new Container("target"); target.addBlocks(List.of(BlockPos.ORIGIN));
        var candidate = new Container("candidate");
        candidate.addBlocks(List.of(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0)));
        var resolver = new BlockOcclusionResolver();
        assertTrue(resolver.resolveOccludingContainers(target, List.of(candidate)).isEmpty());
        candidate.addBlocks(List.of(BlockPos.ORIGIN));
        assertEquals(Set.of(candidate.getId()), resolver.resolveOccludingContainers(target, List.of(candidate)));
        target.clearBlocks(); target.addBlocks(List.of(new BlockPos(10, 0, 0)));
        assertTrue(resolver.resolveOccludingContainers(target, List.of(candidate)).isEmpty());
    }

    @Test void restoredObjectWithSameIdCannotReuseOldPairResult() {
        var target = new Container("target"); target.addBlocks(List.of(BlockPos.ORIGIN));
        var candidate = new Container("candidate"); candidate.addBlocks(List.of(BlockPos.ORIGIN));
        var resolver = new BlockOcclusionResolver();
        assertFalse(resolver.resolveOccludingContainers(target, List.of(candidate)).isEmpty());
        var restored = new Container(candidate.getId(), "restored", ContainerType.GENERAL);
        restored.addBlocks(List.of(new BlockPos(10, 0, 0)));
        assertTrue(resolver.resolveOccludingContainers(target, List.of(restored)).isEmpty());
    }
}
