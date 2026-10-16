package com.director.core.animation;

import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerRenderSectionsTest {
    @Test void boundedFramesEventuallyDrainAllSectionsWithoutDroppingTheRemainder() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        for (int i = 0; i < 100; i++) requests.request(world, new BlockPos(5 + i * 16, 5, 5));
        var rebuilt = new java.util.HashSet<Long>();
        for (int frame = 0; frame < 50; frame++) {
            assertEquals(2, requests.drain(world, 2, Long.MAX_VALUE, section -> {
                assertTrue(rebuilt.add(section));
                return true;
            }));
            assertEquals(100 - rebuilt.size(), requests.pendingCount(world));
        }
        assertEquals(100, rebuilt.size());
        assertEquals(0, requests.pendingCount(world));
    }

    @Test void expiredDeadlineAndUnavailableRendererRetainRequests() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        requests.request(world, new BlockPos(5, 5, 5));
        assertEquals(0, requests.drain(world, 2, 0, section -> fail("deadline expired")));
        assertEquals(1, requests.pendingCount(world));
        requests.drain(world, 2, Long.MAX_VALUE, section -> false);
        assertEquals(1, requests.pendingCount(world));
        requests.drain(world, 2, Long.MAX_VALUE, section -> true);
        assertEquals(0, requests.pendingCount(world));
    }

    @Test void requestsStayDeduplicatedAcrossFramesAndWorldSwitchDiscardsOnlyOldWorldWork() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        var first = new BlockPos(5, 5, 5);
        var second = new BlockPos(21, 5, 5);
        requests.request(world, first); requests.request(world, second);
        requests.drain(world, 1, Long.MAX_VALUE, section -> true);
        requests.request(world, second);
        assertEquals(1, requests.pendingCount(world));
        var nextWorld = new Object();
        assertEquals(0, requests.pendingCount(nextWorld));
        requests.request(nextWorld, first);
        assertEquals(1, requests.pendingCount(nextWorld));
    }
    @Test void repeatedBlocksInSameSectionProduceOneRebuild() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        for (int i = 1; i < 15; i++) requests.request(world, new BlockPos(i, 5, 5));
        assertEquals(java.util.Set.of(ChunkSectionPos.asLong(0, 0, 0)), requests.take(world));
        assertTrue(requests.take(world).isEmpty());
    }
    @Test void sectionBoundaryIncludesNeighborAndHandlesNegativeCoordinates() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        requests.request(world, new BlockPos(-1, 5, 5));
        assertEquals(java.util.Set.of(ChunkSectionPos.asLong(-1, 0, 0), ChunkSectionPos.asLong(0, 0, 0)),
                requests.take(world));
        requests.request(world, BlockPos.ORIGIN);
        assertEquals(8, requests.take(world).size());
    }
    @Test void largeSelectionsAreDeduplicatedWithoutDroppingTransitionsAndWorldChangesClearOldWork() {
        var requests = new WholeContainerRenderSections();
        var world = new Object();
        for (int i = 0; i < 100000; i++) requests.request(world, new BlockPos(5 + (i % 100) * 16, 5, 5));
        assertEquals(100, requests.take(world).size());
        requests.request(world, BlockPos.ORIGIN);
        assertTrue(requests.take(new Object()).isEmpty());
    }
    @Test void onlySuppressionEdgesTriggerHandoffNotChangingAlphaOrAnimationTransforms() {
        var manager = BlockVisualStateManager.getInstance();
        manager.clearAll();
        try {
            var pos = BlockPos.ORIGIN;
            manager.setBlockState(pos, BlockVisualStateManager.BlockVisualState.normal().withSuppressWorldRendering(true));
            assertTrue(manager.hasWorldSuppressionChanged(pos));
            manager.finishFrame(); manager.clearFrame();
            manager.setBlockState(pos, BlockVisualStateManager.BlockVisualState.ghost(0.4f, true, true));
            assertFalse(manager.hasWorldSuppressionChanged(pos));
            manager.finishFrame(); manager.clearFrame();
            manager.setBlockState(pos, BlockVisualStateManager.BlockVisualState.normal());
            assertTrue(manager.hasWorldSuppressionChanged(pos));
        } finally { manager.clearAll(); }
    }
}
