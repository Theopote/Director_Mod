package com.chronoblocks.export.video;

import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

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

    // v1：复用一个离屏 framebuffer，把主 framebuffer 的内容缩放/拷贝到导出分辨率后再截图
    private Framebuffer exportFramebuffer;
    private int exportW = -1;
    private int exportH = -1;

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
        // 使用 jumpToTimeAsPlayer：把采样结果写入玩家实体，确保 renderWorld 使用该视角
        cameraController.jumpToTimeAsPlayer(timeSeconds);
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
        if (main == null) {
            throw new IllegalStateException("client.getFramebuffer() == null");
        }

        // 关键：主 framebuffer 尺寸取决于窗口（你的日志里就是 1920x1017），不一定等于导出目标尺寸（1920x1080）。
        // 因此先把主 framebuffer 的颜色附件 drawBlit 到一个“导出分辨率”的离屏 framebuffer，再对离屏 framebuffer 截图。
        int targetW = Math.max(1, req.settings.width);
        int targetH = Math.max(1, req.settings.height);
        if (INSTANCE.exportFramebuffer == null || INSTANCE.exportW != targetW || INSTANCE.exportH != targetH) {
            if (INSTANCE.exportFramebuffer != null) {
                try {
                    INSTANCE.exportFramebuffer.delete();
                } catch (Exception ignore) {
                    // ignore
                }
            }
            // 注意：部分 IDE/索引可能解析不到 SimpleFramebuffer 构造器签名；这里用反射创建，避免“IDE 报错但运行正常”。
            INSTANCE.exportFramebuffer = newExportFramebuffer(targetW, targetH);
            INSTANCE.exportW = targetW;
            INSTANCE.exportH = targetH;
            LOGGER.info("[VideoExport] Created export framebuffer {}x{} (main framebuffer {}x{})",
                    targetW, targetH, main.textureWidth, main.textureHeight);
        }

        // 将主 framebuffer 的颜色贴图按导出分辨率绘制到离屏 framebuffer（会发生缩放）
        // 同理：getColorAttachmentView / drawBlit 在某些索引下不可见，统一用反射调用。
        Object colorView = invokeNoThrow(main, "getColorAttachmentView");
        if (colorView == null) {
            throw new IllegalStateException("main.getColorAttachmentView() returned null");
        }
        invokeBlit(INSTANCE.exportFramebuffer, colorView);
        Framebuffer toCapture = INSTANCE.exportFramebuffer;

        // 1.21.10+：ScreenshotRecorder 为异步回调形式。
        // 这里使用“以 Framebuffer.class 作为参数类型”的反射调用：
        // - 避免 Java 反射 getMethod 不能用子类参数匹配（WindowFramebuffer）的坑
        // - 同时避免 IDE/索引把 overload 解析错导致的编译器/检查器误报
        try {
            var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class, java.util.function.Consumer.class);
            m.invoke(null, toCapture, (java.util.function.Consumer<NativeImage>) req.future::complete);
        } catch (ReflectiveOperationException e) {
            // fallback overload: takeScreenshot(Framebuffer, int, Consumer)
            try {
                var m = ScreenshotRecorder.class.getMethod("takeScreenshot", Framebuffer.class, int.class, java.util.function.Consumer.class);
                m.invoke(null, toCapture, 0, (java.util.function.Consumer<NativeImage>) req.future::complete);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException("ScreenshotRecorder.takeScreenshot overload not found", e);
            }
        }
    }

    private static Framebuffer newExportFramebuffer(int w, int h) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.gl.SimpleFramebuffer");
            var ctor = clazz.getConstructor(String.class, int.class, int.class, boolean.class);
            return (Framebuffer) ctor.newInstance("video_export", w, h, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create SimpleFramebuffer via reflection", e);
        }
    }

    private static Object invokeNoThrow(Object target, String method) {
        try {
            var m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void invokeBlit(Framebuffer dst, Object gpuTextureView) {
        try {
            Class<?> viewClass = Class.forName("com.mojang.blaze3d.textures.GpuTextureView");
            var m = Framebuffer.class.getMethod("drawBlit", viewClass);
            m.invoke(dst, gpuTextureView);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to blit main framebuffer into export framebuffer", e);
        }
    }
}


