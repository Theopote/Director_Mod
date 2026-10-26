package com.director.core.animation;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;

/** Terrain lightmap interpolation, with blending and transparent texel rejection for fades. */
final class WholeContainerRenderLayers {
    private WholeContainerRenderLayers() {}
    static final RenderLayer FADED = create(true);
    static final RenderLayer FADED_NO_DEPTH = create(false);

    private static RenderLayer create(boolean depthWrite) {
        String name = "whole_container_fade" + (depthWrite ? "" : "_no_depth");
        var pipeline = IrisTerrainCompat.terrain(RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
            .withLocation(Identifier.of("director", "pipeline/" + name))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(depthWrite)
            .withShaderDefine("ALPHA_CUTOUT", 0.01f)
            .build());
        return RenderLayer.of("director_" + name, 786432,
            false, true, pipeline, RenderLayer.MultiPhaseParameters.builder()
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .texture(RenderPhase.MIPMAP_BLOCK_ATLAS_TEXTURE)
                    .target(RenderPhase.ITEM_ENTITY_TARGET).build(false));
    }
}
