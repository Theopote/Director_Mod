package com.director.core.container.visibility;

import com.director.core.container.Container;
import com.director.core.visual.BlockRenderMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.director.core.container.visibility.BlockVisualStateManager.BlockVisualState;

class BlockVisualDeltaTest {
    private final BlockVisualStateManager manager = BlockVisualStateManager.getInstance();
    @BeforeEach void setup() { manager.clearAll(); }
    @AfterEach void cleanup() { manager.clearAll(); ClientSectionInvalidation.getInstance().reset(null); }

    private static final class CountingState extends BlockVisualState {
        int reads;
        CountingState() { super(BlockRenderMode.HIDDEN, 0, false); }
        @Override public BlockRenderMode getMode() { reads++; return super.getMode(); }
        @Override public float getAlpha() { reads++; return super.getAlpha(); }
        @Override public boolean shouldUseOverlay() { reads++; return super.shouldUseOverlay(); }
        @Override public boolean shouldSuppressWorldRendering() { reads++; return super.shouldSuppressWorldRendering(); }
    }

    @Test void hundredThousandStableStatesNeedNoStateReadsAndFiveEditsOnlyCreateFiveCandidates() {
        var hidden = new CountingState();
        for (int i = 0; i < 100_000; i++) manager.setBlockState(new BlockPos(i, 0, 0), hidden);
        manager.finishFrame(); hidden.reads = 0;
        for (int frame = 0; frame < 10; frame++) {
            manager.beginTimelineFrame();
            assertTrue(manager.getChangedBlocks().isEmpty());
            assertTrue(manager.getOverlayBlocks().isEmpty());
            manager.finishFrame();
        }
        assertEquals(0, hidden.reads);
        assertEquals(0, manager.getChangeCandidateCount());
        var edits = new HashSet<BlockPos>();
        for (int i = 0; i < 5; i++) {
            var pos = new BlockPos(i, 0, 0); edits.add(pos);
            manager.setBlockState(pos, BlockVisualState.ghost(0.4f, true, true));
        }
        assertEquals(edits, manager.getChangedBlocks());
        assertEquals(edits, manager.getOverlayBlocks());
        assertEquals(5, manager.getChangeCandidateCount());
        assertTrue(hidden.reads < 100, "Untouched states must not be read by diff/snapshot/overlay queries");
        for (var pos : edits) manager.setBlockState(pos, hidden);
        assertTrue(manager.getChangedBlocks().isEmpty(), "Returning to the frame baseline is a net-zero change");
        assertTrue(manager.getOverlayBlocks().isEmpty());
        manager.finishFrame();
        assertEquals(0, manager.getChangeCandidateCount());
    }

    @Test void temporarySuppressionRestoresPersistentStateAndOnlyNetEdgesRebuild() {
        var pos = BlockPos.ORIGIN;
        var ghost = BlockVisualState.ghost(0.4f, true, false);
        manager.setBlockState(pos, ghost); manager.finishFrame();
        manager.setAnimatedBlockState(pos, ghost.withSuppressWorldRendering(true));
        manager.setAnimatedBlockState(pos, ghost.withSuppressWorldRendering(true));
        assertTrue(manager.hasWorldSuppressionChanged(pos));
        manager.finishFrame(); manager.beginTimelineFrame();
        assertSame(ghost, manager.getBlockState(pos));
        assertTrue(manager.hasWorldSuppressionChanged(pos));
        manager.setAnimatedBlockState(pos, ghost.withSuppressWorldRendering(true));
        assertFalse(manager.hasWorldSuppressionChanged(pos));
        assertTrue(manager.getChangedBlocks().isEmpty());
    }

    @Test void inPlaceMutationOfSharedStateKeepsAnImmutableFrameBaselineAndRefreshesOverlay() {
        var a = BlockPos.ORIGIN; var b = new BlockPos(1, 0, 0);
        var shared = BlockVisualState.normal();
        manager.setBlocksState(List.of(a, b), shared); manager.finishFrame();
        shared.setUseOverlay(true);
        assertEquals(Set.of(a, b), manager.getOverlayBlocks());
        shared.setSuppressWorldRendering(true);
        assertTrue(manager.hasWorldSuppressionChanged(a)); assertTrue(manager.hasWorldSuppressionChanged(b));
        shared.setAlpha(0.4f);
        assertEquals(Set.of(a, b), manager.getChangedBlocks());
        shared.setHidden(true);
        assertTrue(manager.getOverlayBlocks().isEmpty());
        manager.finishFrame();
        shared.setHidden(false); shared.setAlpha(1); shared.setUseOverlay(false);
        shared.setSuppressWorldRendering(false);
        assertEquals(Set.of(a, b), manager.getChangedBlocks());
        assertTrue(manager.hasWorldSuppressionChanged(a));
    }

    @Test void clearAndReaddPreservesBaselineAndRealRemovalsKeepSuppressionEdges() {
        var a = BlockPos.ORIGIN; var b = new BlockPos(1, 0, 0);
        var suppressed = BlockVisualState.normal().withSuppressWorldRendering(true);
        manager.setBlocksState(List.of(a, b), suppressed); manager.finishFrame();
        manager.clearFrame(); manager.setBlockState(a, suppressed);
        assertEquals(Set.of(b), manager.getChangedBlocks());
        assertFalse(manager.hasWorldSuppressionChanged(a)); assertTrue(manager.hasWorldSuppressionChanged(b));
        manager.finishFrame();
        var container = new Container("removed"); container.addBlocks(Set.of(a));
        manager.clearOverridesFor(container);
        assertEquals(Set.of(a), manager.getChangedBlocks());
        assertTrue(manager.hasWorldSuppressionChanged(a));
        manager.finishFrame();
        assertTrue(manager.getChangedBlocks().isEmpty());
    }

    @Test void replacingEqualValuesAvoidsCandidatesAndRemovedObjectsNoLongerDirtyTheManager() {
        var pos = BlockPos.ORIGIN;
        var old = BlockVisualState.ghost(0.5f, true, true);
        manager.setBlockState(pos, old); manager.finishFrame();
        var replacement = BlockVisualState.ghost(0.5f, true, true);
        manager.setBlockState(pos, replacement);
        assertEquals(0, manager.getChangeCandidateCount());
        old.setHidden(true);
        assertEquals(0, manager.getChangeCandidateCount());
        replacement.setAlpha(0.3f);
        assertTrue(manager.hasStateChanged(pos));
        manager.clearAll();
        replacement.setHidden(true);
        assertTrue(manager.getChangedBlocks().isEmpty());
        assertTrue(manager.getOverlayBlocks().isEmpty());
    }

    @Test void alphaToleranceUsesFrameBaselineRatherThanTheMostRecentWrite() {
        var pos = BlockPos.ORIGIN;
        manager.setBlockState(pos, BlockVisualState.ghost(0.5f, true)); manager.finishFrame();
        manager.setBlockState(pos, BlockVisualState.ghost(0.505f, true));
        assertFalse(manager.hasStateChanged(pos));
        manager.setBlockState(pos, BlockVisualState.ghost(0.515f, true));
        assertTrue(manager.hasStateChanged(pos));
        manager.setBlockState(pos, BlockVisualState.ghost(0.5f, true));
        assertFalse(manager.hasStateChanged(pos));
    }

    @Test void mutatingNewUninstalledObjectsDoesNotDirtyExistingPositionsAndOverlayViewIsReadOnly() {
        var pos = BlockPos.ORIGIN;
        manager.setBlockState(pos, BlockVisualState.normal()); manager.finishFrame();
        var tracker = ClientSectionInvalidation.getInstance(); var world = new Object(); tracker.reset(world);
        long section = ClientSectionInvalidation.section(pos); var proof = tracker.token(world, section);
        var draft = BlockVisualState.normal(); draft.setAlpha(0.5f); draft.setMode(BlockRenderMode.GHOST_WITH_DEPTH);
        draft.setSuppressWorldRendering(true);
        assertTrue(tracker.unchanged(world, section, proof), "Uninstalled drafts cannot invalidate live sections");
        assertEquals(0, manager.getChangeCandidateCount());
        manager.setBlockState(pos, draft);
        var overlay = manager.getOverlayBlocks();
        assertThrows(UnsupportedOperationException.class, overlay::clear);
        assertEquals(Set.of(pos), overlay);
        manager.setBlockState(pos, BlockVisualState.hidden());
        assertTrue(overlay.isEmpty());
    }

    @Test void timelineEntryMembershipUsesTheExistingIndexAndClearsAtFrameBoundaries() {
        var pos = BlockPos.ORIGIN;
        assertFalse(manager.hasRenderEntry(pos));
        manager.addRenderEntry(new BlockVisualStateManager.RenderEntry(pos, null,
                BlockVisualState.ghost(0.4f, true), null, null, null));
        assertTrue(manager.hasRenderEntry(pos));
        manager.beginTimelineFrame();
        assertFalse(manager.hasRenderEntry(pos));
    }
}
