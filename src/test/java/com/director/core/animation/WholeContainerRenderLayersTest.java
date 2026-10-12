package com.director.core.animation;

import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerRenderLayersTest {
    @Test void opaqueAndCutoutModelsUseTheirNativeTerrainShaders() {
        assertSame(RenderLayer.getSolid(), BlockAnimationRenderer.finalWorldLayer(BlockRenderLayer.SOLID, 1));
        assertSame(RenderLayer.getCutout(), BlockAnimationRenderer.finalWorldLayer(BlockRenderLayer.CUTOUT, 1));
        assertSame(RenderLayer.getCutoutMipped(), BlockAnimationRenderer.finalWorldLayer(BlockRenderLayer.CUTOUT_MIPPED, 1));
    }
    @Test void fadesUseBlendedTerrainInsteadOfEntityLighting() {
        for (var type : BlockRenderLayer.values()) {
            var layer = BlockAnimationRenderer.finalWorldLayer(type, 0.5f);
            assertSame(WholeContainerRenderLayers.FADED, layer);
            assertTrue(layer.isTranslucent());
            assertEquals(RenderLayer.getSolid().getVertexFormat(), layer.getVertexFormat());
        }
        assertSame(WholeContainerRenderLayers.FADED,
                BlockAnimationRenderer.finalWorldLayer(BlockRenderLayer.TRANSLUCENT, 1));
    }
}
