package com.director.export.video;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.ui.timeline.playback.PlaybackOrchestrator;
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

        default void onCanceled(String message) { onCompleted(false, message); }
    }

    private static final VideoExportManager INSTANCE = new VideoExportManager();

    public static VideoExportManager getInstance() {
        return INSTANCE;
    }

    private final AtomicBoolean exporting = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile Thread workerThread;
    private FFmpegVideoEncoder activeEncoder;

    private record Outcome(boolean success, boolean canceled, String error) {}

    private VideoExportManager() {}

    public boolean isExporting() {
        return exporting.get();
    }

    public synchronized void requestCancel() {
        if (!exporting.get()) return;
        cancelRequested.set(true);
        if (activeEncoder != null) activeEncoder.requestAbort();
    }

    synchronized void registerEncoder(FFmpegVideoEncoder encoder) {
        activeEncoder = encoder;
        if (encoder != null && cancelRequested.get()) encoder.requestAbort();
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
            listener.onCompleted(false, settings.validationError());
            return;
        }

        final VideoExportSettings snapshot = settings.copy();
        cancelRequested.set(false);
        exporting.set(true);

        workerThread = new Thread(() -> {
            var savedCameraState = new java.util.concurrent.atomic.AtomicReference<
                    com.director.timeline.playback.camera.TimelineCameraController.ExportState>();
            var savedPlaybackState = new java.util.concurrent.atomic.AtomicReference<VideoExportPlaybackState>();
            var outcome = new java.util.concurrent.atomic.AtomicReference<>(new Outcome(false, false, "导出未完成"));
            try {
                var client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null) throw new IllegalStateException("客户端不可用");
                client.submit(() -> {
                    checkCanceled();
                    TimelineModel model = TimelinePlaybackSampler.resolveModel();
                    if (model == null) model = PlaybackOrchestrator.get().getBoundModel();
                    savedPlaybackState.set(VideoExportPlaybackState.capture(model));
                    savedCameraState.set(com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                            .saveStateForExport());
                    VideoExportPlaybackState playback = savedPlaybackState.get();
                    if (playback != null) playback.suspend();
                    PlaybackOrchestrator.get().onExportStarted();
                }).get(10, java.util.concurrent.TimeUnit.SECONDS);
                LOGGER.info("[VideoExport] 开始导出任务：{}x{} @{}fps, format={}, output={}",
                        snapshot.width, snapshot.height, snapshot.fps, snapshot.format, snapshot.outputPath);
                new VideoExportTask(snapshot, new ProgressListener() {
                    @Override public void onProgress(float progress, String message) {
                        listener.onProgress(progress, message);
                    }
                    @Override public void onCompleted(boolean success, String error) {
                        outcome.set(new Outcome(success, false, error));
                    }
                    @Override public void onCanceled(String message) {
                        outcome.set(new Outcome(false, true, message));
                    }
                }, this).run();
            } catch (Exception e) {
                LOGGER.error("[VideoExport] 导出任务异常", e);
                outcome.set(new Outcome(false, isCancelRequested(), isCancelRequested() ? "导出已取消。" : "导出失败：" + e.getMessage()));
            } finally {
                Runnable restore = () -> {
                    // Release ownership on the client thread, with admission locked until restoration ends.
                    synchronized (VideoExportManager.this) {
                        exporting.set(false);
                        activeEncoder = null;
                        Exception restoreFailure = null;
                        try {
                            VideoRenderPipeline.getInstance().releaseRenderTargets();
                        } catch (RuntimeException cleanup) {
                            restoreFailure = cleanup;
                            LOGGER.warn("[VideoExport] 离屏渲染目标清理失败", cleanup);
                        }
                        try {
                            com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                                    .restoreStateAfterExport(savedCameraState.get());
                        } catch (Exception e) {
                            if (restoreFailure == null) restoreFailure = e;
                            LOGGER.error("[VideoExport] 恢复相机状态失败", e);
                        }
                        try {
                            VideoExportPlaybackState playback = savedPlaybackState.get();
                            if (playback != null) playback.restore();
                        } catch (Exception e) {
                            if (restoreFailure == null) restoreFailure = e;
                            LOGGER.error("[VideoExport] 恢复播放态失败", e);
                        }
                        try {
                            PlaybackOrchestrator.get().onExportFinished();
                        } catch (Exception e) {
                            if (restoreFailure == null) restoreFailure = e;
                            LOGGER.error("[VideoExport] 重置导出播放协调器失败", e);
                        }
                        try {
                            com.director.client.RenderOverrideSnapshots.resyncAfterVideoExport();
                        } catch (Exception e) {
                            if (restoreFailure == null) restoreFailure = e;
                            LOGGER.error("[VideoExport] 重同步渲染覆盖失败", e);
                        }
                        if (restoreFailure != null) {
                            outcome.set(new Outcome(false, false, "导出收尾失败：" + restoreFailure.getMessage()));
                        }
                    }
                    Outcome result = outcome.get();
                    if (result.canceled()) listener.onCanceled(result.error());
                    else listener.onCompleted(result.success(), result.error());
                };
                var client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null) {
                    try {
                        client.execute(restore);
                    } catch (Exception e) {
                        LOGGER.error("[VideoExport] 无法提交客户端收尾任务", e);
                        outcome.set(new Outcome(false, false, "导出收尾失败：" + e.getMessage()));
                        exporting.set(false);
                        activeEncoder = null;
                        Outcome result = outcome.get();
                        if (result.canceled()) listener.onCanceled(result.error());
                        else listener.onCompleted(result.success(), result.error());
                    }
                } else {
                    exporting.set(false);
                    activeEncoder = null;
                    Outcome result = outcome.get();
                    if (result.canceled()) listener.onCanceled(result.error());
                    else listener.onCompleted(result.success(), result.error());
                }
            }
        }, "Director-VideoExport");
        workerThread.setDaemon(true);
        workerThread.start();
    }
}


