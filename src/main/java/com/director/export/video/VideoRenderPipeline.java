package com.director.export.video;

import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.camera.CameraViewportProfile;
import com.director.timeline.playback.camera.TimelineCameraController;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 视频导出抓帧管线：窗口画面或独立分辨率渲染目标。
 *
 * <p>流程：后台线程请求时间点 → 客户端线程驱动 {@link TimelinePlaybackSampler}
 * 与相机 → 当帧世界渲染完成后抓取原图 → 后台裁切缩放 →
 * 交给 {@link VideoEncoder}（MP4 或 PNG 序列）。</p>
 *
 * <p>可复用窗口画面，或在单次世界渲染期间临时使用输出尺寸的离屏目标。
 * HUD 前截图，在目标恢复成功后交给后台按相机画幅裁切缩放。</p>
 */
public final class VideoRenderPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoRenderPipeline.class);

    private static final VideoRenderPipeline INSTANCE = new VideoRenderPipeline();

    public static VideoRenderPipeline getInstance() {
        return INSTANCE;
    }

    private static final class CaptureRequest {
        final float timeSeconds;
        final VideoExportSettings settings;
        final CompletableFuture<CapturedVideoFrame> future = new CompletableFuture<>();
        CompletableFuture<Void> renderPass;


        CaptureRequest(float timeSeconds, VideoExportSettings settings) {
            this.timeSeconds = timeSeconds;
            this.settings = settings;
        }
    }

    private final FrameCaptureQueue<CaptureRequest> queue = new FrameCaptureQueue<>();

    private final OffscreenVideoRenderer offscreen = new OffscreenVideoRenderer();
    private CaptureRequest activeWorldRequest;

    private VideoRenderPipeline() {
    }

    /** Wraps the complete world pass so render state is restored even when the vanilla pass throws. */
    public void renderWorld(RenderTickCounter ticks, Runnable worldRender) {
        CaptureRequest request = queue.peek();
        if (request == null || request.future.isDone()) {
            worldRender.run();
            return;
        }
        if (activeWorldRequest != null) throw new IllegalStateException("不支持嵌套的导出世界渲染");
        activeWorldRequest = request;
        CompletableFuture<Void> pass = new CompletableFuture<>();
        request.renderPass = pass;
        boolean[] world = {false, false}; // entered, completed
        Runnable render = () -> {
            world[0] = true;
            worldRender.run();
            world[1] = true;
        };
        try {
            if (request.settings.nativeResolution)
                offscreen.render(request.settings.width, request.settings.height, ticks, render);
            else render.run();
            pass.complete(null);
        } catch (RuntimeException failure) {
            pass.completeExceptionally(failure);
            request.future.completeExceptionally(new IllegalStateException("导出世界渲染失败：" + failure.getMessage(), failure));
            queue.remove(request);
            LOGGER.error("[VideoExport] 世界渲染失败，已恢复窗口渲染目标", failure);
            // Do not hide vanilla world-render failures: its internal matrix/profiler stacks may be incomplete.
            if (world[0] && !world[1]) throw failure;
            if (!world[0]) worldRender.run();
        } catch (Error failure) {
            pass.completeExceptionally(failure);
            request.future.completeExceptionally(failure);
            queue.remove(request);
            throw failure;
        } finally {
            activeWorldRequest = null;
        }
    }

    public void releaseRenderTargets() {
        offscreen.close();
    }

    /**
     * 请求渲染并抓取某个时间点的一帧。
     *
     * <p>
     * 调用线程：后台导出线程。
     * </p>
     * <p>
     * 机制：世界渲染 HEAD 设置时间线/相机，TAIL 抓取同一帧，future 转移图像所有权。
     * </p>
     */
    public NativeImage renderFrame(float timeSeconds, VideoExportSettings settings)
            throws ExecutionException, InterruptedException {
        Objects.requireNonNull(settings, "settings");

        CaptureRequest req = new CaptureRequest(timeSeconds, settings);
        queue.add(req);

        boolean delivered = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (true) {
                VideoExportManager.getInstance().checkCanceled();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException("渲染抓帧超时（10s）");
                try {
                    try (CapturedVideoFrame frame = req.future.get(
                            Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)) {
                        delivered = true;
                        VideoExportManager.getInstance().checkCanceled();
                        return frame.takeImage();
                    }
                } catch (TimeoutException polling) {
                    if (System.nanoTime() >= deadline) throw polling;
                }
            }
        } catch (TimeoutException te) {
            throw new ExecutionException("渲染抓帧超时（10s），请检查世界渲染是否仍在运行。", te);
        } finally {
            queue.remove(req);
            if (!delivered) {
                // Covers completion racing with timeout, interruption or cancellation.
                req.future.thenAccept(CapturedVideoFrame::close);
                req.future.completeExceptionally(new java.util.concurrent.CancellationException());
            }
        }
    }

    /**
     * 旧实现的兼容入口：保留为 no-op（避免外部仍调用导致逻辑重复）。
     */
    public void processPendingCaptures() {
        // intentionally empty
    }

    /**
     * Hook：在 GameRenderer.renderWorld(HEAD) 调用。
     * 这里做“驱动时间线 + 应用摄像机”，让当帧世界渲染即为导出帧内容。
     */
    public void onRenderWorldHead(RenderTickCounter tickCounter) {
        CaptureRequest head = queue.beginPreparation();
        if (head == null || head != activeWorldRequest || head.future.isDone()) {
            return;
        }
        try {
            applyCamera(head.timeSeconds, head.settings);
            applyTimelineTime(head.timeSeconds);
            queue.markPrepared(head);
        } catch (Exception e) {
            head.future.completeExceptionally(e);
        }
    }

    /**
     * Hook：在 GameRenderer.renderWorld(TAIL) 调用（此时 HUD 尚未绘制）。
     * 截取主 framebuffer 的世界画面，按相机画幅裁切缩放后输出。
     */
    public void onRenderWorldTail() {
        CaptureRequest head = queue.takePrepared();
        if (head == null) {
            return;
        }
        if (head.future.isDone()) {
            queue.remove(head);
            return;
        }

        // Light changes (including switching off) are baked into terrain meshes.
        // Wait at the requested time until visible rebuilds/uploads settle.
        if (com.director.lighting.PointLightRuntime.awaitTerrain()) return;
        if (com.director.season.SeasonWorldEffects.awaitTerrain()) return;
        try {
            captureMainFramebuffer(head);
        } catch (Exception e) {
            head.future.completeExceptionally(e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[VideoExport] frame captured from main framebuffer ({}x{})",
                        head.settings.width, head.settings.height);
            }
            queue.remove(head);
        }
    }

    private void applyTimelineTime(float timeSeconds) {
        TimelinePlaybackSampler.sampleWorldForExport(timeSeconds);
    }

    private void applyCamera(float timeSeconds, VideoExportSettings settings) {
        if (settings.cameraMode == VideoExportSettings.CAMERA_PLAYER) {
            TimelineCameraController.getInstance().preparePlayerCameraForExport();
            return;
        }

        TimelineModel model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        List<TimelineTrack> cameraTracks = getCameraTracks(model);
        if (cameraTracks.isEmpty()) {
            TimelineCameraController.getInstance().preparePlayerCameraForExport();
            return;
        }

        int idx = Math.max(0, Math.min(settings.cameraTrackIndex, cameraTracks.size() - 1));
        TimelineTrack track = cameraTracks.get(idx);

        TimelineCameraController cameraController = TimelineCameraController.getInstance();
        cameraController.init(model); // 确保已绑定（重复调用安全）
        cameraController.setActiveCameraTrack(track);
        // 导出模式必须“严格确定”：不能把采样写到 player 实体上让渲染用 tickDelta 插值，否则会出现逐帧抖动。
        // 这里让 CameraRuntime 进入 TIMELINE owner，由 CameraMixin 在 Camera.update(TAIL)
        // 直接覆盖相机位置/旋转/FOV。
        cameraController.applySampleForExport(timeSeconds);
    }

    private static List<TimelineTrack> getCameraTracks(TimelineModel model) {
        List<TimelineTrack> result = new ArrayList<>();
        for (TimelineTrack t : model.getTracks()) {
            if (t != null && t.getType() == TrackType.CAMERA) {
                result.add(t);
            }
        }
        return result;
    }

    private static void captureMainFramebuffer(CaptureRequest req) {
        RenderSystem.assertOnRenderThread();

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Consumer<NativeImage> consumer = getNativeImageConsumer(req, main);
        ScreenshotRecorder.takeScreenshot(main, consumer);
    }

    private static @NotNull Consumer<NativeImage> getNativeImageConsumer(CaptureRequest req, Framebuffer main) {
        if (main == null) {
            throw new IllegalStateException("client.getFramebuffer() == null");
        }

        int targetW = Math.max(1, req.settings.width);
        int targetH = Math.max(1, req.settings.height);

        CameraViewportProfile profile = null;
        if (req.settings.cameraMode == VideoExportSettings.CAMERA_TRACK) {
            TimelineModel model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
            if (model != null) {
                List<TimelineTrack> cameraTracks = getCameraTracks(model);
                if (!cameraTracks.isEmpty()) {
                    int idx = Math.max(0, Math.min(req.settings.cameraTrackIndex, cameraTracks.size() - 1));
                    TimelineTrack track = cameraTracks.get(idx);
                    if (track != null) {
                        profile = track.getCameraViewportProfile();
                    }
                }
            }
        }
        // Freeze mutable editor framing before the asynchronous screenshot callback runs.
        final float cameraAspect = profile == null ? 0 : profile.getAspectRatio();
        final CameraViewportProfile.PreviewScalePolicy scalePolicy = profile == null
                ? CameraViewportProfile.PreviewScalePolicy.FIT : profile.getPreviewScalePolicy();

        CompletableFuture<Void> renderPass = req.renderPass;
        return (NativeImage img) -> {
            CapturedVideoFrame captured = new CapturedVideoFrame(img, targetW, targetH, cameraAspect, scalePolicy);
            RenderPassHandoff.completeAfter(req.future, renderPass, captured, CapturedVideoFrame::close);
        };
    }

}
