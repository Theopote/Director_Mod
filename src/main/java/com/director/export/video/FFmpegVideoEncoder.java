package com.director.export.video;

import com.director.mixin.accessor.NativeImageAccessor;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Streams borrowed RGBA frames to FFmpeg and publishes the MP4 only after a successful exit. */
public final class FFmpegVideoEncoder implements VideoEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FFmpegVideoEncoder.class);
    private final Supplier<String> binaryResolver;
    private final ProcessStarter processStarter;
    private final long exitTimeoutMillis;
    private volatile Process ffmpegProcess;
    private volatile boolean abortRequested;
    private final StringBuilder diagnosticTail = new StringBuilder();
    private Thread drainThread;
    private OutputStream ffmpegStdin;
    private WritableByteChannel ffmpegChannel;
    private StagedVideoOutput output;
    private boolean initialized;
    private int width;
    private int height;
    private int expectedFrames;
    private int encodedFrames;
    private Path audioWavPath;

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    public FFmpegVideoEncoder() {
        this(FFmpegLocator::resolveBinary,
                command -> new ProcessBuilder(command).redirectErrorStream(true).start(),
                TimeUnit.MINUTES.toMillis(5));
    }

    FFmpegVideoEncoder(Supplier<String> binaryResolver, ProcessStarter processStarter, long exitTimeoutMillis) {
        this.binaryResolver = java.util.Objects.requireNonNull(binaryResolver);
        this.processStarter = java.util.Objects.requireNonNull(processStarter);
        if (exitTimeoutMillis <= 0) throw new IllegalArgumentException("exitTimeoutMillis");
        this.exitTimeoutMillis = exitTimeoutMillis;
    }

    public void setAudioWavPath(Path audioWavPath) {
        this.audioWavPath = audioWavPath;
    }

    @Override public void init(VideoExportSettings settings, int totalFrames) throws IOException {
        checkCanceled();
        if (initialized) throw new IOException("编码器不能重复初始化");
        if (!settings.validate() || settings.format != VideoExportSettings.FORMAT_H264_MP4 || totalFrames <= 0)
            throw new IOException("MP4 导出设置或总帧数无效");
        initialized = true;
        width = settings.width;
        height = settings.height;
        expectedFrames = totalFrames;
        if (audioWavPath != null && !Files.isRegularFile(audioWavPath))
            throw new IOException("音频文件不存在：" + audioWavPath);
        try {
            output = StagedVideoOutput.video(settings.getOutputPathAsPath());
            List<String> command = buildCommand(settings, totalFrames, output.path());
            checkCanceled();
            LOGGER.info("[FFmpeg] Starting: {}", String.join(" ", command));
            ffmpegProcess = processStarter.start(command);
            checkCanceled();
            startDrainThread(ffmpegProcess.getInputStream());
            ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 1024 * 1024);
            ffmpegChannel = Channels.newChannel(ffmpegStdin);
        } catch (IOException | RuntimeException failure) {
            abort();
            throw failure;
        }
    }

    private List<String> buildCommand(VideoExportSettings settings, int totalFrames, Path destination) {
        List<String> command = new ArrayList<>(List.of(binaryResolver.get(), "-hide_banner", "-nostats", "-y",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height,
                "-r", Integer.toString(settings.fps), "-i", "-"));
        if (audioWavPath != null) command.addAll(List.of("-i", audioWavPath.toAbsolutePath().toString(),
                "-map", "0:v:0", "-map", "1:a:0"));
        else command.add("-an");
        command.addAll(List.of("-c:v", "libx264", "-preset",
                settings.x264Preset.trim().toLowerCase(java.util.Locale.ROOT),
                "-crf", Integer.toString(settings.x264Crf), "-pix_fmt", "yuv420p"));
        if (audioWavPath != null) command.addAll(List.of("-c:a", "aac", "-b:a", "192k", "-af", "apad",
                "-t", Double.toString(totalFrames / (double) settings.fps)));
        command.addAll(List.of("-movflags", "+faststart", destination.toString()));
        return command;
    }

    @Override public void encodeFrame(NativeImage image, int frameIndex) throws IOException {
        if (image.getWidth() != width || image.getHeight() != height) throw new IOException("帧尺寸与导出尺寸不匹配");
        long pointer = ((NativeImageAccessor) (Object) image).getPointer();
        if (pointer == 0) throw new IOException("NativeImage 已关闭");
        // Borrow the pixels only for this synchronous write; the caller owns and closes the image.
        encodeRgbaFrame(MemoryUtil.memByteBuffer(pointer, Math.multiplyExact(Math.multiplyExact(width, height), 4)), frameIndex);
    }

    void encodeRgbaFrame(ByteBuffer pixels, int frameIndex) throws IOException {
        checkCanceled();
        if (ffmpegChannel == null) throw new IOException("FFmpegVideoEncoder 未初始化或已经结束");
        if (frameIndex != encodedFrames || encodedFrames >= expectedFrames) throw new IOException("视频帧序号不连续");
        if (pixels.remaining() != width * height * 4) throw new IOException("RGBA 帧字节数不匹配");
        ByteBuffer borrowed = pixels.duplicate();
        try {
            while (borrowed.hasRemaining()) {
                checkCanceled();
                ffmpegChannel.write(borrowed);
            }
            encodedFrames++;
        } catch (IOException failure) {
            checkCanceled();
            throw failure;
        }
    }

    @Override public void finish() throws IOException {
        checkCanceled();
        if (output == null || ffmpegStdin == null || encodedFrames != expectedFrames)
            throw new IOException("视频帧数不完整或编码器已经结束");
        try {
            closeStdin();
            waitForExitOrThrow();
            checkCanceled();
            output.commit();
        } catch (IOException | RuntimeException failure) {
            abort();
            throw failure;
        }
    }

    /** May run on the UI thread: terminate the reader to unblock writes, never flush or free pixel memory. */
    public void requestAbort() {
        abortRequested = true;
        Process process = ffmpegProcess;
        if (process != null) process.destroyForcibly();
    }

    @Override public void abort() {
        requestAbort();
        Process process = ffmpegProcess;
        if (process != null) {
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) LOGGER.warn("[FFmpeg] 终止后仍未退出");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        try { closeStdin(); }
        catch (IOException ignored) { }
        if (output != null) {
            try { output.close(); }
            catch (IOException cleanup) { LOGGER.warn("[FFmpeg] 临时视频清理失败：{}", output.path(), cleanup); }
        }
    }

    private void checkCanceled() {
        if (abortRequested) throw new CancellationException("导出已取消");
    }

    private void closeStdin() throws IOException {
        OutputStream stream = ffmpegStdin;
        ffmpegStdin = null;
        ffmpegChannel = null;
        if (stream != null) stream.close();
    }

    private void waitForExitOrThrow() throws IOException {
        try {
            if (!ffmpegProcess.waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IOException("FFmpeg 完成编码超时，请检查磁盘空间和系统资源");
            }
            checkCanceled();
            if (ffmpegProcess.exitValue() != 0) {
                if (drainThread != null) drainThread.join(500);
                String diagnostic;
                synchronized (diagnosticTail) { diagnostic = diagnosticTail.toString().trim(); }
                throw new IOException("FFmpeg 编码失败（退出码 " + ffmpegProcess.exitValue() + "）"
                        + (diagnostic.isEmpty() ? "" : ": " + diagnostic));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg 等待被中断", interrupted);
        }
    }

    private void startDrainThread(InputStream input) {
        drainThread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (input) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count == 0) continue;
                    String message = new String(buffer, 0, count, StandardCharsets.UTF_8);
                    synchronized (diagnosticTail) {
                        diagnosticTail.append(message);
                        if (diagnosticTail.length() > 8192) diagnosticTail.delete(0, diagnosticTail.length() - 8192);
                    }
                    LOGGER.debug("[FFmpeg] {}", message);
                }
            } catch (IOException ignored) { }
        }, "Director-FFmpeg-LogDrain");
        drainThread.setDaemon(true);
        drainThread.start();
    }
}
