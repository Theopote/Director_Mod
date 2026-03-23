package com.director.core.animation;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * AlphaVertexConsumer
 * -------------------
 * 包装 VertexConsumer，注入 alpha 值到所有 vertex 的 color 属性。
 * 
 * <p>这是实现方块动画透明度的快速方案（方案 B）。
 * 通过拦截 color() 调用，将自定义 alpha 乘入 vertex color。
 * 
 * <p>使用方法：
 * <pre>
 * VertexConsumer原始Buffer = provider.getBuffer(layer);
 * VertexConsumer alphaBuffer = new AlphaVertexConsumer(原始Buffer, snapshot.getAlpha());
 * blockRenderManager.renderBlock(..., alphaBuffer, ...);
 * </pre>
 * 
 * @author Director Team
 * @version 1.0
 */
public class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alpha;

    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // 将自定义 alpha 乘入 vertex alpha
        int finalAlpha = (int) (alpha * this.alpha);
        delegate.color(red, green, blue, finalAlpha);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        // 将自定义 alpha 乘入 vertex alpha
        float finalAlpha = alpha * this.alpha;
        delegate.color(red, green, blue, finalAlpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int uv) {
        delegate.light(uv);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
        delegate.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer normal(MatrixStack.Entry matrix, float x, float y, float z) {
        delegate.normal(matrix, x, y, z);
        return this;
    }
}

