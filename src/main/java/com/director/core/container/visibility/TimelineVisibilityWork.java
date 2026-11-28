package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

/** Resumable, server-thread-only reconciliation. Every inspected position consumes budget. */
final class TimelineVisibilityWork {
    enum ApplyResult {
        SUCCESS,
        /** The chunk is not loaded yet; retry with bounded exponential backoff. */
        WAITING,
        /** A world write failed or did not materialize; retry on the next tick. */
        RETRY,
        /** Missing/corrupt snapshot data; never retry the same target. */
        FAILED
    }

    interface Apply {
        ApplyResult at(long position, boolean visible);
    }

    static final int MAX_TRANSIENT_ATTEMPTS = 12;

    private final LongSet shown;
    private final LongSet target;
    private LongSet latestTarget;
    private final LongIterator hides;
    private LongIterator shows;
    private final LongArrayFIFOQueue retry = new LongArrayFIFOQueue();
    private final Long2ObjectMap<RetryState> retryStates = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Boolean> terminalFailures = new Long2ObjectOpenHashMap<>();
    private long tick;
    private long transientWaits;
    private long permanentFailures;
    private long retriesExhausted;

    private static final class RetryState {
        boolean visible;
        int attempts;
        long nextAttemptTick;

        RetryState(boolean visible) {
            this.visible = visible;
        }
    }

    TimelineVisibilityWork(LongSet shown, LongSet target) {
        this.shown = shown;
        this.target = target;
        this.latestTarget = target;
        this.hides = shown.iterator();
    }

    int process(int budget, long deadlineNanos, Apply apply) {
        tick++;
        terminalFailures.long2ObjectEntrySet().removeIf(entry -> {
            boolean desired = latestTarget.contains(entry.getLongKey());
            return desired != entry.getValue() || shown.contains(entry.getLongKey()) == desired;
        });
        int inspected = 0;
        while (shows == null && hides.hasNext() && inspected < budget && System.nanoTime() < deadlineNanos) {
            long pos = hides.nextLong();
            inspected++;
            if (latestTarget.contains(pos)) continue;
            ApplyResult result = apply.at(pos, false);
            if (result == ApplyResult.SUCCESS) hides.remove();
            else if (recordFailure(pos, false, result)) retry.enqueue(pos);
        }
        if (shows == null && !hides.hasNext()) shows = target.iterator();
        while (shows != null && shows.hasNext() && inspected < budget && System.nanoTime() < deadlineNanos) {
            long pos = shows.nextLong();
            inspected++;
            if (!latestTarget.contains(pos)) continue;
            ApplyResult result = apply.at(pos, true);
            if (result == ApplyResult.SUCCESS) shown.add(pos);
            else if (recordFailure(pos, true, result)) retry.enqueue(pos);
        }
        // Do not mutate shown outside its hide iterator until the initial scan is finished.
        if (shows != null && !shows.hasNext()) {
            int remainingRetries = retry.size();
            while (remainingRetries-- > 0 && inspected < budget && System.nanoTime() < deadlineNanos) {
                long pos = retry.dequeueLong();
                RetryState state = retryStates.get(pos);
                if (state == null) continue;
                boolean visible = latestTarget.contains(pos);
                if (state.visible != visible) {
                    // A newer target supersedes the old request. Retry immediately only if the
                    // recorded world state does not already match that newer target.
                    if (shown.contains(pos) == visible) {
                        retryStates.remove(pos);
                    } else {
                        state.visible = visible;
                        state.attempts = 0;
                        state.nextAttemptTick = tick;
                    }
                    continue;
                }
                if (state.nextAttemptTick > tick) {
                    retry.enqueue(pos);
                    continue;
                }
                inspected++;
                ApplyResult result = apply.at(pos, visible);
                if (result == ApplyResult.SUCCESS) {
                    if (visible) shown.add(pos);
                    else shown.remove(pos);
                    retryStates.remove(pos);
                } else {
                    if (recordFailure(pos, visible, result)) retry.enqueue(pos);
                }
            }
        }
        return inspected;
    }

    private boolean recordFailure(long pos, boolean visible, ApplyResult result) {
        if (result == ApplyResult.FAILED) {
            permanentFailures++;
            recordTerminalFailure(pos, visible);
            retryStates.remove(pos);
            return false;
        }
        if (result == ApplyResult.WAITING) transientWaits++;
        RetryState state = retryStates.computeIfAbsent(pos, ignored -> new RetryState(visible));
        state.attempts++;
        if (state.attempts >= MAX_TRANSIENT_ATTEMPTS) {
            retriesExhausted++;
            recordTerminalFailure(pos, visible);
            retryStates.remove(pos);
            return false;
        }
        long delayTicks = result == ApplyResult.RETRY ? 1 : 1L << Math.min(state.attempts, 5);
        state.nextAttemptTick = tick + delayTicks;
        return true;
    }

    private void recordTerminalFailure(long pos, boolean visible) {
        ((Long2ObjectFunction<Boolean>) terminalFailures).put(Long.valueOf(pos), Boolean.valueOf(visible));
    }

    boolean isComplete() {
        return shows != null && !shows.hasNext() && retryStates.isEmpty();
    }

    boolean hasFailures() {
        return !terminalFailures.isEmpty();
    }

    long permanentFailures() { return permanentFailures; }
    long transientWaits() { return transientWaits; }
    long retriesExhausted() { return retriesExhausted; }
    int pendingRetries() { return retryStates.size(); }

    LongSet target() { return target; }

    void updateLatestTarget(LongSet latest) { latestTarget = latest; }
}
