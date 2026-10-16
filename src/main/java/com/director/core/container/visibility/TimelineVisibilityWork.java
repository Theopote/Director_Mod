package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

/** Resumable, server-thread-only reconciliation. Every inspected position consumes budget. */
final class TimelineVisibilityWork {
    interface Apply {
        /** True also means the world was already in the requested state. */
        boolean at(long position, boolean visible);
    }

    private final LongSet shown;
    private final LongSet target;
    private LongSet latestTarget;
    private final LongIterator hides;
    private LongIterator shows;
    private final LongArrayFIFOQueue retry = new LongArrayFIFOQueue();

    TimelineVisibilityWork(LongSet shown, LongSet target) {
        this.shown = shown;
        this.target = target;
        this.latestTarget = target;
        this.hides = shown.iterator();
    }

    int process(int budget, long deadlineNanos, Apply apply) {
        int inspected = 0;
        while (shows == null && hides.hasNext() && inspected < budget && System.nanoTime() < deadlineNanos) {
            long pos = hides.nextLong();
            inspected++;
            if (latestTarget.contains(pos)) continue;
            if (apply.at(pos, false)) hides.remove();
            else retry.enqueue(pos);
        }
        if (shows == null && !hides.hasNext()) shows = target.iterator();
        while (shows != null && shows.hasNext() && inspected < budget && System.nanoTime() < deadlineNanos) {
            long pos = shows.nextLong();
            inspected++;
            if (!latestTarget.contains(pos)) continue;
            if (apply.at(pos, true)) shown.add(pos);
            else retry.enqueue(pos);
        }
        // Do not mutate shown outside its hide iterator until the initial scan is finished.
        if (shows != null && !shows.hasNext()) {
            int remainingRetries = retry.size();
            while (remainingRetries-- > 0 && inspected < budget && System.nanoTime() < deadlineNanos) {
                long pos = retry.dequeueLong();
                inspected++;
                boolean visible = latestTarget.contains(pos);
                // Only cancel a retry when a newer target reversed the request. The shown
                // bookkeeping may already say "visible" while the real chunk is unavailable.
                if (visible != target.contains(pos) && shown.contains(pos) == visible) continue;
                if (apply.at(pos, visible)) {
                    if (visible) shown.add(pos);
                    else shown.remove(pos);
                } else retry.enqueue(pos);
            }
        }
        return inspected;
    }

    boolean isComplete() {
        return shows != null && !shows.hasNext() && retry.isEmpty();
    }

    LongSet target() { return target; }

    void updateLatestTarget(LongSet latest) { latestTarget = latest; }
}
