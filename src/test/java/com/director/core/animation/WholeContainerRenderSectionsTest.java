package com.director.core.animation;

import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerRenderSectionsTest {
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
