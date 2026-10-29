package com.director.core.container.visibility;

import com.director.core.container.BlockStore;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientSectionInvalidationTest {
    @Test void chunkLoadAndUnloadInvalidatesOnlyThatColumnIncludingNegativeSections() {
        var tracker = new ClientSectionInvalidation();
        var world = new Object();
        long a = ChunkSectionPos.asLong(-2, -4, 3), b = ChunkSectionPos.asLong(-2, 10, 3);
        long other = ChunkSectionPos.asLong(-1, 10, 3);
        var ta = tracker.token(world, a); var tb = tracker.token(world, b); var tc = tracker.token(world, other);
        long before = tracker.revision();
        tracker.invalidateChunk(world, -2, 3, -4, 20);
        assertTrue(tracker.revision() > before);
        assertFalse(tracker.unchanged(world, a, ta)); assertFalse(tracker.unchanged(world, b, tb));
        assertTrue(tracker.unchanged(world, other, tc));
        tracker.invalidateChunk(new Object(), -1, 3, -4, 20);
        assertTrue(tracker.unchanged(world, other, tc));
    }

    @Test void capacityEvictionIsConservativeAndWorldSwitchNeverReusesProofs() {
        var tracker = new ClientSectionInvalidation(); var world = new Object();
        Object first = tracker.token(world, 0);
        for (long i = 1; i <= 8192; i++) tracker.token(world, i);
        assertFalse(tracker.unchanged(world, 0, first));
        long before = tracker.revision();
        tracker.invalidateVisual(BlockPos.ORIGIN);
        assertTrue(tracker.revision() > before, "Evicted proofs must still wake on visual writes");
        Object last = tracker.token(world, 8192);
        tracker.reset(new Object());
        assertFalse(tracker.unchanged(world, 8192, last));
    }

    @Test void visualWritesTemporaryRestoreClearAndMutableStateEditsInvalidateProofs() {
        var tracker = ClientSectionInvalidation.getInstance();
        var manager = BlockVisualStateManager.getInstance();
        manager.clearAll(); var world = new Object(); tracker.reset(world);
        var pos = new BlockPos(1, 2, 3); long section = ClientSectionInvalidation.section(pos);
        var normal = tracker.token(world, section);
        manager.setBlockState(pos, BlockVisualStateManager.BlockVisualState.normal());
        assertTrue(tracker.unchanged(world, section, normal));
        manager.setAnimatedBlockState(pos, BlockVisualStateManager.BlockVisualState.normal().withSuppressWorldRendering(true));
        assertFalse(tracker.unchanged(world, section, normal));
        var suppressed = tracker.token(world, section);
        manager.beginTimelineFrame();
        assertFalse(tracker.unchanged(world, section, suppressed));
        var restored = tracker.token(world, section);
        manager.getBlockState(pos).setHidden(true);
        assertFalse(tracker.unchanged(world, section, restored));
        var hidden = tracker.token(world, section);
        manager.clearFrame();
        assertFalse(tracker.unchanged(world, section, hidden));
        manager.clearAll(); tracker.reset(null);
    }

    @Test void snapshotWritesAdvanceRevisionAndWakeWatchedSections() {
        var store = new BlockStore(); var tracker = ClientSectionInvalidation.getInstance();
        var world = new Object(); tracker.reset(world);
        var pos = BlockPos.ORIGIN; long section = ClientSectionInvalidation.section(pos);
        long before = store.getRevision(); var token = tracker.token(world, section);
        store.add(pos, new BlockStore.Entry(null, null));
        assertTrue(store.getRevision() > before);
        assertFalse(tracker.unchanged(world, section, token));
        before = store.getRevision(); token = tracker.token(world, section);
        store.remove(pos);
        assertTrue(store.getRevision() > before);
        assertFalse(tracker.unchanged(world, section, token));
        store.add(pos, new BlockStore.Entry(null, null)); token = tracker.token(world, section);
        before = store.getRevision(); store.clear();
        assertTrue(store.getRevision() > before);
        assertFalse(tracker.unchanged(world, section, token));
        tracker.reset(null);
    }
}
