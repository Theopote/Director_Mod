package com.director.core.animation;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IrisTerrainCompatTest {
    // Separate tables model Iris 1.9.7's main and shadow mappings. Exercise the real
    // reflection bridge without making Iris mandatory for Director or its unit tests.
    public static final class Api {
        static final Api INSTANCE = new Api();
        boolean enabled;
        public static Api getInstance() { return INSTANCE; }
        public boolean isShaderPackInUse() { return enabled; }
    }
    public static final class Pipelines {
        static final Map<RenderPipeline, Object> MAIN = new IdentityHashMap<>();
        static final Map<RenderPipeline, Object> SHADOW = new IdentityHashMap<>();
        public static void copyPipeline(RenderPipeline source, RenderPipeline target) {
            MAIN.put(target, MAIN.get(source));
            SHADOW.put(target, SHADOW.get(source));
        }
    }
    public static final class BrokenPipelines {
        public static void copyPipeline(RenderPipeline source, RenderPipeline target) {
            throw new IllegalStateException("unsupported Iris version");
        }
    }
    @BeforeEach void reset() {
        Pipelines.MAIN.clear();
        Pipelines.SHADOW.clear();
        Pipelines.MAIN.put(RenderPipelines.TRANSLUCENT, new Object());
        Pipelines.SHADOW.put(RenderPipelines.TRANSLUCENT, new Object());
        Api.INSTANCE.enabled = false;
    }
    @Test void mapsMainAndShadowWithoutChangingBlendDepthOrVertexFormat() throws Exception {
        var bridge = new IrisTerrainCompat.Bridge(Api.class, Pipelines.class);
        for (boolean depth : new boolean[] {true, false}) {
            var pipeline = pipeline(depth);
            assertSame(pipeline, IrisTerrainCompat.terrain(pipeline, bridge));
            assertSame(Pipelines.MAIN.get(RenderPipelines.TRANSLUCENT), Pipelines.MAIN.get(pipeline));
            assertSame(Pipelines.SHADOW.get(RenderPipelines.TRANSLUCENT), Pipelines.SHADOW.get(pipeline));
            assertNotSame(Pipelines.MAIN.get(pipeline), Pipelines.SHADOW.get(pipeline));
            assertEquals(depth, pipeline.isWriteDepth());
            assertEquals(BlendFunction.TRANSLUCENT, pipeline.getBlendFunction().orElseThrow());
            assertSame(RenderPipelines.TRANSLUCENT.getVertexFormat(), pipeline.getVertexFormat());
        }
    }
    @Test void queriesCurrentPackStateAcrossToggleAndReload() throws Exception {
        var bridge = new IrisTerrainCompat.Bridge(Api.class, Pipelines.class);
        assertFalse(bridge.shaderPackInUse());
        Api.INSTANCE.enabled = true;
        assertTrue(bridge.shaderPackInUse());
        Api.INSTANCE.enabled = false;
        assertFalse(bridge.shaderPackInUse());
        Api.INSTANCE.enabled = true;
        assertTrue(bridge.shaderPackInUse());
    }
    @Test void unsupportedMappingUsesAKnownPipeline() throws Exception {
        var bridge = new IrisTerrainCompat.Bridge(Api.class, BrokenPipelines.class);
        assertSame(RenderPipelines.TRANSLUCENT, IrisTerrainCompat.terrain(pipeline(true), bridge));
    }
    @Test void shadersNeverEmitTheCustomTiledSurfaceShader() {
        var shell = OcclusionSurfaceRenderer.prepare(null, List.of(), true);
        assertTrue(shell.handled().isEmpty());
        shell.draw(new MatrixStack(), layer -> fail("custom tiled shader used with Iris"));
    }
    private static RenderPipeline pipeline(boolean depth) {
        return RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
                .withLocation(Identifier.of("director", "test_iris_terrain_" + depth))
                .withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(depth)
                .withShaderDefine("ALPHA_CUTOUT", 0.01f).build();
    }
}
