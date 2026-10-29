package com.director.export.video;

import java.io.IOException;
import java.util.concurrent.*;

/** Times only active pipe operations; slow scene rendering never consumes the IO deadline. */
final class ExportIoWatchdog implements AutoCloseable {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Director-EncoderWatchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final long timeoutNanos;
    private final Runnable terminate;
    private final ScheduledFuture<?> timer;
    private String operation;
    private long lastProgress;
    private IOException failure;
    private boolean closed;

    ExportIoWatchdog(long timeoutMillis, Runnable terminate) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis");
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.terminate = terminate;
        long interval = Math.clamp(timeoutMillis / 4, 1, 1000);
        timer = TIMER.scheduleAtFixedRate(this::check, interval, interval, TimeUnit.MILLISECONDS);
    }

    synchronized void begin(String operation) throws IOException {
        throwIfFailed();
        this.operation = operation;
        lastProgress = System.nanoTime();
    }

    synchronized void progress() { lastProgress = System.nanoTime(); }
    synchronized void end() { operation = null; }
    synchronized void throwIfFailed() throws IOException { if (failure != null) throw failure; }

    private void check() {
        synchronized (this) {
            if (closed || operation == null || failure != null || System.nanoTime() - lastProgress < timeoutNanos) return;
            failure = new IOException("FFmpeg " + operation + "停滞，超过 "
                    + TimeUnit.NANOSECONDS.toSeconds(timeoutNanos) + " 秒没有写入进展，已终止编码");
            operation = null;
        }
        // Never acquire a pipe lock: terminating the reader releases blocked writes/flushes.
        try { terminate.run(); }
        catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(ExportIoWatchdog.class).warn("[FFmpeg] 停滞后终止进程失败", error);
        }
    }

    @Override public synchronized void close() {
        closed = true;
        operation = null;
        timer.cancel(false);
    }
}
