package com.director.timeline.playback.block;

import com.director.core.container.visibility.ClientSectionInvalidation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SettledBlockSectionsTest {
    @Test void clientSectionChangesWakeAStationaryControllerWithoutAModelOrTimeChange() throws Exception {
        com.director.core.DirectorServices.initialize();
        var constructor = TimelineClipBlockController.class.getDeclaredConstructor(); constructor.setAccessible(true);
        var controller = constructor.newInstance();
        var model = new com.director.timeline.model.TimelineModel(new com.director.timeline.model.TimelineViewportState());
        var tracker = ClientSectionInvalidation.getInstance(); var world = new Object(); tracker.reset(world);
        var budget = TimelinePlacementBudget.class.getDeclaredField("catchUpFramesRemaining"); budget.setAccessible(true);
        int previousCatchUp = budget.getInt(null); budget.setInt(null, 0);
        try {
            for (var value : Map.<String, Object>of("lastHandledRevision", 1L, "lastHandledSectionRevision", tracker.revision(),
                    "lastHandledPreviewTime", model.getCurrentTime(), "timelineDrivingPaused", true).entrySet()) {
                var field = TimelineClipBlockController.class.getDeclaredField(value.getKey()); field.setAccessible(true);
                field.set(controller, value.getValue());
            }
            assertFalse(controller.needsClipBlockUpdate(model));
            tracker.invalidateBlock(world, BlockPos.ORIGIN);
            assertTrue(controller.needsClipBlockUpdate(model));
        } finally {
            budget.setInt(null, previousCatchUp); tracker.reset(null);
        }
    }

    @Test void hundredThousandReadyPositionsSleepAndOnlyEditedSectionIsRechecked() {
        var changes = new ClientSectionInvalidation();
        var sections = new SettledBlockSections(changes);
        var world = new Object();
        var positions = new HashSet<BlockPos>();
        for (int x = 0; x < 100; x++) for (int y = 0; y < 20; y++) for (int z = 0; z < 50; z++)
            positions.add(new BlockPos(x, y, z));
        var immutable = BlockPositionSets.immutableCopy(positions);
        assertEquals(100_000, sections.visit(world, immutable, 0, pos -> true).checked());
        assertEquals(new SettledBlockSections.Visits(0, 100_000),
                sections.visit(world, immutable, 0, pos -> { fail("Stable sections must not read positions"); return true; }));
        var edited = new BlockPos(1, 1, 1);
        changes.invalidateBlock(world, edited);
        var visits = sections.visit(world, immutable, 0, pos -> {
            assertEquals(ClientSectionInvalidation.section(edited), ClientSectionInvalidation.section(pos));
            return true;
        });
        assertEquals(4096, visits.checked());
        assertEquals(95_904, visits.skipped());
        changes.invalidateBlock(world, new BlockPos(1000, 0, 0));
        assertEquals(0, sections.visit(world, immutable, 0, pos -> true).checked());
    }

    @Test void unfinishedAndOverlaySectionsKeepRunningAndCanConvergeWithoutNewTargets() {
        var changes = new ClientSectionInvalidation();
        var sections = new SettledBlockSections(changes);
        var world = new Object();
        var pending = new BlockPos(32, 0, 0);
        var blocks = Set.of(BlockPos.ORIGIN, pending);
        assertEquals(2, sections.visit(world, blocks, 0, pos -> !pos.equals(pending)).checked());
        for (int i = 0; i < 3; i++) {
            var visits = sections.visit(world, blocks, 0, pos -> {
                assertEquals(pending, pos); return false;
            });
            assertEquals(new SettledBlockSections.Visits(1, 1), visits);
        }
        assertEquals(1, sections.visit(world, blocks, 0, pos -> true).checked());
        assertEquals(new SettledBlockSections.Visits(0, 2), sections.visit(world, blocks, 0, pos -> true));
    }

    @Test void snapshotPositionWorldAndReloadChangesInvalidateProofs() {
        var changes = new ClientSectionInvalidation();
        var sections = new SettledBlockSections(changes);
        var world = new Object();
        var blocks = Set.of(BlockPos.ORIGIN);
        sections.visit(world, blocks, 0, pos -> true);
        assertEquals(1, sections.visit(world, blocks, 1, pos -> true).checked());
        var editedBlocks = Set.of(new BlockPos(1, 0, 0));
        var seen = new ArrayList<BlockPos>();
        assertEquals(1, sections.visit(world, editedBlocks, 1, pos -> { seen.add(pos); return true; }).checked());
        assertEquals(List.copyOf(editedBlocks), seen);
        var nextWorld = new Object();
        assertEquals(1, sections.visit(nextWorld, editedBlocks, 1, pos -> true).checked());
        changes.reset(nextWorld);
        assertEquals(1, sections.visit(nextWorld, editedBlocks, 1, pos -> true).checked());
        changes.reset(null);
        assertEquals(1, sections.visit(nextWorld, editedBlocks, 1, pos -> true).checked());
    }

    @Test void invalidationDuringVerificationCannotPublishAStaleReadyProof() {
        var changes = new ClientSectionInvalidation();
        var sections = new SettledBlockSections(changes);
        var world = new Object();
        var blocks = Set.of(BlockPos.ORIGIN, new BlockPos(1, 0, 0));
        var calls = new AtomicInteger();
        sections.visit(world, blocks, 0, pos -> {
            if (calls.incrementAndGet() == 1) changes.invalidateBlock(world, pos);
            return true;
        });
        assertEquals(2, sections.visit(world, blocks, 0, pos -> true).checked());
        assertEquals(0, sections.visit(world, blocks, 0, pos -> true).checked());
    }

    @Test void missingWorldCannotPutASectionToSleep() {
        var sections = new SettledBlockSections(new ClientSectionInvalidation());
        var blocks = Set.of(BlockPos.ORIGIN);
        sections.visit(null, blocks, 0, pos -> true);
        assertEquals(1, sections.visit(null, blocks, 0, pos -> true).checked());
    }
}
