package com.director.client.renderer.mesh;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalDouble;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Render-thread-owned persistent vertex buffers. Call clear on world/resource changes. */
public final class SectionGpuBufferCache implements AutoCloseable {
    public record Key(long section, Object material, long geometryVersion, long lightingVersion) {}
    private final long budget;
    private final Map<Key, GpuBuffer> buffers = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    public SectionGpuBufferCache(long budget) {
        if (budget <= 0) throw new IllegalArgumentException("GPU budget must be positive");
        this.budget = budget;
    }

    public GpuBuffer get(Key key) {
        RenderSystem.assertOnRenderThread();
        return buffers.get(key);
    }

    /** Oversized geometry stays on the caller's existing fallback path. */
    public GpuBuffer upload(Key key, ByteBuffer vertices) {
        RenderSystem.assertOnRenderThread();
        var cached = buffers.get(key);
        if (cached != null) return cached;
        int size = vertices.remaining();
        if (size == 0 || size > budget) return null;
        while (bytes + size > budget && !buffers.isEmpty()) {
            var iterator = buffers.entrySet().iterator();
            var entry = iterator.next();
            var removed = entry.getValue();
            iterator.remove(); bytes -= removed.size(); removed.close();
        }
        // Unlike RenderLayer.draw(BuiltBuffer), this buffer survives the current draw call.
        var uploaded = RenderSystem.getDevice().createBuffer(() -> "Director section " + key.section(),
                GpuBuffer.USAGE_VERTEX, vertices.duplicate());
        buffers.put(key, uploaded);
        bytes += uploaded.size();
        return uploaded;
    }

    public void invalidate(long section) {
        RenderSystem.assertOnRenderThread();
        var iterator = buffers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().section() != section) continue;
            var buffer = entry.getValue();
            iterator.remove(); bytes -= buffer.size(); buffer.close();
        }
    }

    public long residentBytes() { return bytes; }

    /** Draws an opaque cached QUADS mesh; the caller explicitly selects its render target. */
    public boolean draw(Key key, RenderLayer layer, Framebuffer target, Matrix4f modelView, int indexCount) {
        RenderSystem.assertOnRenderThread();
        if (indexCount <= 0) return false;
        var vertices = buffers.get(key);
        if (vertices == null) return false;
        var indices = RenderSystem.getSequentialBuffer(layer.getDrawMode());
        var indexBuffer = indices.getIndexBuffer(indexCount);
        layer.startDrawing();
        try {
            var transforms = RenderSystem.getDynamicUniforms().write(modelView, new Vector4f(1, 1, 1, 1),
                    new Vector3f(), RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());
            var color = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride : target.getColorAttachmentView();
            var depth = target.useDepthAttachment
                    ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride : target.getDepthAttachmentView()) : null;
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Director cached section", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(layer.getRenderPipeline());
                var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.isEnabled()) pass.enableScissor(scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                for (int i = 0; i < 12; i++) {
                    var texture = RenderSystem.getShaderTexture(i);
                    if (texture != null) pass.bindSampler("Sampler" + i, texture);
                }
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                pass.drawIndexed(0, 0, indexCount, 1);
            }
            return true;
        } finally {
            layer.endDrawing();
        }
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        try {
            for (var buffer : buffers.values()) buffer.close();
        } finally {
            buffers.clear(); bytes = 0;
        }
    }
}
