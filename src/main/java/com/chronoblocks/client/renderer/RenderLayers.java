package com.chronoblocks.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 🎨 RenderLayers v3.3
 *
 * 包含 ChronoBlocks 专用的工具渲染层定义：
 *  - Selection（选区线框 / 填充）
 *  - Preview（工具半透明预览）
 *  - Lasso（路径与区域叠加）
 */
public final class RenderLayers {

    private static final Map<String, RenderLayer> REGISTERED_LAYERS = new HashMap<>();

    private static final Identifier WHITE_TEXTURE = Identifier.of("chronoblocks", "textures/misc/white.png");

    private static final RenderPhase.TextureBase WHITE_TEXTURE_PHASE = new RenderPhase.TextureBase(
        () -> RenderSystem.setShaderTexture(0, WHITE_TEXTURE),
        () -> {}
    );

    /** 🟦 半透明预览层（用于平面/体积选择） */
    private static final RenderLayer PREVIEW_LAYER = registerLayer(
        "chronoblocks_preview",
        RenderLayer.of(
        "chronoblocks_preview",
        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
        DrawMode.QUADS,
        256,
        true,
        false,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.TRANSLUCENT_PROGRAM)
            .texture(WHITE_TEXTURE_PHASE)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
            .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(true)
    ));

    /** 🟦 套索填充层 */
    private static RenderLayer LASSO_FILL_LAYER;

    /** 🔵 套索路径线条层 */
    private static RenderLayer LASSO_LINE_LAYER;

    /** 🔷 几何预览填充层 */
    private static RenderLayer GEOMETRY_FILL_LAYER;

    /** 🔷 几何预览线条层 */
    private static RenderLayer GEOMETRY_LINE_LAYER;

    /** 🧱 实心覆盖层（不透明包围盒） */
    private static final RenderLayer SOLID_OVERLAY = registerLayer("chronoblocks_solid_overlay", RenderLayer.of(
        "chronoblocks_solid_overlay",
        VertexFormats.POSITION_COLOR,
        DrawMode.QUADS,
        256,
        false,
        false,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.POSITION_COLOR_PROGRAM)
            .transparency(RenderPhase.NO_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
            .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(false)
    ));

    /** 🟨 选区专用填充层（纯顶点颜色） */
    private static final RenderLayer SELECTION_FILL_LAYER = registerLayer("chronoblocks_selection_fill", RenderLayer.of(
        "chronoblocks_selection_fill",
        VertexFormats.POSITION_COLOR,
        DrawMode.QUADS,
        256,
        true,
        false,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.POSITION_COLOR_PROGRAM)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
            .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(true)
    ));

    private RenderLayers() {}

    /** 🔷 获取半透明预览层（面选择、魔棒、拖拽共用） */
    public static RenderLayer getPreviewLayer() {
        return PREVIEW_LAYER;
    }

    public static RenderLayer getSelectionFillLayer() {
        return SELECTION_FILL_LAYER;
    }

    /**
     * 🔵 获取套索工具专用的透明渲染层
     *
     * 特点：
     * - 启用深度测试（允许路径与方块共存）
     * - 半透明混合（支持叠加动画）
     * - 不写入深度缓冲（避免遮挡）
     * - 线条与面片均可共用
     */
    public static RenderLayer getLassoFillLayer() {
        if (LASSO_FILL_LAYER == null) {
            LASSO_FILL_LAYER = registerLayer("chronoblocks_lasso_fill", RenderLayer.of(
                "chronoblocks_lasso_fill",
                VertexFormats.POSITION_COLOR,
                DrawMode.QUADS,
                256,
                false,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.POSITION_COLOR_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)
            ));
        }
        return LASSO_FILL_LAYER;
    }

    public static RenderLayer getLassoLineLayer() {
        if (LASSO_LINE_LAYER == null) {
            LASSO_LINE_LAYER = registerLayer("chronoblocks_lasso_lines", RenderLayer.of(
                "chronoblocks_lasso_lines",
                VertexFormats.POSITION_COLOR,
                DrawMode.LINES,
                256,
                false,
                false,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(java.util.OptionalDouble.of(2.0)))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)
            ));
        }
        return LASSO_LINE_LAYER;
    }

    public static RenderLayer getGeometryFillLayer() {
        if (GEOMETRY_FILL_LAYER == null) {
            GEOMETRY_FILL_LAYER = registerLayer("chronoblocks_geometry_fill", RenderLayer.of(
                "chronoblocks_geometry_fill",
                VertexFormats.POSITION_COLOR,
                DrawMode.QUADS,
                512,
                false,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.POSITION_COLOR_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)
            ));
        }
        return GEOMETRY_FILL_LAYER;
    }

    public static RenderLayer getGeometryLineLayer() {
        if (GEOMETRY_LINE_LAYER == null) {
            GEOMETRY_LINE_LAYER = registerLayer("chronoblocks_geometry_lines", RenderLayer.of(
                "chronoblocks_geometry_lines",
                VertexFormats.POSITION_COLOR,
                DrawMode.LINES,
                512,
                false,
                false,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(java.util.OptionalDouble.of(1.6)))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)
            ));
        }
        return GEOMETRY_LINE_LAYER;
    }

    /** 🔶 获取线框层 */
    public static RenderLayer getLineOverlay() {
        return getOrRegister("chronoblocks_line_overlay", RenderLayer::getLines);
    }

    /** ⚪ 获取实心覆盖层 */
    public static RenderLayer getSolidOverlay() {
        return SOLID_OVERLAY;
    }

    /** 🖌️ Brush 预览填充层（语义隔离，当前复用半透明预览层） */
    public static RenderLayer getBrushPreviewFillLayer() {
        return getOrRegister("chronoblocks_brush_preview_fill", RenderLayer::getTranslucent);
    }

    /** 🖌️ Brush 预览线框层（地面圆环） */
    public static RenderLayer getBrushPreviewLineLayer() {
        return getOrRegister("chronoblocks_brush_preview_lines", RenderLayer::getLines);
    }

    public static Optional<RenderLayer> findLayer(String id) {
        return Optional.ofNullable(REGISTERED_LAYERS.get(id));
    }

    public static RenderLayer getLayer(String id) {
        return REGISTERED_LAYERS.get(id);
    }

    private static RenderLayer registerLayer(String id, RenderLayer layer) {
        REGISTERED_LAYERS.putIfAbsent(id, layer);
        return layer;
    }

    private static RenderLayer getOrRegister(String id, Supplier<RenderLayer> supplier) {
        return REGISTERED_LAYERS.computeIfAbsent(id, key -> supplier.get());
    }
}