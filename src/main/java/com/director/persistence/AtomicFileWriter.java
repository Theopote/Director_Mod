package com.director.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

/** Writes through a sibling temporary file and retains the previous committed version. */
public final class AtomicFileWriter {
    private static final int MAX_MOVE_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 50L;
    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private AtomicFileWriter() {}

    @FunctionalInterface
    public interface PathWriter {
        void write(Path path) throws IOException;
    }

    public static void writeString(Path target, String content) throws IOException {
        write(target, path -> Files.writeString(path, content, StandardCharsets.UTF_8));
    }

    public static void write(Path target, PathWriter writer) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Object lock = FILE_LOCKS.computeIfAbsent(absoluteTarget.toString(), ignored -> new Object());
        synchronized (lock) {
            Path temp = absoluteTarget.resolveSibling(absoluteTarget.getFileName() + ".tmp");
            Path backup = absoluteTarget.resolveSibling(absoluteTarget.getFileName() + ".bak");
            try {
                writer.write(temp);
                backupExistingFile(absoluteTarget, backup);
                moveWithRetry(temp, absoluteTarget);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void backupExistingFile(Path target, Path backup) {
        if (!Files.exists(target)) {
            return;
        }
        try {
            Files.deleteIfExists(backup);
            Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ignored) {
            // Backup is best-effort; a locked .bak must not block the primary write.
        }
    }

    private static void moveWithRetry(Path source, Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                lastFailure = e;
                if (attempt + 1 < MAX_MOVE_ATTEMPTS) {
                    sleepQuietly(RETRY_DELAY_MS);
                }
            }
        }
        throw lastFailure;
    }

    private static void sleepQuietly(long delayMs) throws IOException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry atomic file write", e);
        }
    }
}
