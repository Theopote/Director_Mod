package com.director.client.renderer.pipeline;

import com.director.core.DirectorServices;
import com.director.client.DirectorClientController;
import com.director.client.gizmo.GizmoRenderEvents;
import com.director.client.renderer.BlockVisualOverlayRenderer;
import com.director.client.renderer.CameraPathDebugRenderer;
import com.director.client.renderer.ContainerHighlightRenderer;
import com.director.client.renderer.HoverHighlightRenderer;
import com.director.client.renderer.InteractionHintRenderer;
import com.director.client.renderer.SelectionOverlayRenderer;
import com.director.client.renderer.SelectionRenderer;
import com.director.client.renderer.ToolPreviewManager;
import com.director.client.input.DirectorInputSystem;
import com.director.core.animation.BlockAnimationRenderer;
import com.director.core.container.visibility.BlockVisualStateManager;
import com.director.core.selection.SelectionManager;
import com.director.export.video.VideoExportManager;
import com.director.ui.DirectorScreen;
import com.director.timeline.container.ContainerTimelineService;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🧩 DirectorRendererRegistry v3.0
 * -----------------------------------------------------
 * 🎯 统一渲染调度系统（类似 Unity RenderPipeline）
 * <p>
 * ✅ 注册 / 注销 各类渲染器（Selection、ToolPreview、Hover、Gizmo 等）
 * ✅ 自动挂载 Fabric WorldRenderEvents（BeforeEntities / AfterEntities / AfterTranslucent）
 * ✅ 支持阶段化渲染 Stage → priority → renderer
 * ✅ 可重复初始化检测（isInitialized / ensureInitialized）
 */
public final class DirectorRendererRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorRendererRegistry.class);
    private static final DirectorRendererRegistry INSTANCE = new DirectorRendererRegistry();

    public static DirectorRendererRegistry get() {
        return INSTANCE;
    }

    private final AtomicBoolean eventsHooked = new AtomicBoolean(false);
    private final AtomicBoolean defaultsRegistered = new AtomicBoolean(false);
    private final Map<RendererStage, List<RendererEntry>> stageMap = new EnumMap<>(RendererStage.class);

    // ---------------------------------------------------------------------
    // Lightweight render profiler (throttled logging)
    // ---------------------------------------------------------------------

    private static final class ProfStat {
        long count;
        long sumNs;
        long maxNs;
        long lastNs;
        void add(long ns) {
            count++;
            sumNs += ns;
            lastNs = ns;
            if (ns > maxNs) maxNs = ns;
        }
        double avgMs() {
            return count == 0 ? 0.0 : (sumNs / 1_000_000.0) / (double) count;
        }
        double maxMs() {
            return maxNs / 1_000_000.0;
        }
        double lastMs() {
            return lastNs / 1_000_000.0;
        }
    }

    private final Map<String, ProfStat> rendererPerf = new java.util.HashMap<>();
    private final Map<RendererStage, ProfStat> stagePerf = new EnumMap<>(RendererStage.class);
    private long lastPerfLogMs = 0L;

    public interface IRenderer {
        void render(WorldRenderContext ctx) throws Exception;
    }

    public static final class RendererEntry {
        private final String name;
        private final RendererStage stage;
        private final IRenderer renderer;
        private final int priority;
        private volatile boolean enabled = true;

        public RendererEntry(String name, RendererStage stage, IRenderer renderer, int priority) {
            this.name = name;
            this.stage = stage;
            this.renderer = renderer;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public RendererStage getStage() {
            return stage;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void invoke(WorldRenderContext ctx) throws Exception {
            if (enabled) {
                renderer.render(ctx);
            }
        }
    }

    private DirectorRendererRegistry() {
        for (RendererStage stage : RendererStage.values()) {
            stageMap.put(stage, new ArrayList<>());
        }
    }

    // ---------------------------------------------------------------------
    // Registry API
    // ---------------------------------------------------------------------

    public synchronized void registerRenderer(RendererStage stage, String name, IRenderer renderer, int priority) {
        stageMap.values().forEach(list -> list.removeIf(entry -> entry.getName().equalsIgnoreCase(name)));
        RendererEntry entry = new RendererEntry(name, stage, renderer, priority);
        stageMap.get(stage).add(entry);
        stageMap.get(stage).sort(Comparator.comparingInt(RendererEntry::getPriority));
        LOGGER.info("[RendererRegistry] 注册渲染器: {} (stage={}, priority={})", name, stage, priority);
    }

    public void initialize() {
        hookFabricWorldEvents();
        registerDefaultRenderers();
    }

    public boolean isInitialized() {
        return eventsHooked.get() && defaultsRegistered.get();
    }

    public void ensureInitialized() {
        if (!isInitialized()) {
            initialize();
        }
    }

    // ---------------------------------------------------------------------
    // Fabric event hook
    // ---------------------------------------------------------------------

    public void hookFabricWorldEvents() {
        if (eventsHooked.compareAndSet(false, true)) {
            LOGGER.info("[RendererRegistry] 挂载 Fabric WorldRenderEvents...");
            WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> renderPhase(RendererStage.WorldRenderPhase.BEFORE_ENTITIES, ctx));
            WorldRenderEvents.AFTER_ENTITIES.register(ctx -> renderPhase(RendererStage.WorldRenderPhase.AFTER_ENTITIES, ctx));
            // 1.21.10+：使用 END_MAIN 作为“透明层等主渲染结束”的阶段（替代旧 AFTER_TRANSLUCENT）
            WorldRenderEvents.END_MAIN.register(ctx -> renderPhase(RendererStage.WorldRenderPhase.AFTER_TRANSLUCENT, ctx));
            LOGGER.info("[RendererRegistry] ✅ 渲染事件挂载完成");
        } else {
            LOGGER.debug("[RendererRegistry] 渲染事件已挂载，跳过");
        }
    }

    // ---------------------------------------------------------------------
    // Default renderer wiring
    // ---------------------------------------------------------------------

    public void registerDefaultRenderers() {
        if (!defaultsRegistered.compareAndSet(false, true)) {
            LOGGER.debug("[RendererRegistry] 默认渲染器已注册，跳过");
            return;
        }

        LOGGER.info("[RendererRegistry] 注册默认渲染器...");
        try {
            if (RendererStage.SELECTION_BASE.isEnabled()) {
                int baseOrder = RendererStage.SELECTION_BASE.getOrder();
                registerRenderer(RendererStage.SELECTION_BASE, "SelectionOutline",
                    ctx -> SelectionRenderer.getInstance()
                        .renderOutlineOnly(ctx.matrices(), ctx.consumers(), getTickDelta()),
                    baseOrder);
            }

            if (RendererStage.INTERACTION_HINT.isEnabled()) {
                int hintOrder = RendererStage.INTERACTION_HINT.getOrder();
                registerRenderer(RendererStage.INTERACTION_HINT, "HoverHighlight",
                    DirectorRendererRegistry::renderHoverHighlight,
                    hintOrder);
                registerRenderer(RendererStage.INTERACTION_HINT, "InteractionHints",
                    DirectorRendererRegistry::renderInteractionHints,
                    hintOrder + 5);
                registerRenderer(RendererStage.INTERACTION_HINT, "ContainerBounds",
                    DirectorRendererRegistry::renderContainerBounds,
                    hintOrder + 10);
                registerRenderer(RendererStage.INTERACTION_HINT, "ContainerHighlight",
                    DirectorRendererRegistry::renderContainerHighlight,
                    hintOrder + 15);
                // 框选预览包围盒的轮廓线必须在 AFTER_ENTITIES 阶段绘制，
                // 否则 RenderLayer.getLines() 在 END_MAIN 阶段不会被正确 flush。
                registerRenderer(RendererStage.INTERACTION_HINT, "SelectionPreviewOutline",
                    DirectorRendererRegistry::renderSelectionPreviewOutline,
                    hintOrder + 20);
            }

            if (RendererStage.SELECTION_OVERLAY.isEnabled()) {
                int overlayOrder = RendererStage.SELECTION_OVERLAY.getOrder();
                registerRenderer(RendererStage.SELECTION_OVERLAY, "SelectionFill",
                    DirectorRendererRegistry::renderSelectionFill,
                    overlayOrder);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "SelectionOverlay",
                    DirectorRendererRegistry::renderSelectionOverlay,
                    overlayOrder + 5);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "BlockVisualOverlay",
                    DirectorRendererRegistry::renderBlockVisualOverlay,
                    overlayOrder + 10);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "BlockAnimations",
                    DirectorRendererRegistry::renderBlockAnimations,
                    overlayOrder + 15);
            }

            if (RendererStage.TOOL_PREVIEW.isEnabled()) {
                registerRenderer(RendererStage.TOOL_PREVIEW, "ToolPreview",
                    DirectorRendererRegistry::renderToolPreview,
                    RendererStage.TOOL_PREVIEW.getOrder());
            }

            if (RendererStage.GIZMO.isEnabled()) {
                registerRenderer(RendererStage.GIZMO, "Gizmo",
                        GizmoRenderEvents::render,
                    RendererStage.GIZMO.getOrder());
            }

            // 摄像机轨迹调试渲染（使用 TOOL_PREVIEW 阶段，因为 DEBUG_OVERLAY 默认禁用）
            if (RendererStage.TOOL_PREVIEW.isEnabled()) {
                registerRenderer(RendererStage.TOOL_PREVIEW, "CameraPathDebug",
                        ctx -> CameraPathDebugRenderer.getInstance().render(ctx),
                        RendererStage.TOOL_PREVIEW.getOrder() + 10); // 在工具预览之后渲染
            }

            // Actor LookAt 可视化（使用 INTERACTION_HINT 阶段，在实体渲染后显示视线和 Marker）
            if (RendererStage.INTERACTION_HINT.isEnabled()) {
                registerRenderer(RendererStage.INTERACTION_HINT, "ActorLookAtVisualizer",
                        ctx -> {
                            var visualizer = com.director.actor.visualizer.ActorLookAtVisualizer.getInstance();
                            visualizer.render(ctx.matrices(), getTickDelta(), ctx.consumers());
                        },
                        RendererStage.INTERACTION_HINT.getOrder() + 20); // 在交互提示之后渲染
            }

            LOGGER.info("[RendererRegistry] ✅ 默认渲染器注册完成");
        } catch (Exception e) {
            LOGGER.error("[RendererRegistry] 注册默认渲染器失败", e);
        }
    }

    // ---------------------------------------------------------------------
    // Stage dispatch
    // ---------------------------------------------------------------------

    private void renderPhase(RendererStage.WorldRenderPhase phase, WorldRenderContext ctx) {
        for (RendererStage stage : RendererStage.getOrderedStages()) {
            if (stage.getWorldPhase() == phase) {
                renderStage(stage, ctx);
            }
        }
    }

    private void renderStage(RendererStage stage, WorldRenderContext ctx) {
        if (!stage.isEnabled()) {
            return;
        }
        List<RendererEntry> entries = stageMap.get(stage);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        boolean perfMode = isPlaybackOrExportPerfMode();

        final long stageStart = System.nanoTime();
        for (RendererEntry entry : List.copyOf(entries)) {
            if (!entry.isEnabled()) {
                continue;
            }
            final long t0 = System.nanoTime();
            try {
                if (perfMode && shouldSkipInPerfMode(entry.getName())) {
                    continue;
                }
                entry.invoke(ctx);
            } catch (Exception ex) {
                LOGGER.warn("[RendererRegistry] Renderer '{}' 在阶段 {} 渲染异常: {}", entry.getName(), stage, ex.getMessage(), ex);
            } finally {
                long dt = System.nanoTime() - t0;
                String key = stage.name() + "/" + entry.getName();
                rendererPerf.computeIfAbsent(key, k -> new ProfStat()).add(dt);
            }
        }
        long stageDt = System.nanoTime() - stageStart;
        stagePerf.computeIfAbsent(stage, k -> new ProfStat()).add(stageDt);

        // Throttled dump: every 5s, log top offenders (by last)
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPerfLogMs > 5000L) {
            lastPerfLogMs = nowMs;
            try {
                var top = rendererPerf.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue().lastNs, a.getValue().lastNs))
                        .limit(6)
                        .toList();
                StringBuilder sb = new StringBuilder();
                for (var e : top) {
                    ProfStat s = e.getValue();
                    sb.append(e.getKey())
                            .append(" last=").append(String.format("%.2f", s.lastMs())).append("ms")
                            .append(" avg=").append(String.format("%.2f", s.avgMs())).append("ms")
                            .append(" max=").append(String.format("%.2f", s.maxMs())).append("ms")
                            .append(" | ");
                }

                var topStages = stagePerf.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue().lastNs, a.getValue().lastNs))
                        .limit(4)
                        .toList();
                StringBuilder sbStage = new StringBuilder();
                for (var e : topStages) {
                    ProfStat s = e.getValue();
                    sbStage.append(e.getKey().name())
                            .append(" last=").append(String.format("%.2f", s.lastMs())).append("ms")
                            .append(" avg=").append(String.format("%.2f", s.avgMs())).append("ms")
                            .append(" | ");
                }

                LOGGER.warn("[Perf][RendererRegistry] topRenderers: {} topStages: {}", sb, sbStage);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 播放/导出时自动进入性能模式：关闭重渲染负担大的“装饰性”渲染器，保持 FPS。
     */
    private static boolean isPlaybackOrExportPerfMode() {
        try {
            // 1) 导出时必然开启（截图/编码本身很吃性能）
            if (VideoExportManager.getInstance().isExporting()) {
                return true;
            }
            // 2) 时间线播放/拖动播放头时开启（避免额外 debug/overlay renderer 抢占帧时间）
            // 说明：ContainerTimelineService 的 model 绑定可能在某些 UI 生命周期下为 null 或不同步。
            // TimelineCameraController 绑定 TimelineModel 更早、更稳定，因此以它为真相源。
            try {
                return com.director.timeline.playback.camera.TimelineCameraController
                        .getInstance()
                        .isTimelineActive();
            } catch (Throwable ignored) {
                var model = DirectorServices.get().containerTimeline().getTimelineModel();
                return model != null && (model.isPlaying() || model.isScrubbing());
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean shouldSkipInPerfMode(String rendererName) {
        if (rendererName == null) return false;
        // 这些项对“播放/导出结果”不是必需，但很吃 GPU/CPU：
        // 播放时优先保证“时间线动画本体”的流畅度：选择/交互提示/工具预览全部属于编辑辅助，可跳过。
        return rendererName.equalsIgnoreCase("CameraPathDebug")
                || rendererName.equalsIgnoreCase("Gizmo")
                || rendererName.equalsIgnoreCase("ToolPreview")
                // Selection（大量线段/填充会触发 GPU 顶点压力，导致 FPS 极低，但 CPU 计时未必能反映）
                || rendererName.equalsIgnoreCase("SelectionOutline")
                || rendererName.equalsIgnoreCase("SelectionFill")
                || rendererName.equalsIgnoreCase("SelectionOverlay")
                // Interaction hints / container overlays（同样属于编辑辅助）
                || rendererName.equalsIgnoreCase("HoverHighlight")
                || rendererName.equalsIgnoreCase("InteractionHints")
                || rendererName.equalsIgnoreCase("ContainerBounds")
                || rendererName.equalsIgnoreCase("ContainerHighlight")
                // Block overlays（非动画本体，可视需要再开；默认播放先关掉保 FPS）
                || rendererName.equalsIgnoreCase("BlockVisualOverlay");
    }

    // ---------------------------------------------------------------------
    // Render helpers
    // ---------------------------------------------------------------------

    private static void renderHoverHighlight(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        BlockHitResult hitResult = null;
        if (client.currentScreen instanceof DirectorScreen) {
            hitResult = DirectorInputSystem.raycastFromImGui();
        } else if (client.crosshairTarget instanceof BlockHitResult crosshairHit) {
            hitResult = crosshairHit;
        }

        if (hitResult == null) {
            return;
        }

        HoverHighlightRenderer.renderBlockOutline(matrices, consumers, hitResult);
    }

    private static void renderInteractionHints(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        Camera camera = getCamera();
        if (camera == null) {
            return;
        }
        SelectionManager manager = DirectorServices.get().selection();
        if (manager == null) {
            return;
        }

        InteractionHintRenderer.render(
            matrices,
            consumers,
            camera.getPos(),
            manager,
            getTickDelta()
        );
    }
    
    private static void renderContainerBounds(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        com.director.client.renderer.ContainerBoundsRenderer.render(matrices, consumers);
    }

    private static void renderSelectionPreviewOutline(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        ToolPreviewManager.getInstance().renderSelectionPreviewBoxOutline(matrices, consumers);
    }

    private static void renderContainerHighlight(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        ContainerHighlightRenderer.getInstance().render(matrices, consumers, getTickDelta());
    }

    private static void renderSelectionFill(WorldRenderContext context) {
        if (!hasRenderableSelection()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        SelectionRenderer.getInstance().renderFillOnly(matrices, consumers, getTickDelta());
    }

    private static void renderSelectionOverlay(WorldRenderContext context) {
        if (!hasRenderableSelection()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        Camera camera = getCamera();
        if (camera == null) {
            return;
        }
        SelectionOverlayRenderer.get().render(matrices, getTickDelta(), camera, consumers);
    }

    private static void renderToolPreview(WorldRenderContext context) {
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        ToolPreviewManager.getInstance().render(matrices, consumers, getTickDelta());
    }

    private static void renderBlockVisualOverlay(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        Camera camera = getCamera();
        if (matrices == null || consumers == null || camera == null) {
            return;
        }
        BlockVisualOverlayRenderer.getInstance().renderOverlays(
            matrices, 
            consumers, 
            camera.getPos()
        );
    }

    private static void renderBlockAnimations(WorldRenderContext context) {
        if (!isDirectorActive()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client != null ? client.world : null;
        if (world == null) {
            return;
        }
        BlockVisualStateManager manager = BlockVisualStateManager.getInstance();
        var entries = manager.getRenderEntries();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        BlockAnimationRenderer.renderEntries(world, matrices, consumers, entries);
    }

    // ---------------------------------------------------------------------
    // Helper utilities
    // ---------------------------------------------------------------------

    private static boolean isDirectorActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        if (client.currentScreen instanceof DirectorScreen) {
            return true;
        }
        return DirectorClientController.getInstance().isDirectorModeActive();
    }

    private static boolean hasRenderableSelection() {
        SelectionRenderer renderer = SelectionRenderer.getInstance();
        if (!renderer.isEnabled()) {
            return false;
        }
        SelectionManager manager = DirectorServices.get().selection();
        if (manager == null || !manager.hasCurrentTool()) {
            return false;
        }
        var selection = manager.getCurrentSelection();
        return selection != null && !selection.isEmpty();
    }

    private static float getTickDelta() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0.0f;
        }
        return client.getRenderTickCounter().getTickProgress(false);
    }

    private static Camera getCamera() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        return client.gameRenderer.getCamera();
    }
}
