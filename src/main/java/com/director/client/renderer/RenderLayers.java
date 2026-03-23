package com.director.client.renderer;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * 🎨 RenderLayers (Minecraft 1.21.10+)
 *
 * 1.21.10 的渲染管线中，旧版基于 RenderPhase 常量/RenderSystem 纹理绑定的自定义层实现不再兼容。
 * 为了保证稳定性，这里优先复用 vanilla 的 RenderLayer，并用白色纹理实现“纯色半透明”填充。
 */
public final class RenderLayers {

    private static final Identifier WHITE_TEXTURE = Identifier.of("director", "textures/misc/white.png");

    private RenderLayers() {}

    /** 半透明预览层（面选择/魔棒/拖拽等共用） */
    public static RenderLayer getPreviewLayer() {
        return RenderLayer.getEntityTranslucent(WHITE_TEXTURE);
    }

    /** 选区填充层（白色纹理 × 顶点颜色） */
    public static RenderLayer getSelectionFillLayer() {
        return RenderLayer.getEntityTranslucent(WHITE_TEXTURE);
    }

    /** 套索填充层 */
    public static RenderLayer getLassoFillLayer() {
        return RenderLayer.getEntityTranslucent(WHITE_TEXTURE);
    }

    /** 套索线条层 */
    public static RenderLayer getLassoLineLayer() {
        return RenderLayer.getLines();
    }

    /** 几何预览填充层 */
    public static RenderLayer getGeometryFillLayer() {
        return RenderLayer.getEntityTranslucent(WHITE_TEXTURE);
    }

    /** 几何预览线条层 */
    public static RenderLayer getGeometryLineLayer() {
        return RenderLayer.getLines();
    }

    /** 线框层 */
    public static RenderLayer getLineOverlay() {
        return RenderLayer.getLines();
    }
}


