package com.director.core.animation;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.RenderPipelines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Optional Iris 1.9.x integration; no Iris classes are required to load Director. */
final class IrisTerrainCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisTerrainCompat.class);
    private static final boolean IRIS_PRESENT = FabricLoader.getInstance().isModLoaded("iris");
    private static final Bridge BRIDGE = discover();
    private static boolean queryFailed;

    private IrisTerrainCompat() {}

    private static Bridge discover() {
        if (!IRIS_PRESENT) return null;
        try {
            return new Bridge(Class.forName("net.irisshaders.iris.api.v0.IrisApi"),
                    Class.forName("net.irisshaders.iris.pipeline.IrisPipelines"));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("[Director] Iris terrain integration unavailable; using native translucent pipelines", e);
            return null;
        }
    }

    static RenderPipeline terrain(RenderPipeline pipeline) {
        if (!IRIS_PRESENT) return pipeline;
        if (BRIDGE == null) return RenderPipelines.TRANSLUCENT;
        return terrain(pipeline, BRIDGE);
    }

    static RenderPipeline terrain(RenderPipeline pipeline, Bridge bridge) {
        try {
            // Iris 1.9.7's public assignPipeline only assigns the main pass. copyPipeline
            // inherits both main and shadow mappings while preserving our blend/depth state.
            bridge.copy(RenderPipelines.TRANSLUCENT, pipeline);
            return pipeline;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("[Director] Could not map {} into Iris; using native translucent pipeline",
                    pipeline.getLocation(), e);
            return RenderPipelines.TRANSLUCENT;
        }
    }

    static boolean shaderPackInUse() {
        if (!IRIS_PRESENT) return false;
        if (BRIDGE == null || queryFailed) return true;
        try {
            return BRIDGE.shaderPackInUse();
        } catch (ReflectiveOperationException | LinkageError e) {
            queryFailed = true;
            LOGGER.warn("[Director] Could not query Iris shader state; disabling custom tiled terrain shaders", e);
            return true;
        }
    }

    /** Resolve once; pipeline creation and one query per overlay pass use cached methods. */
    static final class Bridge {
        private final Object api;
        private final Method shaderPackInUse;
        private final Method copyPipeline;

        Bridge(Class<?> apiClass, Class<?> pipelinesClass) throws ReflectiveOperationException {
            api = apiClass.getMethod("getInstance").invoke(null);
            shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            copyPipeline = pipelinesClass.getMethod("copyPipeline", RenderPipeline.class, RenderPipeline.class);
        }

        boolean shaderPackInUse() throws ReflectiveOperationException {
            return (boolean) shaderPackInUse.invoke(api);
        }

        void copy(RenderPipeline source, RenderPipeline target) throws ReflectiveOperationException {
            copyPipeline.invoke(null, source, target);
        }
    }
}
