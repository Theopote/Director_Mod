package com.director.export.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real-codec round trip; set DIRECTOR_TEST_FFMPEG to an executable path. */
@EnabledIfEnvironmentVariable(named = "DIRECTOR_TEST_FFMPEG", matches = ".+")
class FFmpegVideoEncoderIntegrationTest {
    @TempDir Path directory;

    @Test void actualH264AacRoundTripPreservesFrameCountPadsAudioAndWritesFaststartMetadata() throws Exception {
        String binary = System.getenv("DIRECTOR_TEST_FFMPEG");
        Path wav = directory.resolve("short-audio.wav");
        try (var audio = new AudioInputStream(new ByteArrayInputStream(new byte[4800 * 4]),
                new AudioFormat(48000, 16, 2, true, false), 4800)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        var settings = new VideoExportSettings();
        settings.width = 16; settings.height = 16; settings.fps = 30;
        settings.outputPath = directory.resolve("round trip.mp4").toString();
        settings.x264Preset = "ultrafast";
        var encoder = new FFmpegVideoEncoder(() -> binary,
                args -> new ProcessBuilder(args).redirectErrorStream(true).start(), 10000);
        encoder.setAudioWavPath(wav);
        try {
            encoder.init(settings, 31);
            for (int frame = 0; frame < 31; frame++) {
                ByteBuffer pixels = ByteBuffer.allocate(16 * 16 * 4);
                for (int pixel = 0; pixel < 256; pixel++) {
                    pixels.put((byte) (frame * 7)).put((byte) (pixel % 16 * 16)).put((byte) 90).put((byte) 255);
                }
                pixels.flip();
                encoder.encodeRgbaFrame(pixels, frame);
            }
            encoder.finish();
        } finally { encoder.abort(); }
        Path video = settings.getOutputPathAsPath();
        String bytes = Files.readString(video, StandardCharsets.ISO_8859_1);
        assertTrue(bytes.indexOf("moov") >= 0 && bytes.indexOf("moov") < bytes.indexOf("mdat"));
        Path decodedVideo = directory.resolve("decoded.rgba");
        run(binary, List.of("-i", video.toString(), "-map", "0:v:0", "-f", "rawvideo", "-pix_fmt", "rgba", decodedVideo.toString()));
        assertEquals(31 * 16 * 16 * 4, Files.size(decodedVideo));
        Path decodedAudio = directory.resolve("decoded.pcm");
        run(binary, List.of("-i", video.toString(), "-map", "0:a:0", "-f", "s16le", "-ar", "48000", "-ac", "2", decodedAudio.toString()));
        long samples = Files.size(decodedAudio) / 4;
        assertTrue(Math.abs(samples - 49600) <= 2048, "Decoded audio frames: " + samples);
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".director-")));
        }
    }

    private void run(String binary, List<String> options) throws Exception {
        List<String> command = new ArrayList<>(List.of(binary, "-v", "error", "-y"));
        command.addAll(options);
        Path diagnostic = Files.createTempFile(directory, "ffmpeg-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(diagnostic.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "FFmpeg decode timeout");
            assertEquals(0, process.exitValue(), Files.readString(diagnostic));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
