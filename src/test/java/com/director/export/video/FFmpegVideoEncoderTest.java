package com.director.export.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FFmpegVideoEncoderTest {
    @TempDir Path directory;
    private VideoExportSettings settings() {
        var settings = new VideoExportSettings();
        settings.outputPath = directory.resolve("clip.mp4").toString();
        settings.width = 16; settings.height = 16; settings.enableAudio = false;
        return settings;
    }

    private FFmpegVideoEncoder encoder(FakeProcess process, AtomicReference<List<String>> command) {
        return new FFmpegVideoEncoder(() -> "test-ffmpeg", args -> {
            command.set(args);
            Files.writeString(Path.of(args.getLast()), "encoded-video");
            return process;
        }, 50);
    }

    @Test void successfulExitPublishesOutputAndWritesOnlySelectedBytesWithoutConsumingCallerBuffer() throws Exception {
        var settings = settings();
        Files.writeString(settings.getOutputPathAsPath(), "previous");
        var process = new FakeProcess();
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(process, command);
        encoder.init(settings, 1);
        ByteBuffer buffer = ByteBuffer.allocate(1028);
        buffer.position(4);
        for (int i = 4; i < 1028; i++) buffer.put(i, (byte) i);
        encoder.encodeRgbaFrame(buffer, 0);
        assertEquals(4, buffer.position());
        assertEquals("previous", Files.readString(settings.getOutputPathAsPath()));
        encoder.finish();
        assertEquals(1024, process.received.size());
        assertEquals("encoded-video", Files.readString(settings.getOutputPathAsPath()));
        assertFalse(command.get().getLast().equals(settings.outputPath));
        assertTrue(command.get().contains("+faststart"));
        assertFalse(Files.exists(Path.of(command.get().getLast())));
    }

    @Test void failedExitPreservesPreviousVideoAndExposesEncoderDiagnostic() throws Exception {
        var settings = settings();
        Files.writeString(settings.getOutputPathAsPath(), "previous");
        var process = new FakeProcess(); process.exitCode = 9;
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(process, command);
        encoder.init(settings, 1);
        encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
        IOException failure = assertThrows(IOException.class, encoder::finish);
        assertTrue(failure.getMessage().contains("test encoder diagnostic"));
        assertEquals("previous", Files.readString(settings.getOutputPathAsPath()));
        assertTrue(process.destroyed);
        assertFalse(Files.exists(Path.of(command.get().getLast())));
    }

    @Test void finishTimeoutTerminatesProcessAndRemovesPartialOutput() throws Exception {
        var process = new FakeProcess(); process.exitOnClose = false;
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(process, command);
        encoder.init(settings(), 1);
        encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(IOException.class, encoder::finish));
        assertTrue(process.destroyed);
        assertFalse(Files.exists(Path.of(command.get().getLast())));
        assertFalse(Files.exists(settings().getOutputPathAsPath()));
    }

    @Test void cancelUnblocksFlushWithoutWaitingOnUiThreadAndPreservesPreviousVideo() throws Exception {
        var settings = settings();
        Files.writeString(settings.getOutputPathAsPath(), "previous");
        var process = new FakeProcess(); process.blockWrites = true;
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(process, command);
        encoder.init(settings, 1);
        encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
        var worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> finishing = worker.submit(() -> {
                try { encoder.finish(); fail("Canceled encoding must not succeed"); }
                catch (IOException | CancellationException expected) { }
            });
            assertTrue(process.writeStarted.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), encoder::requestAbort);
            finishing.get(2, TimeUnit.SECONDS);
            assertTrue(process.destroyed);
            assertEquals("previous", Files.readString(settings.getOutputPathAsPath()));
            assertFalse(Files.exists(Path.of(command.get().getLast())));
        } finally {
            encoder.abort();
            worker.shutdownNow();
        }
    }

    @Test void cancellationBeforeStartupDoesNotLaunchProcessOrCreateOutput() {
        var encoder = new FFmpegVideoEncoder(() -> { throw new AssertionError("resolved after cancel"); },
                args -> { throw new AssertionError("started after cancel"); }, 50);
        encoder.requestAbort();
        assertThrows(CancellationException.class, () -> encoder.init(settings(), 1));
        assertFalse(Files.exists(settings().getOutputPathAsPath()));
    }

    @Test void malformedOrMissingFramesCannotBePublished() throws Exception {
        var process = new FakeProcess();
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(process, command);
        try {
            encoder.init(settings(), 2);
            assertThrows(IOException.class, () -> encoder.encodeRgbaFrame(ByteBuffer.allocate(1023), 0));
            assertThrows(IOException.class, () -> encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 1));
            encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
            assertThrows(IOException.class, encoder::finish);
            assertFalse(Files.exists(settings().getOutputPathAsPath()));
        } finally { encoder.abort(); }
        assertFalse(Files.exists(Path.of(command.get().getLast())));
    }

    @Test void audioIsPaddedToVideoFrameDurationAndMissingWavIsAnError() throws Exception {
        var settings = settings();
        var command = new AtomicReference<List<String>>();
        var encoder = encoder(new FakeProcess(), command);
        Path wav = directory.resolve("audio.wav"); Files.writeString(wav, "audio");
        encoder.setAudioWavPath(wav);
        try {
            encoder.init(settings, 31);
            List<String> args = command.get();
            assertEquals("apad", args.get(args.indexOf("-af") + 1));
            assertEquals(31 / 30.0, Double.parseDouble(args.get(args.indexOf("-t") + 1)));
            assertFalse(args.contains("-shortest"));
        } finally { encoder.abort(); }
        var missing = encoder(new FakeProcess(), new AtomicReference<>());
        missing.setAudioWavPath(directory.resolve("missing.wav"));
        assertThrows(IOException.class, () -> missing.init(settings, 1));
    }

    @Test void stalledFinalFlushIsTerminatedAutomaticallyWithoutUserCancellation() throws Exception {
        var settings = settings();
        Files.writeString(settings.getOutputPathAsPath(), "previous");
        var process = new FakeProcess(); process.blockWrites = true;
        var command = new AtomicReference<List<String>>();
        var encoder = new FFmpegVideoEncoder(() -> "test-ffmpeg", args -> {
            command.set(args);
            Files.writeString(Path.of(args.getLast()), "partial");
            return process;
        }, 1000, 50);
        encoder.init(settings, 1);
        encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
        IOException failure = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertThrows(IOException.class, encoder::finish));
        assertTrue(failure.getMessage().contains("停滞"));
        assertTrue(process.destroyed);
        assertEquals("previous", Files.readString(settings.getOutputPathAsPath()));
        assertFalse(Files.exists(Path.of(command.get().getLast())));
    }

    @Test void stalledFrameWriteIsTerminatedAutomaticallyAndReportedAsFailure() throws Exception {
        var settings = settings(); settings.width = 1024; settings.height = 512;
        var process = new FakeProcess(); process.blockWrites = true;
        var command = new AtomicReference<List<String>>();
        var encoder = new FFmpegVideoEncoder(() -> "test-ffmpeg", args -> {
            command.set(args);
            Files.writeString(Path.of(args.getLast()), "partial");
            return process;
        }, 1000, 50);
        try {
            encoder.init(settings, 1);
            IOException failure = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    assertThrows(IOException.class, () -> encoder.encodeRgbaFrame(ByteBuffer.allocate(1024 * 512 * 4), 0)));
            assertTrue(failure.getMessage().contains("停滞"));
            assertTrue(process.destroyed);
            assertFalse(Files.exists(settings.getOutputPathAsPath()));
        } finally { encoder.abort(); }
        assertFalse(Files.exists(Path.of(command.get().getLast())));
    }

    @Test void slowRenderingBetweenWritesDoesNotConsumeTheIoDeadline() throws Exception {
        var process = new FakeProcess();
        var encoder = new FFmpegVideoEncoder(() -> "test-ffmpeg", args -> {
            Files.writeString(Path.of(args.getLast()), "complete");
            return process;
        }, 1000, 50);
        try {
            encoder.init(settings(), 1);
            encoder.encodeRgbaFrame(ByteBuffer.allocate(1024), 0);
            assertFalse(process.exited.await(200, TimeUnit.MILLISECONDS));
            assertFalse(process.destroyed);
            encoder.finish();
            assertTrue(Files.exists(settings().getOutputPathAsPath()));
        } finally { encoder.abort(); }
    }

    private static final class FakeProcess extends Process {
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        final CountDownLatch exited = new CountDownLatch(1);
        final CountDownLatch writeStarted = new CountDownLatch(1);
        volatile boolean destroyed;
        boolean blockWrites;
        boolean exitOnClose = true;
        int exitCode;
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}); }
                @Override public void write(byte[] values, int offset, int length) throws IOException {
                    if (blockWrites) {
                        writeStarted.countDown();
                        try { if (!exited.await(5, TimeUnit.SECONDS)) throw new IOException("test writer timed out"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                        throw new IOException("reader terminated");
                    }
                    received.write(values, offset, length);
                }
                @Override public void close() { if (exitOnClose) exited.countDown(); }
            };
        }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream("test encoder diagnostic".getBytes(StandardCharsets.UTF_8));
        }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() throws InterruptedException { exited.await(); return exitCode; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return exited.await(timeout, unit); }
        @Override public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return exitCode; }
        @Override public boolean isAlive() { return exited.getCount() != 0; }
        @Override public void destroy() { destroyed = true; exited.countDown(); }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }
}
