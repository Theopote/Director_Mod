package com.director.core.animation;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;

/** Tile metadata uses the otherwise unused overlay/normal attributes; color and light remain native. */
final class OcclusionSurfaceRenderLayers {
    private static final RenderLayer TILED_DEPTH = create(true, true);
    private static final RenderLayer TILED_NO_DEPTH = create(true, false);
    private static final RenderLayer NATIVE_DEPTH = create(false, true);
    private static final RenderLayer NATIVE_NO_DEPTH = create(false, false);
    private OcclusionSurfaceRenderLayers() {}
    static RenderLayer tiled(boolean depthWrite) { return depthWrite ? TILED_DEPTH : TILED_NO_DEPTH; }
    static RenderLayer nativeLayer(boolean depthWrite) { return depthWrite ? NATIVE_DEPTH : NATIVE_NO_DEPTH; }
    private static RenderLayer create(boolean tiled, boolean depthWrite) {
        String name = "occlusion_" + (tiled ? "tiled" : "native") + (depthWrite ? "_depth" : "_no_depth");
        var builder = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
                .withLocation(Identifier.of("director", "pipeline/" + name))
                .withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(depthWrite)
                .withShaderDefine("ALPHA_CUTOUT", 0.01f);
        if (tiled) builder.withVertexShader(Identifier.of("director", "core/occlusion_surface"))
                .withFragmentShader(Identifier.of("director", "core/occlusion_surface"))
                .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS);
        return RenderLayer.of("director_" + name, 786432, false, true, builder.build(),
                RenderLayer.MultiPhaseParameters.builder().lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .texture(RenderPhase.MIPMAP_BLOCK_ATLAS_TEXTURE)
                        .target(RenderPhase.ITEM_ENTITY_TARGET).build(false));
    }
}
