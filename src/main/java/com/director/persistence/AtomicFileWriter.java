package com.director.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes through a sibling temporary file and retains the previous committed version. */
public final class AtomicFileWriter {
    private AtomicFileWriter() {}

    @FunctionalInterface
    public interface PathWriter {
        void write(Path path) throws IOException;
    }

    public static void writeString(Path target, String content) throws IOException {
        write(target, path -> Files.writeString(path, content, StandardCharsets.UTF_8));
    }

    public static void write(Path target, PathWriter writer) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absoluteTarget.resolveSibling(absoluteTarget.getFileName() + ".tmp");
        Path backup = absoluteTarget.resolveSibling(absoluteTarget.getFileName() + ".bak");
        try {
            writer.write(temp);
            if (Files.exists(absoluteTarget)) {
                Files.copy(absoluteTarget, backup, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            }
            try {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
