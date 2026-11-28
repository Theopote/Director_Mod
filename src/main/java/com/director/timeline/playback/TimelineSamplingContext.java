package com.director.timeline.playback;

import java.util.Objects;

/**
 * Explicit side-effect boundary for timeline sampling.
 *
 * <p>The context is scoped to the calling thread. Preview may advance live playback
 * effects; export and headless sampling only produce deterministic render state.</p>
 */
public final class TimelineSamplingContext {
    public enum Mode {
        PREVIEW,
        EXPORT,
        HEADLESS
    }

    public static final TimelineSamplingContext PREVIEW = new TimelineSamplingContext(Mode.PREVIEW);
    public static final TimelineSamplingContext EXPORT = new TimelineSamplingContext(Mode.EXPORT);
    public static final TimelineSamplingContext HEADLESS = new TimelineSamplingContext(Mode.HEADLESS);

    private static final ThreadLocal<TimelineSamplingContext> CURRENT = new ThreadLocal<>();

    private final Mode mode;

    private TimelineSamplingContext(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public Mode mode() {
        return mode;
    }

    public boolean isPreview() {
        return mode == Mode.PREVIEW;
    }

    /** Export/headless failures must abort instead of silently producing incomplete frames. */
    public boolean isStrict() {
        return !isPreview();
    }

    public boolean allowsParticles() {
        return isPreview();
    }

    public boolean allowsLiveAudio() {
        return isPreview();
    }

    /** Real world writes, container intake and other editor interactions are preview-only. */
    public boolean allowsWorldMutation() {
        return isPreview();
    }

    public static TimelineSamplingContext current() {
        TimelineSamplingContext context = CURRENT.get();
        return context != null ? context : PREVIEW;
    }

    /** Installs this context until the returned scope is closed, including exceptional exits. */
    public static Scope open(TimelineSamplingContext context) {
        Objects.requireNonNull(context, "context");
        TimelineSamplingContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    public static void run(TimelineSamplingContext context, Runnable action) {
        Objects.requireNonNull(action, "action");
        try (Scope ignored = open(context)) {
            action.run();
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
