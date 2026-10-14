package com.director.export.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频导出任务管理器。
 *
 * <p>在后台线程启动 {@link VideoExportTask}，协调取消/完成/失败回调与并发互斥。
 * 实际逐帧渲染与编码由 {@link VideoRenderPipeline}、{@link FFmpegVideoEncoder} /
 * {@link PngSequenceEncoder} 完成。</p>
 */
public final class VideoExportManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoExportManager.class);

    public interface ProgressListener {
        /**
         * @param progress01 0.0 ~ 1.0
         * @param message 当前状态描述
         */
        void onProgress(float progress01, String message);

        /**
         * @param success 是否成功
         * @param errorMessage 错误信息（成功时为 null）
         */
        void onCompleted(boolean success, String errorMessage);
    }

    private static final VideoExportManager INSTANCE = new VideoExportManager();

    public static VideoExportManager getInstance() {
        return INSTANCE;
    }

    private final AtomicBoolean exporting = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile Thread workerThread;

    private VideoExportManager() {}

    public boolean isExporting() {
        return exporting.get();
    }

    public void requestCancel() {
        cancelRequested.set(true);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    void checkCanceled() {
        if (cancelRequested.get()) {
            throw new CancellationException("导出任务被用户取消。");
        }
    }

    public synchronized void start(VideoExportSettings settings, ProgressListener listener) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(listener, "listener");

        if (exporting.get()) {
            LOGGER.warn("[VideoExport] 已有导出任务在运行，忽略 start()");
            // 重要：不要“静默忽略”，否则 UI 看起来像“点了没反应”
            listener.onCompleted(false, "已有导出任务正在运行，请先取消或等待完成。");
            return;
        }
        if (!settings.validate()) {
            listener.onCompleted(false, "导出设置不合法（validate 失败）");
            return;
        }

        cancelRequested.set(false);
        exporting.set(true);

        workerThread = new Thread(() -> {
            com.director.timeline.playback.camera.TimelineCameraController.ExportState cameraState = null;
            try {
                var client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null) cameraState = client.submit(() ->
                        com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                                .saveStateForExport()).get(10, java.util.concurrent.TimeUnit.SECONDS);
                LOGGER.info("[VideoExport] 开始导出任务：{}x{} @{}fps, format={}, output={}",
                        settings.width, settings.height, settings.fps, settings.format, settings.outputPath);
                VideoExportTask task = new VideoExportTask(settings, listener, this);
                task.run();
            } catch (Exception e) {
                LOGGER.error("[VideoExport] 导出任务异常", e);
                listener.onCompleted(false, "导出失败：" + e.getMessage());
            } finally {
                exporting.set(false);
                var state = cameraState;
                var client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null) client.execute(() -> {
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                            .restoreStateAfterExport(state);
                    com.director.client.RenderOverrideSnapshots.resyncAfterVideoExport();
                });
            }
        }, "Director-VideoExport");
        workerThread.setDaemon(true);
        workerThread.start();
    }
}


