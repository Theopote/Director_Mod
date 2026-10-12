package com.director.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileWriterTest {
    @TempDir Path tempDir;

    @Test
    void replacesTargetAndKeepsPreviousVersionAsBackup() throws IOException {
        Path target = tempDir.resolve("timeline.json");
        Files.writeString(target, "old");
        AtomicFileWriter.writeString(target, "new");
        assertEquals("new", Files.readString(target));
        assertEquals("old", Files.readString(tempDir.resolve("timeline.json.bak")));
        assertFalse(Files.exists(tempDir.resolve("timeline.json.tmp")));
    }

    @Test
    void serializesConcurrentWrites() throws Exception {
        Path target = tempDir.resolve("timeline.json");
        Files.writeString(target, "initial");
        Thread writerA = new Thread(() -> {
            try {
                AtomicFileWriter.writeString(target, "from-a");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread writerB = new Thread(() -> {
            try {
                AtomicFileWriter.writeString(target, "from-b");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writerA.start();
        writerB.start();
        writerA.join();
        writerB.join();

        String content = Files.readString(target);
        assertEquals(true, content.equals("from-a") || content.equals("from-b"));
        assertFalse(Files.exists(tempDir.resolve("timeline.json.tmp")));
    }

    @Test
    void failedWritePreservesCommittedFileAndCleansTemporaryFile() throws IOException {
        Path target = tempDir.resolve("containers.dat");
        Files.writeString(target, "committed");
        assertThrows(IOException.class, () -> AtomicFileWriter.write(target, temp -> {
            Files.writeString(temp, "partial");
            throw new IOException("simulated failure");
        }));
        assertEquals("committed", Files.readString(target));
        assertFalse(Files.exists(tempDir.resolve("containers.dat.tmp")));
    }
}
