package com.chronoblocks.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Interaction hint renderer for hover highlights (block outlines, breathing effects).
 */
public final class HoverHighlightRenderer {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private HoverHighlightRenderer() {}

    public static void renderBlockOutline(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockHitResult hit) {
        if (MC.world == null || hit == null || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        try {
            var selectionManager = com.chronoblocks.core.selection.SelectionManager.get();
            if (selectionManager != null
                && selectionManager.getCurrentSelection() != null
                && selectionManager.getCurrentSelection().getSelectedBlocks().contains(pos)) {
                return;
            }
        } catch (Throwable ignored) {
        }

        Vec3d camPos = MC.gameRenderer.getCamera().getPos();

        Box box = MC.world.getBlockState(pos)
                .getOutlineShape(MC.world, pos)
                .getBoundingBox()
                .offset(pos)
                .offset(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        long time = System.currentTimeMillis();
        float hue = (time % 5000L) / 5000.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float alpha = 0.5f + 0.5f * (float) Math.sin(time / 400.0);
        alpha = 0.35f + alpha * 0.45f;

        RenderSystem.lineWidth(1.5f + 0.5f * (float) Math.sin(time / 600.0));

        drawBoxEdges(consumer, matrix, box, r, g, b, alpha);

        RenderSystem.lineWidth(1.0f);
    }

    private static void drawBoxEdges(VertexConsumer consumer, Matrix4f matrix, Box box,
                                     float r, float g, float b, float a) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;

        drawLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        drawLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        drawLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a)
                .normal(0, 1, 0);
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2)
                .color(r, g, b, a)
                .normal(0, 1, 0);
    }
}

