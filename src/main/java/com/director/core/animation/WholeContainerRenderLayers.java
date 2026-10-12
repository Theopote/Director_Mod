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
    private static final RenderPipeline FADED_PIPELINE = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
            .withLocation(Identifier.of("director", "pipeline/whole_container_fade"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withShaderDefine("ALPHA_CUTOUT", 0.01f)
            .build();
    static final RenderLayer FADED = RenderLayer.of("director_whole_container_fade", 786432,
            false, true, FADED_PIPELINE, RenderLayer.MultiPhaseParameters.builder()
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .texture(RenderPhase.MIPMAP_BLOCK_ATLAS_TEXTURE)
                    .target(RenderPhase.ITEM_ENTITY_TARGET).build(false));
}
