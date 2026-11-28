package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static com.director.core.container.visibility.TimelineVisibilityWork.ApplyResult.*;
import static org.junit.jupiter.api.Assertions.*;

class TimelineVisibilityWorkTest {
    @Test void failedVerificationOfRecordedVisibleBlockIsRetriedUntilItsChunkIsReady() {
        var shown = new LongOpenHashSet(new long[] {1});
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        work.process(10, Long.MAX_VALUE, (pos, visible) -> WAITING);
        assertFalse(work.isComplete());
        var attempts = new AtomicInteger();
        work.process(10, Long.MAX_VALUE, (pos, visible) -> fail("retry is still backing off"));
        work.process(10, Long.MAX_VALUE, (pos, visible) -> { attempts.incrementAndGet(); return SUCCESS; });
        assertEquals(1, attempts.get());
        assertTrue(work.isComplete());
    }
    @Test void submittedTargetKeepsAdvancingOnServerTicksWithoutAnotherClientFrame() {
        var shown = new LongOpenHashSet();
        var target = new LongOpenHashSet();
        for (int i = 0; i < 10_000; i++) target.add(i);
        // One frame submits the full target, then only server ticks run.
        var work = new TimelineVisibilityWork(shown, target);
        for (int tick = 0; tick < 4; tick++) work.process(3000, Long.MAX_VALUE, (pos, visible) -> SUCCESS);
        assertTrue(work.isComplete());
        assertEquals(10_000, shown.size());
    }
    @Test void largeTargetConvergesAcrossSmallBudgetsWithoutRestartingScans() {
        var shown = new LongOpenHashSet();
        var target = new LongOpenHashSet();
        for (int i = 0; i < 100_000; i++) target.add(i);
        var work = new TimelineVisibilityWork(shown, target);
        var writes = new AtomicInteger();
        int ticks = 0;
        while (!work.isComplete() && ticks++ < 2000) {
            assertTrue(work.process(128, Long.MAX_VALUE, (pos, visible) -> {
                assertTrue(visible); writes.incrementAndGet(); return SUCCESS;
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
            int used = work.process(2, Long.MAX_VALUE, (pos, visible) -> { attempts.incrementAndGet(); return WAITING; });
            assertTrue(used <= 2);
            assertTrue(attempts.get() - before <= 2);
            assertFalse(work.isComplete());
        }
        while (!work.isComplete()) work.process(2, Long.MAX_VALUE, (pos, visible) -> SUCCESS);
        assertEquals(target, shown);
    }

    @Test void alreadyCorrectWorldAcknowledgesBothHideAndShow() {
        var shown = new LongOpenHashSet(new long[] {1, 2, 3});
        var target = new LongOpenHashSet(new long[] {3, 4});
        var work = new TimelineVisibilityWork(shown, target);
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> SUCCESS);
        assertEquals(target, shown);
    }

    @Test void recordedVisibleBlocksAreRecheckedSoExternalWorldEditsCanBeCorrected() {
        var shown = new LongOpenHashSet(new long[] {1});
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        var checks = new AtomicInteger();
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> {
            assertTrue(visible); checks.incrementAndGet(); return SUCCESS;
        });
        assertEquals(1, checks.get());
    }

    @Test void hideAndShowConvergeWithoutInvalidatingIterators() {
        var shown = new LongOpenHashSet(new long[] {1, 2, 3, 4});
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {5}));
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> SUCCESS);
        assertTrue(work.isComplete());
        assertEquals(new LongOpenHashSet(new long[] {5}), shown);
    }

    @Test void expiredTimeBudgetDoesNoWorldWork() {
        var work = new TimelineVisibilityWork(new LongOpenHashSet(), new LongOpenHashSet(new long[] {1}));
        assertEquals(0, work.process(100, 0, (pos, visible) -> fail("deadline exceeded")));
        assertFalse(work.isComplete());
    }

    @Test void permanentFailureIsNotRequeued() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        var attempts = new AtomicInteger();
        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return FAILED;
        });
        assertTrue(work.isComplete());
        assertTrue(work.hasFailures());
        assertEquals(1, work.permanentFailures());
        assertEquals(0, work.pendingRetries());
        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return SUCCESS;
        });
        assertEquals(1, attempts.get());
    }

    @Test void newerTargetCanSupersedeAPermanentFailure() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        work.process(10, Long.MAX_VALUE, (pos, visible) -> FAILED);
        assertTrue(work.hasFailures());
        work.updateLatestTarget(new LongOpenHashSet());
        work.process(10, Long.MAX_VALUE, (pos, visible) -> fail("obsolete target must not be retried"));
        assertTrue(work.isComplete());
        assertFalse(work.hasFailures());
    }

    @Test void writeFailureRetriesOnTheNextTickAndCanConverge() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        var attempts = new AtomicInteger();
        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return RETRY;
        });
        assertFalse(work.isComplete());
        assertEquals(1, work.pendingRetries());
        assertFalse(work.hasFailures());

        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return SUCCESS;
        });
        assertTrue(work.isComplete());
        assertEquals(2, attempts.get());
        assertEquals(new LongOpenHashSet(new long[] {1}), shown);
    }

    @Test void repeatedWriteFailureStopsAtRetryLimit() {
        var work = new TimelineVisibilityWork(new LongOpenHashSet(), new LongOpenHashSet(new long[] {1}));
        var attempts = new AtomicInteger();
        for (int i = 0; i < TimelineVisibilityWork.MAX_TRANSIENT_ATTEMPTS; i++) {
            work.process(10, Long.MAX_VALUE, (pos, visible) -> {
                attempts.incrementAndGet();
                return RETRY;
            });
        }
        assertTrue(work.isComplete());
        assertTrue(work.hasFailures());
        assertEquals(TimelineVisibilityWork.MAX_TRANSIENT_ATTEMPTS, attempts.get());
        assertEquals(1, work.retriesExhausted());
        assertEquals(0, work.pendingRetries());
    }

    @Test void transientFailureBacksOffAndStopsAtRetryLimit() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1}));
        var attempts = new AtomicInteger();
        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return WAITING;
        });
        assertEquals(1, attempts.get());
        work.process(10, Long.MAX_VALUE, (pos, visible) -> {
            attempts.incrementAndGet();
            return WAITING;
        });
        assertEquals(1, attempts.get(), "retry must respect its backoff window");

        int ticks = 0;
        while (!work.isComplete() && ticks++ < 1000) {
            work.process(10, Long.MAX_VALUE, (pos, visible) -> {
                attempts.incrementAndGet();
                return WAITING;
            });
        }
        assertTrue(work.isComplete());
        assertTrue(work.hasFailures());
        assertEquals(TimelineVisibilityWork.MAX_TRANSIENT_ATTEMPTS, attempts.get());
        assertEquals(1, work.retriesExhausted());
        assertEquals(0, work.pendingRetries());
    }

    @Test void seekingAwayDoesNotWaitForAnObsoleteUnloadedTarget() {
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, new LongOpenHashSet(new long[] {1, 2, 3}));
        work.process(10, Long.MAX_VALUE, (pos, visible) -> WAITING);
        assertFalse(work.isComplete());
        work.updateLatestTarget(new LongOpenHashSet());
        work.process(10, Long.MAX_VALUE, (pos, visible) -> fail("obsolete target must not edit the world"));
        assertTrue(work.isComplete());
        assertTrue(shown.isEmpty());
    }
}
