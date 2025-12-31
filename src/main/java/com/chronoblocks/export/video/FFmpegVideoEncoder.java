package com.chronoblocks.export.video;

import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FFmpeg MP4 编码器（stdin 管道写入 raw RGBA）
 *
 * <p>不产生临时 PNG 序列，直接输出 MP4（H.264）。</p>
 *
 * <p>要求：系统可执行 {@code ffmpeg}（Windows: ffmpeg.exe）在 PATH 中，
 * 或通过系统属性 {@code chronoblocks.ffmpeg} 指定完整路径。</p>
 */
public final class FFmpegVideoEncoder implements VideoEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FFmpegVideoEncoder.class);

    private Process ffmpegProcess;
    private OutputStream ffmpegStdin;

    private int width;
    private int height;
    private Path audioWavPath;

    /** 复用的帧缓冲（RGBA bytes） */
    private byte[] rgbaFrameBuffer;

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

        String ffmpeg = detectFFmpegBinary();

        // ffmpeg -y -f rawvideo -vcodec rawvideo -pix_fmt rgba -s WxH -r FPS -i - (-i audio.wav -map ...) -c:v libx264 ... out.mp4
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
        cmd.add("-preset");
        cmd.add("slow");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");

        if (hasAudio) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            cmd.add("-shortest");
        }
        cmd.add(outputPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        LOGGER.info("[FFmpeg] Starting: {}", String.join(" ", cmd));
        ffmpegProcess = pb.start();

        // 防止 ffmpeg 输出填满缓冲区导致阻塞：后台吞日志
        startDrainThread(ffmpegProcess.getInputStream());

        ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 1024 * 1024);
        rgbaFrameBuffer = new byte[width * height * 4];
    }

    @Override
    public void encodeFrame(NativeImage image, int frameIndex) throws IOException {
        if (ffmpegStdin == null || ffmpegProcess == null) {
            throw new IOException("FFmpegVideoEncoder 未初始化");
        }
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IOException("帧尺寸不匹配：got " + image.getWidth() + "x" + image.getHeight()
                    + ", expected " + width + "x" + height);
        }

        // NativeImage 提供 ARGB copy（int），这里转换为 RGBA bytes
        int[] argb = image.copyPixelsArgb();
        int p = 0;
        for (int c : argb) {
            // ARGB: 0xAARRGGBB
            int a = (c >>> 24) & 0xFF;
            int r = (c >>> 16) & 0xFF;
            int g = (c >>> 8) & 0xFF;
            int b = (c) & 0xFF;
            rgbaFrameBuffer[p++] = (byte) r;
            rgbaFrameBuffer[p++] = (byte) g;
            rgbaFrameBuffer[p++] = (byte) b;
            rgbaFrameBuffer[p++] = (byte) a;
        }

        ffmpegStdin.write(rgbaFrameBuffer);
    }

    @Override
    public void finish() throws IOException {
        // 正常结束：关闭 stdin，让 ffmpeg flush/finish
        closeStdin();
        waitForExitOrThrow();
    }

    /**
     * 取消/失败时的快速中止。
     */
    public void abort() {
        try {
            closeStdin();
        } catch (Exception ignore) {
            // ignore
        }
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            try {
                ffmpegProcess.destroyForcibly();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private void closeStdin() throws IOException {
        if (ffmpegStdin != null) {
            ffmpegStdin.flush();
            ffmpegStdin.close();
            ffmpegStdin = null;
        }
    }

    private void waitForExitOrThrow() throws IOException {
        if (ffmpegProcess == null) {
            return;
        }
        try {
            int exit = ffmpegProcess.waitFor();
            if (exit != 0) {
                throw new IOException("FFmpeg 编码失败（退出码 " + exit + "），请检查 ffmpeg 是否安装/可用。");
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
                    // 仅在 debug 时输出，避免刷屏
                    if (LOGGER.isDebugEnabled() && n > 0) {
                        LOGGER.debug("[FFmpeg] {}", new String(buf, 0, n));
                    }
                }
            } catch (Exception ignore) {
                // ignore
            }
        }, "ChronoBlocks-FFmpeg-LogDrain");
        t.setDaemon(true);
        t.start();
    }

    private String detectFFmpegBinary() {
        // 0) 允许开发者通过 JVM 属性显式指定（最高优先级）
        String override = System.getProperty("chronoblocks.ffmpeg");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }

        // 1) 优先查找游戏根目录（.minecraft）下的 ffmpeg 可执行文件
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String name = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
            Path local = gameDir.resolve(name);
            if (Files.exists(local)) {
                return local.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            // ignore and fallback
        }

        // 2) 回退：系统 PATH
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
    }
}


