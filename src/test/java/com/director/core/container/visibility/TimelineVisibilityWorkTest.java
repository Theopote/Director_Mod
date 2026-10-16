package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TimelineVisibilityWorkTest {
    @Test void largeTargetConvergesAcrossSmallBudgetsWithoutRestartingScans() {
        var shown = new LongOpenHashSet();
        var target = new LongOpenHashSet();
        for (int i = 0; i < 100_000; i++) target.add(i);
        var work = new TimelineVisibilityWork(shown, target);
        var writes = new AtomicInteger();
        int ticks = 0;
        while (!work.isComplete() && ticks++ < 2000) {
            assertTrue(work.process(128, Long.MAX_VALUE, (pos, visible) -> {
                assertTrue(visible); writes.incrementAndGet(); return true;
            }) <= 128);
        }
        assertTrue(work.isComplete());
        assertEquals(target, shown);
        assertEquals(100_000, writes.get());
    }

    @Test void unavailablePositionsConsumeBudgetAndRetryAfterLoading() {
        var shown = new LongOpenHashSet();
        var target = new LongOpenHashSet(new long[] {1, 2, 3});
        var work = new TimelineVisibilityWork(shown, target);
        var attempts = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            int before = attempts.get();
            int used = work.process(2, Long.MAX_VALUE, (pos, visible) -> { attempts.incrementAndGet(); return false; });
            assertTrue(used <= 2);
            assertTrue(attempts.get() - before <= 2);
            assertFalse(work.isComplete());
        }
        while (!work.isComplete()) work.process(2, Long.MAX_VALUE, (pos, visible) -> true);
        assertEquals(target, shown);
    }

    @Test void alreadyCorrectWorldAcknowledgesBothHideAndShow() {
        var shown = new LongOpenHashSet(new long[] {1, 2, 3});
        var target = new LongOpenHashSet(new long[] {3, 4});
        var work = new TimelineVisibilityWork(shown, target);
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> true);
        assertEquals(target, shown);
    }

    @Test void recordedVisibleBlocksAreRecheckedSoExternalWorldEditsCanBeCorrected() {
        var shown = new LongOpenHashSet(new long[] {1});
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        var checks = new AtomicInteger();
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> {
            assertTrue(visible); checks.incrementAndGet(); return true;
        });
        assertEquals(1, checks.get());
    }

    @Test void deferredHideDoesNotInvalidateTheActiveShownIterator() {
        var shown = new LongOpenHashSet(new long[] {1, 2, 3, 4});
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {5}));
        for (int i = 0; i < 12; i++) work.process(1, Long.MAX_VALUE, (pos, visible) -> visible);
        assertFalse(work.isComplete());
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> true);
        assertEquals(new LongOpenHashSet(new long[] {5}), shown);
    }

    @Test void expiredTimeBudgetDoesNoWorldWork() {
        var work = new TimelineVisibilityWork(new LongOpenHashSet(), new LongOpenHashSet(new long[] {1}));
        assertEquals(0, work.process(100, 0, (pos, visible) -> fail("deadline exceeded")));
        assertFalse(work.isComplete());
    }

    @Test void seekingAwayDoesNotWaitForAnObsoleteUnloadedTarget() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1, 2, 3}));
        work.process(10, Long.MAX_VALUE, (pos, visible) -> false);
        assertFalse(work.isComplete());
        work.updateLatestTarget(new LongOpenHashSet());
        work.process(10, Long.MAX_VALUE, (pos, visible) -> fail("obsolete target must not edit the world"));
        assertTrue(work.isComplete());
        assertTrue(shown.isEmpty());
    }
}
