package com.director.client.renderer;

import com.director.client.settings.RenderSettings;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ToolPreviewBufferLifetimeTest {
    private final RenderSettings settings = RenderSettings.get();
    private final boolean oldFill = settings.tool_showFill;
    private final boolean oldOutline = settings.tool_showOutline;

    @AfterEach void restoreSettings() {
        settings.tool_showFill = oldFill;
        settings.tool_showOutline = oldOutline;
    }

    @Test void faceSelectionFinishesFillBeforeAcquiringOutlineForEveryDisplayMode() throws Exception {
        for (boolean fill : new boolean[] {false, true}) {
            for (boolean outline : new boolean[] {false, true}) {
                var manager = manager();
                manager.updateFacePreview(Set.of(BlockPos.ORIGIN, new BlockPos(1, 0, 0)),
                        ToolPreviewManager.FacePreviewTint.BASE);
                settings.tool_showFill = fill;
                settings.tool_showOutline = outline;
                var provider = new SwitchingProvider();
                var render = ToolPreviewManager.class.getDeclaredMethod("renderFacePreview",
                        Matrix4f.class, VertexConsumerProvider.class);
                render.setAccessible(true);
                assertDoesNotThrow(() -> render.invoke(manager, new Matrix4f(), provider));
                assertEquals(fill ? 48 : 0, provider.fillVertices);
                assertEquals(outline, provider.lineVertices > 0);
                assertEquals((fill ? 1 : 0) + (outline ? 1 : 0), provider.layers.size());
            }
        }
    }

    @Test void faceHoverKeepsGradientAndNormalArrowWithoutUsingAnEndedFillBuffer() throws Exception {
        for (boolean fill : new boolean[] {false, true}) {
            for (boolean outline : new boolean[] {false, true}) {
                var manager = manager();
                var a = BlockPos.ORIGIN;
                var b = new BlockPos(1, 0, 0);
                manager.updateFaceHoverPreview(Set.of(a, b), Map.of(a, 0, b, 1), a, Direction.UP);
                settings.tool_showFill = fill;
                settings.tool_showOutline = outline;
                var provider = new SwitchingProvider();
                var render = ToolPreviewManager.class.getDeclaredMethod("renderFaceHoverPreview",
                        Matrix4f.class, VertexConsumerProvider.class, float.class);
                render.setAccessible(true);
                assertDoesNotThrow(() -> render.invoke(manager, new Matrix4f(), provider, 0.5f));
                assertEquals(fill ? 48 : 0, provider.fillVertices);
                assertTrue(provider.lineVertices > 0, "normal arrow remains visible without outlines");
                assertEquals(fill ? 2 : 1, provider.layers.size());
            }
        }
    }

    private static ToolPreviewManager manager() throws Exception {
        var constructor = ToolPreviewManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /** Like Immediate's shared fallback buffer: a layer switch ends the old builder. */
    private static final class SwitchingProvider implements VertexConsumerProvider {
        final List<RenderLayer> layers = new ArrayList<>();
        int generation, fillVertices, lineVertices;

        @Override public VertexConsumer getBuffer(RenderLayer layer) {
            layers.add(layer);
            int acquired = ++generation;
            return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[] {VertexConsumer.class}, (proxy, method, args) -> {
                        if (acquired != generation) throw new IllegalStateException("Not building!");
                        if (method.getName().equals("vertex")) {
                            if (layer == RenderLayers.getPreviewLayer()) fillVertices++;
                            else lineVertices++;
                        }
                        return method.getReturnType() == VertexConsumer.class ? proxy : null;
                    });
        }
    }
}
