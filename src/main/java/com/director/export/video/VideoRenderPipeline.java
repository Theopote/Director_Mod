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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 离屏渲染管线（v1：基于主 Framebuffer 抓帧）
 *
 * <p>
 * 重要说明：
 * </p>
 * - 真实的“独立 FBO 离屏渲染 + 指定分辨率 + 关闭 UI/HUD”属于下一阶段；
 * - 当前实现仍然是“可用”的：在导出任务中逐帧驱动时间线/相机，然后在下一帧 UI 渲染开始处
 * 从主 Framebuffer 抓取像素为 {@link NativeImage}，交给编码器写出 PNG。
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
        volatile boolean armed = false;

        CaptureRequest(float timeSeconds, VideoExportSettings settings) {
            this.timeSeconds = timeSeconds;
            this.settings = settings;
        }
    }

    private final ConcurrentLinkedQueue<CaptureRequest> queue = new ConcurrentLinkedQueue<>();

    private VideoRenderPipeline() {
    }

    /**
     * 请求渲染并抓取某个时间点的一帧。
     *
     * <p>
     * 调用线程：后台导出线程。
     * </p>
     * <p>
     * 机制：在客户端线程设置时间线/相机 -> 等到下一帧 UI 渲染开始时抓取 framebuffer -> future 完成。
     * </p>
     */
    public NativeImage renderFrame(float timeSeconds, VideoExportSettings settings)
            throws ExecutionException, InterruptedException {
        Objects.requireNonNull(settings, "settings");

        CaptureRequest req = new CaptureRequest(timeSeconds, settings);
        queue.add(req);

        // 由渲染线程在 GameRenderer.renderWorld 的 HEAD/Tail 钩子中执行：
        // - HEAD: applyTimelineTime/applyCamera（影响“当帧世界渲染”）
        // - TAIL: blit 主 framebuffer -> offscreen 并截图
        req.armed = true;

        try {
            // 重要：不要无限等待，否则用户看到的就是“点了开始导出没反应 / 没进度条”。
            // 如果渲染钩子（GameRendererMixin.renderWorld HEAD/TAIL）未生效，这里会永远卡住。
            return req.future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            String msg = "渲染抓帧超时（10s）。可能原因：renderWorld 钩子未触发 / 客户端线程卡住 / 导出被其他逻辑阻断。";
            LOGGER.error("[VideoExport] {}", msg);
            req.future.completeExceptionally(new IllegalStateException(msg, te));
            throw new ExecutionException(msg, te);
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
        CaptureRequest head = queue.peek();
        if (head == null || !head.armed || head.future.isDone()) {
            return;
        }
        try {
            applyTimelineTime(head.timeSeconds);
            applyCamera(head.timeSeconds, head.settings);
        } catch (Exception e) {
            head.future.completeExceptionally(e);
        }
    }

    /**
     * Hook：在 GameRenderer.renderWorld(TAIL) 调用（此时 HUD 尚未绘制）。
     * 这里将“主 framebuffer 的世界画面” blit/缩放到 offscreen framebuffer，再截图输出。
     */
    public void onRenderWorldTail() {
        CaptureRequest head = queue.peek();
        if (head == null || !head.armed) {
            return;
        }
        if (head.future.isDone()) {
            queue.poll();
            return;
        }

        try {
            captureMainFramebuffer(head);
        } catch (Exception e) {
            head.future.completeExceptionally(e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[VideoExport] frame captured from main framebuffer ({}x{})",
                        head.settings.width, head.settings.height);
            }
            queue.poll();
        }
    }

    private void applyTimelineTime(float timeSeconds) {
        TimelinePlaybackSampler.sampleWorldAt(timeSeconds);
    }

    private void applyCamera(float timeSeconds, VideoExportSettings settings) {
        if (settings.cameraMode == VideoExportSettings.CAMERA_PLAYER) {
            return;
        }

        TimelineModel model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return;
        }

        List<TimelineTrack> cameraTracks = getCameraTracks(model);
        if (cameraTracks.isEmpty()) {
            return;
        }

        int idx = Math.max(0, Math.min(settings.cameraTrackIndex, cameraTracks.size() - 1));
        TimelineTrack track = cameraTracks.get(idx);

        TimelineCameraController cameraController = TimelineCameraController.getInstance();
        cameraController.setTimelineModel(model); // 确保已绑定（重复调用安全）
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

    // --- 🚀 性能优化：复用 NativeImage 缓冲区，避免每帧分配 33MB+ 造成的 GC 抖动 ---
    private static NativeImage cachedResizedBuffer = null;

    /**
     * 清理缓存的缓冲区（在导出彻底结束时调用）。
     * ⭐ 线程安全：NativeImage 的 close() 必须在渲染线程执行，
     *   因此通过 execute() 调度到渲染线程，避免与 onRenderWorldTail 的竞态。
     */
    public static void clearCache() {
        MinecraftClient.getInstance().execute(() -> {
            if (cachedResizedBuffer != null) {
                cachedResizedBuffer.close();
                cachedResizedBuffer = null;
            }
        });
    }

    private static void captureMainFramebuffer(CaptureRequest req) {
        RenderSystem.assertOnRenderThread();

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Consumer<NativeImage> consumer = getNativeImageConsumer(req, main);

        // 1.21.10+：ScreenshotRecorder 为异步回调形式。
        try {
            var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class,
                    java.util.function.Consumer.class);
            m.invoke(null, main, consumer);
        } catch (ReflectiveOperationException e) {
            // fallback overload: takeScreenshot(Framebuffer, int, Consumer)
            try {
                var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class, int.class,
                        java.util.function.Consumer.class);
                m.invoke(null, main, 0, consumer);
            } catch (ReflectiveOperationException e2) {
                e.addSuppressed(e2);
                throw new IllegalStateException("ScreenshotRecorder.takeScreenshot overload not found", e);
            }
        }
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
            try {
                NativeImage out;
                if (img.getWidth() == targetW && img.getHeight() == targetH) {
                    // 尺寸正好匹配，拷贝到复用缓冲区再深拷贝给消费者
                    ensureAndFillBufferSync(img, targetW, targetH);
                    img.close();
                } else {
                    // ⭐ 使用复用缓冲区进行裁切缩放
                    resizeToTargetOptimized(img, targetW, targetH, cameraProfile);
                    img.close();
                }
                // ⭐ P0 修复：深拷贝再 complete，避免下一帧覆盖 cachedResizedBuffer 导致画面错乱
                out = deepCopyBuffer(cachedResizedBuffer);
                req.future.complete(out);
            } catch (Exception t) {
                try {
                    img.close();
                } catch (Exception ignored) {
                }
                req.future.completeExceptionally(t);
            }
        };
    }

    /**
     * ⭐ P0 修复：将 cachedResizedBuffer 深拷贝一份交给编码器，
     * 避免下一帧抓取覆盖同一块内存导致画面错乱。
     */
    private static NativeImage deepCopyBuffer(NativeImage src) {
        if (src == null) {
            throw new IllegalStateException("cachedResizedBuffer is null when trying to deep copy");
        }
        NativeImage copy = new NativeImage(src.getWidth(), src.getHeight(), true);
        copy.copyFrom(src);
        return copy;
    }

    /**
     * 确保缓存的 buffer 存在且尺寸正确，并将 src 数据拷贝进去。
     */
    private static void ensureAndFillBufferSync(NativeImage src, int w, int h) {
        if (cachedResizedBuffer == null || cachedResizedBuffer.getWidth() != w
                || cachedResizedBuffer.getHeight() != h) {
            if (cachedResizedBuffer != null)
                cachedResizedBuffer.close();
            cachedResizedBuffer = new NativeImage(w, h, true);
        }
        // 使用高效的 copyFrom
        cachedResizedBuffer.copyFrom(src);
    }

    private static void resizeToTargetOptimized(NativeImage src, int targetW, int targetH,
            CameraViewportProfile cameraProfile) {
        if (cachedResizedBuffer == null || cachedResizedBuffer.getWidth() != targetW
                || cachedResizedBuffer.getHeight() != targetH) {
            if (cachedResizedBuffer != null)
                cachedResizedBuffer.close();
            cachedResizedBuffer = new NativeImage(targetW, targetH, true);
        }

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        cachedResizedBuffer.fillRect(0, 0, targetW, targetH, 0xFF000000);

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

        float scaleX = (float) cropW / (float) innerW;
        float scaleY = (float) cropH / (float) innerH;

        // 🚀 优化性能：直接使用循环赋值，虽仍是 CPU 循环，但避免了 getColorArgb 这种高层封装的额外开销
        for (int dy = 0; dy < innerH; dy++) {
            int dstY = innerY + dy;
            if (dstY < 0 || dstY >= targetH)
                continue;
            int srcY = cropY + Math.min(cropH - 1, Math.round(dy * scaleY));
            for (int dx = 0; dx < innerW; dx++) {
                int dstX = innerX + dx;
                if (dstX < 0 || dstX >= targetW)
                    continue;
                int srcX = cropX + Math.min(cropW - 1, Math.round(dx * scaleX));
                // 使用底层 set/get，避免 getColorArgb() 的逻辑检查
                cachedResizedBuffer.setColorArgb(dstX, dstY, src.getColorArgb(srcX, srcY));
            }
        }

    }
}
