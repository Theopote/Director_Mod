package com.director.client.renderer.mesh;

import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ContainerOutlineMeshCacheTest {
    private static final class CountingContainer extends Container {
        int reads;
        CountingContainer() { super("outline"); }
        @Override public Set<BlockPos> getBlocks() { throw new AssertionError("Full copies are forbidden"); }
        @Override public Set<BlockPos> getBlocksView() { reads++; return super.getBlocksView(); }
    }

    @Test void cachedOutlineDoesNotReadPositionsAfterMetadataEdits() {
        var container = new CountingContainer(); container.addBlocks(List.of(BlockPos.ORIGIN));
        var cache = new ContainerOutlineMeshCache();
        var mesh = cache.getOrCreateMesh(container);
        container.setName("new name"); container.setColor(123); container.setOpacity(0.5f);
        for (int i = 0; i < 100; i++) assertSame(mesh, cache.getOrCreateMesh(container));
        assertEquals(1, container.reads);
    }

    @Test void equalSizePositionReplacementAndCloneClearingRebuildOutlines() {
        var container = new CountingContainer(); container.addBlocks(List.of(BlockPos.ORIGIN));
        var cache = new ContainerOutlineMeshCache();
        var original = cache.getOrCreateMesh(container);
        container.removeBlock(BlockPos.ORIGIN); container.addBlocks(List.of(new BlockPos(10, 0, 0)));
        var moved = cache.getOrCreateMesh(container);
        assertNotEquals(original, moved);
        assertEquals(2, container.reads);
        var clone = container.clone();
        assertEquals(moved, cache.getOrCreateMesh(clone));
        clone.clearBlockReferencesForClone();
        assertTrue(cache.getOrCreateMesh(clone).quads().isEmpty());
        cache.clear();
        assertEquals(moved, cache.getOrCreateMesh(container));
    }
}
