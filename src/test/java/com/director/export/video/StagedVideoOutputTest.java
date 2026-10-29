package com.director.export.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StagedVideoOutputTest {
    @TempDir Path directory;

    @Test void canceledVideoPreservesExistingFileAndRemovesPartialOutput() throws Exception {
        Path target = directory.resolve("clip.mp4");
        Files.writeString(target, "previous");
        Path partial;
        try (var output = StagedVideoOutput.video(target)) {
            partial = output.path();
            Files.writeString(partial, "partial");
            assertEquals("previous", Files.readString(target));
        }
        assertEquals("previous", Files.readString(target));
        assertFalse(Files.exists(partial));
    }

    @Test void completedVideoReplacesExistingFileOnlyAtCommit() throws Exception {
        Path target = directory.resolve("clip.mp4");
        Files.writeString(target, "previous");
        try (var output = StagedVideoOutput.video(target)) {
            Files.writeString(output.path(), "complete");
            output.commit();
            assertEquals("complete", Files.readString(target));
        }
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void emptyVideoCannotOverwriteExistingFile() throws Exception {
        Path target = directory.resolve("clip.mp4");
        Files.writeString(target, "previous");
        try (var output = StagedVideoOutput.video(target)) {
            assertThrows(IOException.class, output::commit);
        }
        assertEquals("previous", Files.readString(target));
    }

    @Test void canceledSequenceKeepsAllPreviousFrames() throws Exception {
        Path target = directory.resolve("frames");
        Files.createDirectories(target);
        Files.writeString(target.resolve("frame_00001.png"), "previous");
        Path stage;
        try (var output = StagedVideoOutput.sequence(target)) {
            stage = output.path();
            Files.writeString(stage.resolve("frame_00001.png"), "partial");
        }
        assertEquals("previous", Files.readString(target.resolve("frame_00001.png")));
        assertFalse(Files.exists(stage));
    }

    @Test void shorterSequenceRemovesStaleFramesAfterSuccessfulPublication() throws Exception {
        Path target = directory.resolve("frames");
        Files.createDirectories(target);
        Files.writeString(target.resolve("frame_00001.png"), "previous");
        Files.writeString(target.resolve("frame_00002.png"), "stale");
        try (var output = StagedVideoOutput.sequence(target)) {
            Files.writeString(output.path().resolve("frame_00001.png"), "complete");
            output.commit();
        }
        assertEquals("complete", Files.readString(target.resolve("frame_00001.png")));
        assertFalse(Files.exists(target.resolve("frame_00002.png")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void sequenceRollbackRestoresPreviousDirectoryWhenStagingMoveFails() throws Exception {
        Path target = directory.resolve("frames");
        Files.createDirectories(target);
        Files.writeString(target.resolve("frame_00001.png"), "previous");
        try (var output = StagedVideoOutput.sequence(target)) {
            Files.delete(output.path()); // Simulate external removal / publication failure.
            assertThrows(IOException.class, output::commit);
        }
        assertEquals("previous", Files.readString(target.resolve("frame_00001.png")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void sequenceNeverDeletesUnrelatedFilesOrNestedDirectories() throws Exception {
        Path target = directory.resolve("frames");
        Files.createDirectories(target);
        Path notes = target.resolve("notes.txt");
        Files.writeString(notes, "keep");
        assertThrows(IOException.class, () -> StagedVideoOutput.sequence(target));
        assertEquals("keep", Files.readString(notes));
        Files.delete(notes);
        Files.createDirectory(target.resolve("nested"));
        assertThrows(IOException.class, () -> StagedVideoOutput.sequence(target));
        assertTrue(Files.isDirectory(target.resolve("nested")));
    }

    @Test void unrelatedFileAddedDuringExportPreventsPublication() throws Exception {
        Path target = directory.resolve("frames");
        Files.createDirectory(target);
        Files.writeString(target.resolve("frame_00001.png"), "previous");
        try (var output = StagedVideoOutput.sequence(target)) {
            Files.writeString(output.path().resolve("frame_00001.png"), "new");
            Files.writeString(target.resolve("notes.txt"), "keep");
            assertThrows(IOException.class, output::commit);
        }
        assertEquals("previous", Files.readString(target.resolve("frame_00001.png")));
        assertEquals("keep", Files.readString(target.resolve("notes.txt")));
    }

    @Test void sequenceBasenameCannotResolveToItsParent() {
        for (String name : new String[] {".png", ".", ".."})
            assertThrows(IOException.class, () -> StagedVideoOutput.sequenceTarget(directory.resolve(name)));
    }
}
