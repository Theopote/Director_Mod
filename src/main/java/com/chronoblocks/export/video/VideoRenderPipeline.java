package com.chronoblocks.export.video;

import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.CameraViewportProfile;
import com.chronoblocks.ui.timeline.model.camera.ViewportMath;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;

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
 * <p>重要说明：</p>
 * - 真实的“独立 FBO 离屏渲染 + 指定分辨率 + 关闭 UI/HUD”属于下一阶段；
 * - 当前实现仍然是“可用”的：在导出任务中逐帧驱动时间线/相机，然后在下一帧 UI 渲染开始处
 *   从主 Framebuffer 抓取像素为 {@link NativeImage}，交给编码器写出 PNG。
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

    private VideoRenderPipeline() {}

    /**
     * 请求渲染并抓取某个时间点的一帧。
     *
     * <p>调用线程：后台导出线程。</p>
     * <p>机制：在客户端线程设置时间线/相机 -> 等到下一帧 UI 渲染开始时抓取 framebuffer -> future 完成。</p>
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
        // 1) 驱动核心播放系统（容器/动画可视化）
        PlaybackController.get().pause();
        PlaybackController.get().seek(timeSeconds);

        // 2) 同步 UI 时间线模型（摄像机轨道采样依赖它）
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            model.setCurrentTime(timeSeconds);
        }
    }

    private void applyCamera(float timeSeconds, VideoExportSettings settings) {
        if (settings.cameraMode == VideoExportSettings.CAMERA_PLAYER) {
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
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
        // 这里让 CameraRuntime 进入 TIMELINE owner，由 CameraMixin 在 Camera.update(TAIL) 直接覆盖相机位置/旋转/FOV。
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

        // 1.21.10+：ScreenshotRecorder 为异步回调形式。
        // 这里使用“以 Framebuffer.class 作为参数类型”的反射调用：
        // - 避免 Java 反射 getMethod 不能用子类参数匹配（WindowFramebuffer）的坑
        // - 同时避免 IDE/索引把 overload 解析错导致的编译器/检查器误报
        try {
            var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class, java.util.function.Consumer.class);
            m.invoke(null, main, consumer);
        } catch (ReflectiveOperationException e) {
            // fallback overload: takeScreenshot(Framebuffer, int, Consumer)
            try {
                var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class, int.class, java.util.function.Consumer.class);
                m.invoke(null, main, 0, consumer);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException("ScreenshotRecorder.takeScreenshot overload not found", e);
            }
        }
    }

    private static @NotNull Consumer<NativeImage> getNativeImageConsumer(CaptureRequest req, Framebuffer main) {
        if (main == null) {
            throw new IllegalStateException("client.getFramebuffer() == null");
        }

        // ⭐ 关键：主 framebuffer 尺寸取决于窗口，不一定等于导出目标尺寸。
        // 导出时应根据摄像机的画幅比例和缩放策略（FIT/FILL）进行裁切。
        // 
        // 设计原则：
        // - 导出分辨率：使用 VideoExportSettings.width/height（用户可能手动修改过）
        // - 裁切策略：从摄像机 CameraViewportProfile 获取 PreviewScalePolicy
        // - 计算逻辑：复用 ViewportMath，确保与 UI 预览一致
        int targetW = Math.max(1, req.settings.width);
        int targetH = Math.max(1, req.settings.height);

        // ⭐ 获取当前导出使用的摄像机轨道（如果存在），读取其 CameraViewportProfile
        CameraViewportProfile profile = null;
        if (req.settings.cameraMode == VideoExportSettings.CAMERA_TRACK) {
            TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
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
                NativeImage out = img;
                if (img.getWidth() != targetW || img.getHeight() != targetH) {
                    // ⭐ 复用 ViewportMath 计算裁切区域，保持与 UI 预览一致的逻辑
                    out = resizeToTargetUsingViewportMath(img, targetW, targetH, cameraProfile);
                    img.close();
                }
                req.future.complete(out);
            } catch (Throwable t) {
                try {
                    img.close();
                } catch (Throwable ignore) {
                    // ignore
                }
                req.future.completeExceptionally(t);
            }
        };
    }

    /**
     * ⭐ 使用 ViewportMath 将源图像调整到目标尺寸（复用 UI 预览的裁切逻辑）
     * 
     * <p>根据摄像机的 PreviewScalePolicy（FIT/FILL）决定缩放策略：
     * <ul>
     *   <li>FIT: 保持完整画面，可能留黑边（使用 ViewportMath.computeFit）</li>
     *   <li>FILL: 填满目标尺寸，可能裁切（使用 ViewportMath.computeFill）</li>
     * </ul>
     * 
     * <p>如果没有摄像机配置，默认使用 FIT 策略（保持画幅完整）。
     * 
     * @param src 源图像（主 framebuffer 截图）
     * @param targetW 目标宽度
     * @param targetH 目标高度
     * @param cameraProfile 摄像机视口配置（可选，用于获取缩放策略）
     * @return 调整后的图像
     */
    private static NativeImage resizeToTargetUsingViewportMath(NativeImage src, int targetW, int targetH,
                                                               CameraViewportProfile cameraProfile) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) {
            NativeImage dst = new NativeImage(Math.max(1, targetW), Math.max(1, targetH), true);
            dst.fillRect(0, 0, dst.getWidth(), dst.getHeight(), 0xFF000000);
            return dst;
        }

        // Determine camera aspect; fall back to target aspect when missing.
        float cameraAspect = (cameraProfile != null) ? cameraProfile.getAspectRatio() : 0f;
        if (cameraAspect <= 0f) {
            cameraAspect = (float) targetW / (float) targetH;
        }

        CameraViewportProfile.PreviewScalePolicy scalePolicy =
                (cameraProfile != null) ? cameraProfile.getPreviewScalePolicy()
                        : CameraViewportProfile.PreviewScalePolicy.FIT;

        // Compute viewport rect in target using camera aspect (NOT source aspect).
        ViewportMath.ViewportRects viewport = (scalePolicy == CameraViewportProfile.PreviewScalePolicy.FIT)
                ? ViewportMath.computeFit(0, 0, targetW, targetH, cameraAspect)
                : ViewportMath.computeFill(0, 0, targetW, targetH, cameraAspect);

        int innerX = Math.round(viewport.innerX);
        int innerY = Math.round(viewport.innerY);
        int innerW = Math.round(viewport.innerW);
        int innerH = Math.round(viewport.innerH);
        if (innerW <= 0 || innerH <= 0) {
            NativeImage dst = new NativeImage(Math.max(1, targetW), Math.max(1, targetH), true);
            dst.fillRect(0, 0, dst.getWidth(), dst.getHeight(), 0xFF000000);
            return dst;
        }

        // Crop source to camera aspect (center crop) to avoid distortion.
        float srcAspect = (float) srcW / (float) srcH;
        int cropX = 0;
        int cropY = 0;
        int cropW = srcW;
        int cropH = srcH;
        if (Math.abs(srcAspect - cameraAspect) > 0.0001f) {
            if (srcAspect > cameraAspect) {
                // Source is wider than camera; crop left/right.
                cropW = Math.round(srcH * cameraAspect);
                cropW = Math.min(cropW, srcW);
                cropX = (srcW - cropW) / 2;
            } else {
                // Source is taller than camera; crop top/bottom.
                cropH = Math.round(srcW / cameraAspect);
                cropH = Math.min(cropH, srcH);
                cropY = (srcH - cropH) / 2;
            }
        }

        float scaleX = (float) cropW / (float) innerW;
        float scaleY = (float) cropH / (float) innerH;

        NativeImage dst = new NativeImage(targetW, targetH, true);
        dst.fillRect(0, 0, targetW, targetH, 0xFF000000);

        for (int dy = 0; dy < innerH; dy++) {
            int dstY = innerY + dy;
            if (dstY < 0 || dstY >= targetH) continue;

            int srcY = cropY + Math.min(cropH - 1, Math.round(dy * scaleY));
            for (int dx = 0; dx < innerW; dx++) {
                int dstX = innerX + dx;
                if (dstX < 0 || dstX >= targetW) continue;

                int srcX = cropX + Math.min(cropW - 1, Math.round(dx * scaleX));
                dst.setColorArgb(dstX, dstY, src.getColorArgb(srcX, srcY));
            }
        }

        return dst;
    }

}




