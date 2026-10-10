package com.director.core.animation;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AlphaVertexConsumerTest {
    private static class Recording implements VertexConsumer {
        int red, green, blue, alpha, vertices;
        public VertexConsumer vertex(float x, float y, float z) { vertices++; return this; }
        public VertexConsumer color(int r, int g, int b, int a) { red=r; green=g; blue=b; alpha=a; return this; }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
    }
    @Test void vertexAlphaMultipliesSourceTransparencyAndPreservesColor() {
        var recording = new Recording(); var consumer = new AlphaVertexConsumer(recording, 0.25f);
        consumer.color(0x80112233);
        assertEquals(32, recording.alpha); assertEquals(17, recording.red); assertEquals(34, recording.green); assertEquals(51, recording.blue);
        consumer.vertex(1, 2, 3, 0xFF112233, 0, 0, 0, 0, 0, 1, 0);
        assertEquals(1, recording.vertices); assertEquals(63, recording.alpha);
    }
    @Test void fadedBlockProviderUsesBlendingLayerAndActuallyWritesAlpha() {
        var recording = new Recording(); var requested = new AtomicReference<RenderLayer>();
        VertexConsumerProvider original = layer -> { requested.set(layer); return recording; };
        var faded = BlockAnimationRenderer.withAlpha(original, 0.5f);
        faded.getBuffer(null).color(255, 255, 255, 255);
        assertSame(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE), requested.get());
        assertEquals(127, recording.alpha);
        assertSame(original, BlockAnimationRenderer.withAlpha(original, 1));
    }
}
