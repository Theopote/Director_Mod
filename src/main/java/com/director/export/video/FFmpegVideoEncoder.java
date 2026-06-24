package com.director.export.video;

import com.director.mixin.accessor.NativeImageAccessor;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        if (imgW != width || imgH != height) {
            throw new IOException("帧尺寸不匹配：got " + imgW + "x" + imgH
                    + ", expected " + width + "x" + height);
        }

        // 🚀 优化：不再使用 image.copyPixelsArgb()，因为它每帧都会分配一个新的大 int[] (4K = 33MB)
        // 改为直接读取像素并写入复用的字节缓冲区。
        int p = 0;
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                // getColor(x, y) 在 NativeImage 中返回的是 0xAABBGGRR (小端序直接读 int)
                // 或者 0xAARRGGBB，取决于具体实现。
                // 1.21.x NativeImage.getColor(x, y) 返回的是 0xAABBGGRR
                int c = ((NativeImageAccessor) (Object) image).invokeGetColor(x, y);

                // 我们需要输出 RGBA 给 FFmpeg (-pix_fmt rgba)
                // c = 0xAABBGGRR
                byte r = (byte) (c & 0xFF);
                byte g = (byte) ((c >>> 8) & 0xFF);
                byte b = (byte) ((c >>> 16) & 0xFF);
                byte a = (byte) ((c >>> 24) & 0xFF);

                rgbaFrameBuffer[p++] = r;
                rgbaFrameBuffer[p++] = g;
                rgbaFrameBuffer[p++] = b;
                rgbaFrameBuffer[p++] = a;
            }
        }

        ffmpegStdin.write(rgbaFrameBuffer);
        // 建议在每几帧 flush 一次，或者完全靠管道缓冲区。
        // 30 帧 (约 1s @ 30fps) flush 一次较为合适。
        if (frameIndex % 30 == 0) {
            ffmpegStdin.flush();
        }
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
        }, "Director-FFmpeg-LogDrain");
        t.setDaemon(true);
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
