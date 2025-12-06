package com.chronoblocks.client.renderer.pipeline;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.client.gizmo.GizmoRenderEvents;
import com.chronoblocks.client.renderer.BlockVisualOverlayRenderer;
import com.chronoblocks.client.renderer.CameraPathDebugRenderer;
import com.chronoblocks.client.renderer.ContainerHighlightRenderer;
import com.chronoblocks.client.renderer.HoverHighlightRenderer;
import com.chronoblocks.client.renderer.InteractionHintRenderer;
import com.chronoblocks.client.renderer.SelectionOverlayRenderer;
import com.chronoblocks.client.renderer.SelectionRenderer;
import com.chronoblocks.client.renderer.ToolPreviewManager;
import com.chronoblocks.client.input.ChronoBlocksInputSystem;
import com.chronoblocks.core.animation.BlockAnimationRenderer;
import com.chronoblocks.core.container.visibility.BlockVisualStateManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.ui.ChronoBlocksScreen;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
 * 🧩 ChronoBlocksRendererRegistry v3.0
 * -----------------------------------------------------
 * 🎯 统一渲染调度系统（类似 Unity RenderPipeline）
 *
 * ✅ 注册 / 注销 各类渲染器（Selection、ToolPreview、Hover、Gizmo 等）
 * ✅ 自动挂载 Fabric WorldRenderEvents（BeforeEntities / AfterEntities / AfterTranslucent）
 * ✅ 支持阶段化渲染 Stage → priority → renderer
 * ✅ 可重复初始化检测（isInitialized / ensureInitialized）
 */
public final class ChronoBlocksRendererRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksRendererRegistry.class);
    private static final ChronoBlocksRendererRegistry INSTANCE = new ChronoBlocksRendererRegistry();

    public static ChronoBlocksRendererRegistry get() {
        return INSTANCE;
    }

    private final AtomicBoolean eventsHooked = new AtomicBoolean(false);
    private final AtomicBoolean defaultsRegistered = new AtomicBoolean(false);
    private final Map<RendererStage, List<RendererEntry>> stageMap = new EnumMap<>(RendererStage.class);

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

    private ChronoBlocksRendererRegistry() {
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
            WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> renderPhase(RendererStage.WorldRenderPhase.AFTER_TRANSLUCENT, ctx));
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
                        .renderOutlineOnly(ctx.matrixStack(), ctx.consumers(), getTickDelta()),
                    baseOrder);
            }

            if (RendererStage.INTERACTION_HINT.isEnabled()) {
                int hintOrder = RendererStage.INTERACTION_HINT.getOrder();
                registerRenderer(RendererStage.INTERACTION_HINT, "HoverHighlight",
                    ChronoBlocksRendererRegistry::renderHoverHighlight,
                    hintOrder);
                registerRenderer(RendererStage.INTERACTION_HINT, "InteractionHints",
                    ChronoBlocksRendererRegistry::renderInteractionHints,
                    hintOrder + 5);
                registerRenderer(RendererStage.INTERACTION_HINT, "ContainerBounds",
                    ChronoBlocksRendererRegistry::renderContainerBounds,
                    hintOrder + 10);
                registerRenderer(RendererStage.INTERACTION_HINT, "ContainerHighlight",
                    ChronoBlocksRendererRegistry::renderContainerHighlight,
                    hintOrder + 15);
            }

            if (RendererStage.SELECTION_OVERLAY.isEnabled()) {
                int overlayOrder = RendererStage.SELECTION_OVERLAY.getOrder();
                registerRenderer(RendererStage.SELECTION_OVERLAY, "SelectionFill",
                    ChronoBlocksRendererRegistry::renderSelectionFill,
                    overlayOrder);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "SelectionOverlay",
                    ChronoBlocksRendererRegistry::renderSelectionOverlay,
                    overlayOrder + 5);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "BlockVisualOverlay",
                    ChronoBlocksRendererRegistry::renderBlockVisualOverlay,
                    overlayOrder + 10);
                registerRenderer(RendererStage.SELECTION_OVERLAY, "BlockAnimations",
                    ChronoBlocksRendererRegistry::renderBlockAnimations,
                    overlayOrder + 15);
            }

            if (RendererStage.TOOL_PREVIEW.isEnabled()) {
                registerRenderer(RendererStage.TOOL_PREVIEW, "ToolPreview",
                    ChronoBlocksRendererRegistry::renderToolPreview,
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
        for (RendererEntry entry : List.copyOf(entries)) {
            try {
                entry.invoke(ctx);
            } catch (Exception ex) {
                LOGGER.warn("[RendererRegistry] Renderer '{}' 在阶段 {} 渲染异常: {}", entry.getName(), stage, ex.getMessage(), ex);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Render helpers
    // ---------------------------------------------------------------------

    private static void renderHoverHighlight(WorldRenderContext context) {
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        BlockHitResult hitResult = null;
        if (client.currentScreen instanceof ChronoBlocksScreen) {
            hitResult = ChronoBlocksInputSystem.raycastFromImGui();
        } else if (client.crosshairTarget instanceof BlockHitResult crosshairHit) {
            hitResult = crosshairHit;
        }

        if (hitResult == null) {
            return;
        }

        HoverHighlightRenderer.renderBlockOutline(matrices, consumers, hitResult);
    }

    private static void renderInteractionHints(WorldRenderContext context) {
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        Camera camera = getCamera();
        if (camera == null) {
            return;
        }
        SelectionManager manager = SelectionManager.get();
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
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        com.chronoblocks.client.renderer.ContainerBoundsRenderer.render(matrices, consumers);
    }
    
    private static void renderContainerHighlight(WorldRenderContext context) {
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
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
        MatrixStack matrices = context.matrixStack();
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
        MatrixStack matrices = context.matrixStack();
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
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        ToolPreviewManager.getInstance().render(matrices, consumers, getTickDelta());
    }

    private static void renderBlockVisualOverlay(WorldRenderContext context) {
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
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
        if (!isChronoBlocksActive()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
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

    private static boolean isChronoBlocksActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        if (client.currentScreen instanceof ChronoBlocksScreen) {
            return true;
        }
        return ChronoBlocksClientController.getInstance().isChronoBlocksModeActive();
    }

    private static boolean hasRenderableSelection() {
        SelectionRenderer renderer = SelectionRenderer.getInstance();
        if (!renderer.isEnabled()) {
            return false;
        }
        SelectionManager manager = SelectionManager.get();
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
        return client.getRenderTickCounter().getTickDelta(false);
    }

    private static Camera getCamera() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        return client.gameRenderer.getCamera();
    }
}
