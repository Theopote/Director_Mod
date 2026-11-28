package com.director.core.container.visibility;

import com.director.core.container.BlockStore;
import com.director.core.container.Container;
import com.director.timeline.playback.TimelineSamplingContext;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContainerVisibilityServiceDiagnosticsTest {
    @Test void snapshotBackfillRemovesStaleGeometryAndPreservesExistingEntries() throws Exception {
        var container = new Container("geometry");
        BlockPos keep = new BlockPos(1, 2, 3);
        BlockPos stale = new BlockPos(7, 8, 9);
        container.addBlocks(List.of(keep));
        container.getBlockStore().add(keep, new BlockStore.Entry(null, null));

        var existing = new BlockStore.Entry(null, null);
        var snapshot = new BlockStore();
        snapshot.add(keep, existing);
        snapshot.add(stale, new BlockStore.Entry(null, null));

        backfill(container, snapshot);

        assertEquals(1, snapshot.size());
        assertSame(existing, snapshot.get(keep));
        assertFalse(snapshot.contains(stale));
    }

    @Test void emptyPersistentSourceStillRemovesStaleSnapshotEntries() throws Exception {
        var container = new Container("empty-source");
        BlockPos keep = new BlockPos(0, 64, 0);
        BlockPos stale = new BlockPos(1, 64, 0);
        container.addBlocks(List.of(keep));
        assertTrue(container.getBlockStore().isEmpty());

        var snapshot = new BlockStore();
        snapshot.add(keep, new BlockStore.Entry(null, null));
        snapshot.add(stale, new BlockStore.Entry(null, null));

        backfill(container, snapshot);

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.contains(keep));
        assertFalse(snapshot.contains(stale));
    }

    @Test void exportAndHeadlessContextsDoNotSubmitWorldHandoffTargets() {
        var service = new ContainerVisibilityService(null);
        var container = new Container("context");
        Set<BlockPos> target = Set.of(new BlockPos(1, 2, 3));

        try (var ignored = TimelineSamplingContext.open(TimelineSamplingContext.EXPORT)) {
            service.showContainerBlocksForTimeline(container, target);
            service.prepareContainerForTimeline(container);
            service.hideContainer(container);
            service.showContainer(container);
            service.restoreContainerBlocks(container, target);
        }
        try (var ignored = TimelineSamplingContext.open(TimelineSamplingContext.HEADLESS)) {
            service.showContainerBlocksForTimeline(container, target);
            service.prepareContainerForTimeline(container);
            service.hideContainer(container);
            service.showContainer(container);
            service.restoreContainerBlocks(container, target);
        }
        assertEquals(0, service.diagnostics().submitted());

        service.showContainerBlocksForTimeline(container, target);
        assertEquals(1, service.diagnostics().submitted());
    }

    @Test void quarantineIsObservableAndCanBeCleared() throws Exception {
        var service = new ContainerVisibilityService(null);
        var container = new Container("missing-snapshot");
        Method quarantine = ContainerVisibilityService.class.getDeclaredMethod(
                "quarantine", Container.class, Set.class, String.class, boolean.class);
        quarantine.setAccessible(true);
        quarantine.invoke(service, container, Set.of(), "缺少运行时快照", false);

        var diagnostics = service.diagnostics();
        assertEquals(1, diagnostics.quarantined());
        service.clearAll();
        assertEquals(0, service.diagnostics().quarantined());
    }

    private static void backfill(Container container, BlockStore snapshot) throws Exception {
        Method method = ContainerVisibilityService.class.getDeclaredMethod(
                "backfillSnapshotFromBlockStore", Container.class, BlockStore.class);
        method.setAccessible(true);
        method.invoke(new ContainerVisibilityService(null), container, snapshot);
    }
}
