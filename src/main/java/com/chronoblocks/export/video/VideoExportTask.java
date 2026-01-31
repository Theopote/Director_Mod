package com.chronoblocks.export.video;

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
            float duration = Math.max(0.001f, endTime - startTime);
            int totalFrames = Math.max(1, Math.round(duration * Math.max(1, settings.fps)));

            notifyProgress(0f, "准备导出，总帧数：" + totalFrames);

            // 1) 导出音频（可选，仅 MP4 有意义）
            if (settings.format == VideoExportSettings.FORMAT_H264_MP4 && settings.enableAudio) {
                notifyProgress(0f, "导出音频轨道...");
                AudioExporter audioExporter = new TimelineAudioExporter();
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
            encoder.init(settings, totalFrames);

            VideoRenderPipeline pipeline = VideoRenderPipeline.getInstance();

            for (int frameIndex = 0; frameIndex < totalFrames; frameIndex++) {
                manager.checkCanceled();

                float t = startTime + (frameIndex / (float) settings.fps);

                // Note: The NativeImage returned is managed by VideoRenderPipeline (cached
                // shared buffer).
                // Do NOT use try-with-resources here, otherwise the buffer will be closed
                // prematurely.
                NativeImage image = pipeline.renderFrame(t, settings);
                encoder.encodeFrame(image, frameIndex);

                float progress = (frameIndex + 1) / (float) totalFrames;
                notifyProgress(progress, "渲染帧 " + (frameIndex + 1) + " / " + totalFrames);
            }

            encoder.finish();
            success = true;
            notifyProgress(1f, "导出完成。");

        } catch (CancellationException ex) {
            error = "导出已取消。";
            if (encoder instanceof FFmpegVideoEncoder ffmpeg) {
                ffmpeg.abort();
            }
        } catch (Exception ex) {
            LOGGER.error("[VideoExportTask] 导出失败", ex);
            error = "导出失败：" + ex.getMessage();
            if (encoder instanceof FFmpegVideoEncoder ffmpeg) {
                ffmpeg.abort();
            }
        } finally {
            // 不在 finally 重复 finish：避免对 FFmpeg 二次 waitFor 导致异常
            // 🚀 清理管线缓存的 NativeImage 缓冲区
            VideoRenderPipeline.clearCache();

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

    private float resolveEndTime() {
        // 注意：core.PlaybackController 的 duration 来自 AnimationTimelineService 的可播放图，
        // 在“仅编辑 UI 时间线”但尚未同步到 core 播放图时可能为 0，从而导致导出被回退为 10s（=30fps时 300 帧）。
        float playbackDuration = com.chronoblocks.core.playback.PlaybackController.get().getDuration();

        // UI 时间线（TimelinePanel 初始化时会注入到 ContainerTimelineService）
        float uiTimelineDuration = 0f;
        com.chronoblocks.ui.timeline.model.TimelineModel model = com.chronoblocks.ui.timeline.container.ContainerTimelineService
                .getInstance().getTimelineModel();
        if (model != null) {
            uiTimelineDuration = model.getDuration();
        }

        float fallback = 10f;
        float best = Math.max(fallback, Math.max(playbackDuration, uiTimelineDuration));

        return switch (settings.rangeMode) {
            case VideoExportSettings.RANGE_CUSTOM -> settings.customEndTime;
            case VideoExportSettings.RANGE_TIMELINE -> uiTimelineDuration > 0f ? uiTimelineDuration : best;
            default -> best;
        };
    }
}
