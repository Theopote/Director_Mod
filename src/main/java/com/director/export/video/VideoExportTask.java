package com.director.export.video;

import com.director.timeline.playback.TimelinePlaybackSampler;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

/**
 * 视频导出任务（后台线程）
 *
 * <p>
 * 职责：
 * </p>
 * - 解析时间范围与总帧数
 * - 逐帧驱动时间线与相机（在客户端线程）
 * - 抓帧 -> 交给编码器写出
 * - 上报进度/完成/失败
 */
public final class VideoExportTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoExportTask.class);

    private final VideoExportSettings settings;
    private final VideoExportManager.ProgressListener listener;
    private final VideoExportManager manager;

    public VideoExportTask(VideoExportSettings settings,
            VideoExportManager.ProgressListener listener,
            VideoExportManager manager) {
        this.settings = settings;
        this.listener = listener;
        this.manager = manager;
    }

    @Override
    public void run() {
        boolean success = false;
        String error = null;
        Path audioWav = null;

        VideoEncoder encoder = null;
        try {
            manager.checkCanceled();

            float startTime = resolveStartTime();
            float endTime = resolveEndTime();
            int totalFrames = VideoExportSettings.frameCount(startTime, endTime, settings.fps);

            notifyProgress(0f, "准备导出，总帧数：" + totalFrames);

            // 1) 导出音频（可选，仅 MP4 有意义）
            if (settings.format == VideoExportSettings.FORMAT_H264_MP4 && settings.enableAudio) {
                notifyProgress(0f, "导出音频轨道...");
                AudioExporter audioExporter = new TimelineAudioExporter(manager::checkCanceled);
                audioWav = audioExporter.exportWav(settings, startTime, endTime);
            }

            // 编码器：根据 format 选择
            if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
                FFmpegVideoEncoder ffmpeg = new FFmpegVideoEncoder();
                if (audioWav != null) {
                    ffmpeg.setAudioWavPath(audioWav);
                }
                encoder = ffmpeg;
            } else {
                encoder = new PngSequenceEncoder();
            }
            if (encoder instanceof FFmpegVideoEncoder ffmpeg) manager.registerEncoder(ffmpeg);
            manager.checkCanceled();
            encoder.init(settings, totalFrames);

            VideoRenderPipeline pipeline = VideoRenderPipeline.getInstance();

            for (int frameIndex = 0; frameIndex < totalFrames; frameIndex++) {
                manager.checkCanceled();

                float t = startTime + (frameIndex / (float) settings.fps);

                // Each capture transfers ownership to this worker.
                try (NativeImage image = pipeline.renderFrame(t, settings)) {
                    manager.checkCanceled();
                    encoder.encodeFrame(image, frameIndex);
                }

                float progress = (frameIndex + 1) / (float) totalFrames;
                notifyProgress(progress, "渲染帧 " + (frameIndex + 1) + " / " + totalFrames);
            }

            manager.checkCanceled();
            notifyProgress(1f, "正在完成编码...");
            encoder.finish();
            // A successful publication is the completion point; a later cancel cannot undo it.
            success = true;
            notifyProgress(1f, "导出完成。");

        } catch (CancellationException ex) {
            error = "导出已取消。";
        } catch (Exception ex) {
            LOGGER.error("[VideoExportTask] 导出失败", ex);
            error = manager.isCancelRequested() ? "导出已取消。" : "导出失败：" + ex.getMessage();
        } finally {
            // Abort on the worker only; this is also responsible for releasing encoder resources.
            if (!success && encoder != null) encoder.abort();
            manager.registerEncoder(null);


            // 清理临时音频文件（可按需改为保留）
            if (audioWav != null) {
                try {
                    Files.deleteIfExists(audioWav);
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }

        if (listener != null) {
            listener.onCompleted(success, error);
        }
    }

    private void notifyProgress(float progress, String msg) {
        if (listener != null) {
            listener.onProgress(progress, msg);
        }
    }

    private float resolveStartTime() {
        return switch (settings.rangeMode) {
            case VideoExportSettings.RANGE_CUSTOM -> settings.customStartTime;
            default -> 0f;
        };
    }

    private static final float DEFAULT_DURATION_FALLBACK_SEC = 10f;

    private float resolveEndTime() {
        float timelineDuration = TimelinePlaybackSampler.resolveDuration(DEFAULT_DURATION_FALLBACK_SEC);

        return switch (settings.rangeMode) {
            case VideoExportSettings.RANGE_CUSTOM -> settings.customEndTime;
            case VideoExportSettings.RANGE_TIMELINE -> timelineDuration;
            default -> timelineDuration;
        };
    }
}
