package com.director.core.animation;

import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OcclusionSurfaceRenderLayersTest {
    @Test void transparentShellDoesNotWriteDepthOrLoseTileMetadata() {
        var tiled = OcclusionSurfaceRenderLayers.tiled(false);
        assertFalse(tiled.getRenderPipeline().isWriteDepth());
        assertTrue(tiled.isTranslucent());
        assertSame(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, tiled.getVertexFormat());
        assertEquals(Identifier.of("director", "core/occlusion_surface"), tiled.getRenderPipeline().getVertexShader());
        assertEquals(Identifier.of("director", "core/occlusion_surface"), tiled.getRenderPipeline().getFragmentShader());
        var nativeLayer = OcclusionSurfaceRenderLayers.nativeLayer(false);
        assertFalse(nativeLayer.getRenderPipeline().isWriteDepth());
        assertTrue(nativeLayer.isTranslucent());
        assertSame(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, nativeLayer.getVertexFormat());
        assertEquals(Identifier.ofVanilla("core/terrain"), nativeLayer.getRenderPipeline().getVertexShader());
    }
}
