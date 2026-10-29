package com.director.export.video;

import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.camera.CameraViewportProfile;
import com.director.timeline.model.camera.ViewportMath;
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
 * 视频导出抓帧管线（v1：基于主 Framebuffer）。
 *
 * <p>流程：后台线程请求时间点 → 客户端线程驱动 {@link TimelinePlaybackSampler}
 * 与相机 → 当帧世界渲染完成后从主 Framebuffer 抓取 {@link NativeImage} →
 * 交给 {@link VideoEncoder}（MP4 或 PNG 序列）。</p>
 *
 * <p>复用主窗口世界画面，在 HUD 前截图并按相机画幅缩放。输出分辨率可以调整，
 * 原始画面细节仍受主窗口 Framebuffer 分辨率约束。</p>
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
        final CompletableFuture<NativeImage> future = new CompletableFuture<>();


        CaptureRequest(float timeSeconds, VideoExportSettings settings) {
            this.timeSeconds = timeSeconds;
            this.settings = settings;
        }
    }

    private final FrameCaptureQueue<CaptureRequest> queue = new FrameCaptureQueue<>();

    private VideoRenderPipeline() {
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
                    NativeImage image = req.future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)),
                            TimeUnit.NANOSECONDS);
                    delivered = true;
                    return image;
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
                req.future.thenAccept(NativeImage::close);
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
        if (head == null || head.future.isDone()) {
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
        TimelinePlaybackSampler.sampleWorldAt(timeSeconds);
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
        final CameraViewportProfile cameraProfile = profile;

        return (NativeImage img) -> {
            NativeImage out = null;
            boolean sourceTransferred = false;
            try {
                if (req.future.isDone()) return;
                float targetAspect = (float) targetW / targetH;
                if (img.getWidth() == targetW && img.getHeight() == targetH
                        && (cameraProfile == null || Math.abs(cameraProfile.getAspectRatio() - targetAspect) < 0.0001f)) {
                    // Transfer the original screenshot when no framing or scaling is needed.
                    sourceTransferred = req.future.complete(img);
                    return;
                }
                // Apply camera framing even when source and target sizes match.
                out = resizeToTarget(img, targetW, targetH, cameraProfile);
                if (!req.future.complete(out)) out.close();
                out = null;
            } catch (Exception error) {
                if (out != null) out.close();
                req.future.completeExceptionally(error);
            } finally {
                if (!sourceTransferred) img.close();
            }
        };
    }

    private static NativeImage resizeToTarget(NativeImage src, int targetW, int targetH,
            CameraViewportProfile cameraProfile) {
        NativeImage output = new NativeImage(targetW, targetH, false);
        try {
            fillTarget(src, output, targetW, targetH, cameraProfile);
            return output;
        } catch (RuntimeException | Error error) {
            output.close();
            throw error;
        }
    }

    private static void fillTarget(NativeImage src, NativeImage output, int targetW, int targetH,
            CameraViewportProfile cameraProfile) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        output.fillRect(0, 0, targetW, targetH, 0xFF000000);

        float cameraAspect = (cameraProfile != null) ? cameraProfile.getAspectRatio() : 0f;
        if (cameraAspect <= 0f) {
            cameraAspect = (float) targetW / (float) targetH;
        }

        CameraViewportProfile.PreviewScalePolicy scalePolicy = (cameraProfile != null)
                ? cameraProfile.getPreviewScalePolicy()
                : CameraViewportProfile.PreviewScalePolicy.FIT;

        ViewportMath.ViewportRects viewport = (scalePolicy == CameraViewportProfile.PreviewScalePolicy.FIT)
                ? ViewportMath.computeFit(0, 0, targetW, targetH, cameraAspect)
                : ViewportMath.computeFill(0, 0, targetW, targetH, cameraAspect);

        int innerX = Math.round(viewport.innerX);
        int innerY = Math.round(viewport.innerY);
        int innerW = Math.round(viewport.innerW);
        int innerH = Math.round(viewport.innerH);

        if (innerW <= 0 || innerH <= 0)
            return;

        float srcAspect = (float) srcW / (float) srcH;
        int cropX = 0, cropY = 0, cropW = srcW, cropH = srcH;
        if (Math.abs(srcAspect - cameraAspect) > 0.0001f) {
            if (srcAspect > cameraAspect) {
                cropW = Math.round(srcH * cameraAspect);
                cropX = (srcW - cropW) / 2;
            } else {
                cropH = Math.round(srcW / cameraAspect);
                cropY = (srcH - cropH) / 2;
            }
        }

        if (srcW == targetW && srcH == targetH && cropW == srcW && cropH == srcH
                && innerX == 0 && innerY == 0 && innerW == targetW && innerH == targetH) {
            output.copyFrom(src);
            return;
        }

        float scaleX = (float) cropW / (float) innerW;
        float scaleY = (float) cropH / (float) innerH;

        // Precompute horizontal sampling indices once per image.
        int firstX = Math.max(0, -innerX);
        int endX = Math.min(innerW, targetW - innerX);
        int[] sourceColumns = new int[Math.max(0, endX - firstX)];
        for (int dx = firstX; dx < endX; dx++)
            sourceColumns[dx - firstX] = cropX + Math.min(cropW - 1, Math.round(dx * scaleX));
        for (int dy = 0; dy < innerH; dy++) {
            int dstY = innerY + dy;
            if (dstY < 0 || dstY >= targetH)
                continue;
            int srcY = cropY + Math.min(cropH - 1, Math.round(dy * scaleY));
            for (int dx = firstX; dx < endX; dx++) {
                int dstX = innerX + dx;
                int srcX = sourceColumns[dx - firstX];
                output.setColorArgb(dstX, dstY, src.getColorArgb(srcX, srcY));
            }
        }

    }
}
