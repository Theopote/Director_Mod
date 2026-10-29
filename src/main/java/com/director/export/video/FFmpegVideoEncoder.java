package com.director.export.video;

import com.director.mixin.accessor.NativeImageAccessor;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg MP4 编码器（stdin 管道写入 raw RGBA）
 *
 * <p>
 * 不产生临时 PNG 序列，直接输出 MP4（H.264）。
 * </p>
 *
 * <p>
 * FFmpeg 路径由 {@link FFmpegLocator} 解析：
 * {@code -Ddirector.ffmpeg} → 游戏目录（含子文件夹）→ 系统 PATH。
 * </p>
 */
public final class FFmpegVideoEncoder implements VideoEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FFmpegVideoEncoder.class);
    private static final long EXIT_TIMEOUT_SECONDS = 300L;

    private volatile Process ffmpegProcess;
    private volatile boolean abortRequested;
    private final StringBuilder diagnosticTail = new StringBuilder();
    private volatile Thread drainThread;
    private OutputStream ffmpegStdin;
    private WritableByteChannel ffmpegChannel;

    private int width;
    private int height;
    private Path audioWavPath;

    /** 复用的帧缓冲（direct, raw RGBA bytes） */
    private ByteBuffer rgbaDirectBuffer;

    /**
     * 设置要混流进 MP4 的 WAV 音频文件（可选）。
     */
    public void setAudioWavPath(Path audioWavPath) {
        this.audioWavPath = audioWavPath;
    }

    @Override
    public void init(VideoExportSettings settings, int totalFrames) throws IOException {
        this.width = settings.width;
        this.height = settings.height;
        int fps = settings.fps;
        Path outputPath = settings.getOutputPathAsPath();

        if (outputPath == null) {
            throw new IOException("outputPath 无效");
        }

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (abortRequested) throw new java.util.concurrent.CancellationException();
        String ffmpeg = detectFFmpegBinary();

        // ffmpeg -y -f rawvideo -vcodec rawvideo -pix_fmt rgba -s WxH -r FPS -i - (-i
        // audio.wav -map ...) -c:v libx264 ... out.mp4
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-y");
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-vcodec");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("rgba");
        cmd.add("-s");
        cmd.add(width + "x" + height);
        cmd.add("-r");
        cmd.add(Integer.toString(fps));
        cmd.add("-i");
        cmd.add("-");

        boolean hasAudio = audioWavPath != null && Files.exists(audioWavPath);
        if (hasAudio) {
            cmd.add("-i");
            cmd.add(audioWavPath.toAbsolutePath().toString());
            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-map");
            cmd.add("1:a:0");
        } else {
            cmd.add("-an");
        }
        cmd.add("-c:v");
        cmd.add("libx264");
        // 用户可调：preset / crf（保持默认值即可获得与旧版本相近行为）
        String preset = (settings.x264Preset != null && !settings.x264Preset.isBlank())
                ? settings.x264Preset.trim().toLowerCase(java.util.Locale.ROOT)
                : "slow";
        int crf = Math.max(0, Math.min(51, settings.x264Crf));

        cmd.add("-preset");
        cmd.add(preset);
        cmd.add("-crf");
        cmd.add(Integer.toString(crf));
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");

        if (hasAudio) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            // Pad fractional-frame audio tails; the video frame count defines output duration.
            cmd.add("-af");
            cmd.add("apad");
            cmd.add("-t");
            cmd.add(Double.toString(totalFrames / (double) fps));
        }
        cmd.add(outputPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        LOGGER.info("[FFmpeg] Starting: {}", String.join(" ", cmd));
        ffmpegProcess = pb.start();
        if (abortRequested) {
            requestAbort();
            throw new java.util.concurrent.CancellationException();
        }

        // 防止 ffmpeg 输出填满缓冲区导致阻塞：后台吞日志
        startDrainThread(ffmpegProcess.getInputStream());

        ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 1024 * 1024);
        ffmpegChannel = Channels.newChannel(ffmpegStdin);
        rgbaDirectBuffer = MemoryUtil.memAlloc(width * height * 4);
    }

    @Override
    public void encodeFrame(NativeImage image, int frameIndex) throws IOException {
        if (ffmpegStdin == null || ffmpegChannel == null || ffmpegProcess == null) {
            throw new IOException("FFmpegVideoEncoder 未初始化");
        }
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        if (imgW != width || imgH != height) {
            throw new IOException("帧尺寸不匹配：got " + imgW + "x" + imgH
                    + ", expected " + width + "x" + height);
        }

        // 🚀 性能：直接从 NativeImage 的 native 像素区批量拷贝并写入 ffmpeg stdin。
        // NativeImage 的像素存储是 little-endian RGBA（内存顺序 RGBA），与 ffmpeg rawvideo -pix_fmt rgba 匹配。
        int size = width * height * 4;
        long ptr = ((NativeImageAccessor) (Object) image).getPointer();
        if (ptr == 0L) {
            throw new IOException("NativeImage pointer == 0");
        }

        rgbaDirectBuffer.clear();
        rgbaDirectBuffer.limit(size);
        MemoryUtil.memCopy(ptr, MemoryUtil.memAddress(rgbaDirectBuffer), (long) size);
        while (rgbaDirectBuffer.hasRemaining()) {
            if (abortRequested) throw new java.util.concurrent.CancellationException();
            ffmpegChannel.write(rgbaDirectBuffer);
        }

        // 建议在每几帧 flush 一次，或者完全靠管道缓冲区。
        // 30 帧 (约 1s @ 30fps) flush 一次较为合适。
        if (frameIndex % 30 == 0) {
            ffmpegStdin.flush();
        }
    }

    @Override
    public void finish() throws IOException {
        // 正常结束：关闭 stdin，让 ffmpeg flush/finish
        try {
            closeStdin();
            waitForExitOrThrow();
        } finally {
            freeBuffers();
        }
    }

    /** Safe from the UI thread: terminate first, never flush pipes or free worker-owned memory here. */
    public void requestAbort() {
        abortRequested = true;
        Process process = ffmpegProcess;
        if (process != null) process.destroyForcibly();
    }

    public void abort() {
        requestAbort();
        try {
            closeStdin();
        } catch (IOException ignored) {
        } finally {
            freeBuffers();
        }
    }

    private void closeStdin() throws IOException {
        OutputStream stream = ffmpegStdin;
        ffmpegStdin = null;
        ffmpegChannel = null;
        if (stream != null) stream.close();
    }

    private void freeBuffers() {
        if (rgbaDirectBuffer != null) {
            try {
                MemoryUtil.memFree(rgbaDirectBuffer);
            } catch (Exception ignore) {
                // ignore
            }
            rgbaDirectBuffer = null;
        }
    }

    private void waitForExitOrThrow() throws IOException {
        if (ffmpegProcess == null) {
            return;
        }
        try {
            boolean exited = ffmpegProcess.waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!exited) {
                ffmpegProcess.destroyForcibly();
                throw new IOException("FFmpeg 在 " + EXIT_TIMEOUT_SECONDS
                    + " 秒内未退出，已强制终止。请检查磁盘空间、编码器和系统资源。");
            }
            int exit = ffmpegProcess.exitValue();
            if (exit != 0) {
                Thread drain = drainThread;
                if (drain != null) drain.join(500);
                String diagnostic;
                synchronized (diagnosticTail) { diagnostic = diagnosticTail.toString().trim(); }
                throw new IOException("FFmpeg 编码失败（退出码 " + exit + "）"
                        + (diagnostic.isEmpty() ? "" : ": " + diagnostic));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg 等待被中断", e);
        }
    }

    private void startDrainThread(InputStream is) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            try {
                while (true) {
                    int n = is.read(buf);
                    if (n < 0) {
                        break;
                    }
                    if (n > 0) {
                        String message = new String(buf, 0, n, StandardCharsets.UTF_8);
                        synchronized (diagnosticTail) {
                            diagnosticTail.append(message);
                            if (diagnosticTail.length() > 8192)
                                diagnosticTail.delete(0, diagnosticTail.length() - 8192);
                        }
                        LOGGER.debug("[FFmpeg] {}", message);
                    }
                }
            } catch (Exception ignore) {
                // ignore
            }
        }, "Director-FFmpeg-LogDrain");
        t.setDaemon(true);
        drainThread = t;
        t.start();
    }

    private String detectFFmpegBinary() {
        FFmpegLocator.Result result = FFmpegLocator.resolve();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[FFmpeg] 使用 {} ({})", result.path(), result.source());
        }
        return result.path();
    }
}
